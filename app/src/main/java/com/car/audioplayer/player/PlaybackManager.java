package com.car.audioplayer.player;

import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.car.audioplayer.App;
import com.car.audioplayer.data.model.OnlineSong;
import com.car.audioplayer.data.remote.QQMusicApi;
import com.car.audioplayer.service.PlaybackService;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;

/**
 * 全局播放管理单例：所有页面的播放入口。
 * 职责：维护内存播放队列、按需拉取真实播放地址（getMusicPlay）、
 * 通过 MediaController 驱动 PlaybackService 的 ExoPlayer。
 * 对外状态拆三条独立流，按需订阅避免 500ms 轮询全量刷新所有观察者：
 * - song()     换歌才发（列表高亮/迷你条/播放页标题封面）
 * - playing()  播放/暂停变化才发（播放键图标）
 * - progress() 轮询推进（仅 position/duration，播放页进度条与歌词消费）
 * state() 保留为同步快照（getValue 读），不再随轮询广播。
 */
public class PlaybackManager {

    private static final String TAG = "PlaybackManager";

    /** 进度轮询间隔（毫秒） */
    private static final long POLL_INTERVAL_MS = 500;

    /** 全局播放状态快照（仅同步 getValue 读；轮询不再广播此对象） */
    public static class PlayingState {
        public OnlineSong song;
        public boolean playing;
        public long position;
        public long duration;
        public int queueIndex = -1;
        public int queueSize;
    }

    /** 进度快照：仅进度与时长（progress 流的载荷） */
    public static class Progress {
        public final long position;
        public final long duration;

        public Progress(long position, long duration) {
            this.position = Math.max(0, position);
            this.duration = Math.max(0, duration);
        }
    }

    private static volatile PlaybackManager instance;

    private MediaController controller;
    private final MutableLiveData<PlayingState> state = new MutableLiveData<>(new PlayingState());
    private final MutableLiveData<OnlineSong> songLive = new MutableLiveData<>();
    private final MutableLiveData<Boolean> playingLive = new MutableLiveData<>(false);
    private final MutableLiveData<Progress> progressLive = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();

    private final List<OnlineSong> queue = new ArrayList<>();
    private int currentIndex = -1;

    /** 切歌代数：快速切歌时丢弃过期的播放地址回调 */
    private int generation = 0;

    /** 连续取地址失败计数：成功播放后清零，防止整队列失败时无限跳歌 */
    private int consecutiveFailures = 0;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final Runnable poller = new Runnable() {
        @Override
        public void run() {
            if (controller != null) {
                // 只推进度流：换歌/播放态各自独立广播，不再全量刷新所有观察者
                dispatchProgress(controller.getCurrentPosition(), controller.getDuration());
            }
            mainHandler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    /** 事件驱动轮询：仅播放中推进进度，暂停即停（原来 500ms 无条件轮询常驻耗电） */
    private void startPolling() {
        mainHandler.removeCallbacks(poller);
        mainHandler.post(poller);
    }

    private void stopPolling() {
        mainHandler.removeCallbacks(poller);
    }

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onIsPlayingChanged(boolean playing) {
            dispatchPlayingChanged(playing);
            if (playing) {
                startPolling();
            } else {
                stopPolling();
            }
        }

        @Override
        public void onPlaybackStateChanged(int playbackState) {
            // 单曲自然播完：自动切下一首（列表循环）
            if (playbackState == Player.STATE_ENDED) {
                autoNext();
            }
        }
    };

    private PlaybackManager() {
    }

    public static PlaybackManager get(Context context) {
        if (instance == null) {
            synchronized (PlaybackManager.class) {
                if (instance == null) {
                    instance = new PlaybackManager();
                    instance.init(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private void init(Context appContext) {
        SessionToken token = new SessionToken(appContext,
                new ComponentName(appContext, PlaybackService.class));
        ListenableFuture<MediaController> future =
                new MediaController.Builder(appContext, token).buildAsync();
        future.addListener(() -> {
            try {
                controller = future.get();
                controller.addListener(playerListener);
                // 恢复进程被杀前的播放现场（ExoPlayer 侧仍在）
                boolean isPlaying = controller.isPlaying();
                MediaItem item = controller.getCurrentMediaItem();
                OnlineSong restored = findSongByMediaId(item == null ? null : item.mediaId);
                dispatchPlayingChanged(isPlaying);
                if (restored != null) {
                    dispatchSongChanged(restored);
                }
            } catch (Exception e) {
                Log.e(TAG, "init controller failed", e);
            }
        }, ContextCompat.getMainExecutor(appContext));
        // 轮询由播放状态事件驱动（onIsPlayingChanged），不再无条件常驻
    }

    // ================= 对外状态 =================

    /** 同步快照（ getValue 读）；UI 订阅请用 song()/playing()/progress() */
    public LiveData<PlayingState> state() {
        return state;
    }

    /** 当前歌曲：换歌才发（null = 队列已清空） */
    public LiveData<OnlineSong> song() {
        return songLive;
    }

    /** 播放/暂停：变化才发 */
    public LiveData<Boolean> playing() {
        return playingLive;
    }

    /** 播放进度：轮询期间每 500ms 推进，仅进度/时长 */
    public LiveData<Progress> progress() {
        return progressLive;
    }

    public LiveData<String> error() {
        return error;
    }

    public MediaController controller() {
        return controller;
    }

    public List<OnlineSong> queue() {
        return new ArrayList<>(queue);
    }

    public int currentIndex() {
        return currentIndex;
    }

    // ================= 状态分发 =================

    /** 换歌：同步快照 + 推 song 流（同对象去重，playAt→playPrepared 同歌不重复触发 UI 换歌） */
    private void dispatchSongChanged(OnlineSong song) {
        PlayingState s = state.getValue();
        if (s != null) {
            s.song = song;
            s.queueIndex = currentIndex;
            s.queueSize = queue.size();
            state.setValue(s);
        }
        if (songLive.getValue() != song) {
            songLive.setValue(song);
        }
    }

    /** 播放态：同步快照 + 推 playing 流（同值去重） */
    private void dispatchPlayingChanged(boolean playing) {
        PlayingState s = state.getValue();
        if (s != null) {
            s.playing = playing;
            state.setValue(s);
        }
        Boolean old = playingLive.getValue();
        if (old == null || old != playing) {
            playingLive.setValue(playing);
        }
    }

    /** 进度：只改快照字段 + 推 progress 流，不广播全量 state（避免全观察者刷新链） */
    private void dispatchProgress(long position, long duration) {
        PlayingState s = state.getValue();
        if (s != null) {
            s.position = Math.max(0, position);
            s.duration = Math.max(0, duration);
        }
        progressLive.setValue(new Progress(position, duration));
    }

    // ================= 播放入口 =================

    /** 整单播放：替换队列并从 index 开始 */
    public void playQueue(List<OnlineSong> songs, int index) {
        if (songs == null || songs.isEmpty()) {
            return;
        }
        Log.d(TAG, "playQueue: size=" + songs.size() + ", index=" + index);
        queue.clear();
        queue.addAll(songs);
        playAt(index);
    }

    /** 播放队列中指定位置的歌曲：先取真实地址再交给播放器 */
    public void playAt(int index) {
        if (controller == null) {
            error.setValue("播放器尚未就绪，请稍后重试");
            return;
        }
        if (index < 0 || index >= queue.size()) {
            return;
        }
        currentIndex = index;
        OnlineSong song = queue.get(index);
        int gen = ++generation;
        Log.d(TAG, "playAt: index=" + index + ", song=" + song.name
                + ", hasUrl=" + song.hasPlayUrl());

        // 乐观切换：歌名/封面等展示数据本地已有，点歌瞬间立即推给 UI，
        // 避免取播放地址期间播放页/迷你条残留上一首；旧歌立即停播，
        // 防止进度轮询用旧音频进度覆盖归零后的进度。地址就绪后 playPrepared 覆盖为真实播放态。
        controller.pause();
        dispatchSongChanged(song);
        dispatchPlayingChanged(false);
        dispatchProgress(0, 0);

        // 可选快速路径：下次切回同一首歌且已有地址，直接播（尽量避免重复等待）
        if (song.hasPlayUrl()) {
            playPrepared(gen, song);
            return;
        }

        // 榜单快路径：列表秒开时只有 songId 没有 mid，播放前按需解析一次
        if (song.mid == null || song.mid.isEmpty()) {
            if (song.songId <= 0) {
                error.setValue("该歌曲暂无播放信息");
                return;
            }
            QQMusicApi.resolveMid(song.songId, new QQMusicApi.Callback<String>() {
                @Override
                public void onSuccess(String mid) {
                    if (gen != generation) {
                        return;
                    }
                    song.mid = mid;
                    fetchPlayUrl(gen, song);
                }

                @Override
                public void onError(String message) {
                    if (gen != generation) {
                        return;
                    }
                    error.setValue(message + "（自动跳下一首）");
                    if (queue.size() > 1 && ++consecutiveFailures < queue.size()) {
                        next();
                    }
                }
            });
            return;
        }

        fetchPlayUrl(gen, song);
    }

    /** 取真实播放地址并播放（mid 已就绪） */
    private void fetchPlayUrl(int gen, OnlineSong song) {
        QQMusicApi.getPlayUrl(song.mid, new QQMusicApi.Callback<String>() {
            @Override
            public void onSuccess(String url) {
                if (gen != generation) {
                    return; // 已被更新的切歌取代
                }
                song.playUrl = url;
                playPrepared(gen, song);
            }

            @Override
            public void onError(String message) {
                if (gen != generation) {
                    return;
                }
                error.setValue(message + "（自动跳下一首）");
                // 播放地址获取失败（如 Cookie 失效被风控）：自动跳下一首，避免卡死
                if (queue.size() > 1 && ++consecutiveFailures < queue.size()) {
                    next();
                }
            }
        });
    }

    private void playPrepared(int gen, OnlineSong song) {
        if (controller == null || gen != generation) {
            return;
        }
        Log.d(TAG, "playPrepared: song=" + song.name + ", gen=" + gen);
        MediaItem item = new MediaItem.Builder()
                .setUri(song.playUrl)
                .setMediaId(song.mid)
                .setMediaMetadata(new MediaMetadata.Builder()
                        .setTitle(song.name)
                        .setArtist(song.singer)
                        .build())
                .build();
        controller.setMediaItem(item);
        controller.prepare();
        controller.play();
        consecutiveFailures = 0;

        dispatchSongChanged(song);
        dispatchPlayingChanged(true);
        dispatchProgress(0, 0);
    }

    /** 自动切下一首（单曲结束触发，列表循环） */
    private void autoNext() {
        if (queue.isEmpty()) {
            return;
        }
        int next = (currentIndex + 1) % queue.size();
        playAt(next);
    }

    public void next() {
        if (queue.isEmpty()) {
            return;
        }
        int next = (currentIndex + 1) % queue.size();
        playAt(next);
    }

    public void prev() {
        if (queue.isEmpty()) {
            return;
        }
        int prevIndex = (currentIndex - 1 + queue.size()) % queue.size();
        playAt(prevIndex);
    }

    public void toggle() {
        if (controller == null) {
            return;
        }
        Log.d(TAG, "toggle: playing=" + controller.isPlaying());
        if (controller.isPlaying()) {
            controller.pause();
        } else {
            controller.play();
        }
    }

    public void seekTo(long positionMs) {
        if (controller != null) {
            Log.d(TAG, "seekTo: " + positionMs + "ms");
            long target = Math.max(0, positionMs);
            controller.seekTo(target);
            // 暂停态轮询已停，手动推一次进度保证进度条即时更新
            PlayingState s = state.getValue();
            dispatchProgress(target, s == null ? 0 : s.duration);
        }
    }

    private OnlineSong findSongByMediaId(String mediaId) {
        if (mediaId == null) {
            return null;
        }
        for (OnlineSong s : queue) {
            if (mediaId.equals(s.mid)) {
                return s;
            }
        }
        return null;
    }

    /** 清空队列并停止播放（队列面板"清空"） */
    public void clearQueue() {
        Log.d(TAG, "clearQueue");
        if (controller != null) {
            controller.pause();
        }
        queue.clear();
        currentIndex = -1;
        generation++;
        dispatchSongChanged(null);
        dispatchPlayingChanged(false);
        dispatchProgress(0, 0);
    }
}