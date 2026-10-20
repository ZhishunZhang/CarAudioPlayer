package com.car.audioplayer.ui.player;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import com.car.audioplayer.R;
import com.car.audioplayer.data.model.LyricLine;
import com.car.audioplayer.data.model.OnlineSong;
import com.car.audioplayer.data.repository.FavoritesRepository;
import com.car.audioplayer.databinding.FragmentPlayerBinding;
import com.car.audioplayer.player.ImageLoader;
import com.car.audioplayer.player.LyricParser;
import com.car.audioplayer.player.PlaybackManager;
import com.car.audioplayer.ui.common.SongAdapter;
import com.car.audioplayer.ui.main.MainActivity;

import java.util.Collections;
import java.util.List;

import static com.car.audioplayer.data.remote.QQMusicApi.coverUrl;

/**
 * 全屏播放页：封面做背景，歌词多行平滑滚动（当前行居中高亮）+ 大触控播控。
 * 点任意歌词行直接 seek 播放该句；拖动暂离自动跟随，停手 6 秒后
 * （且正在播放）自动滚回播放行。返回/下滑关闭时播放不中断，缩回全局迷你条。
 */
public class PlayerFragment extends Fragment {

    /** 拖动歌词后自动滚回播放行的延时（毫秒） */
    private static final long LYRIC_ROLLBACK_MS = 6000L;

    private FragmentPlayerBinding binding;
    private PlaybackManager playback;
    private FavoritesRepository favorites;
    private PlayerViewModel viewModel;

    private List<LyricLine> lyricLines = Collections.emptyList();
    private boolean lyricLoaded = false;
    private String lyricSongMid = "";
    private String lyricSongName = "";
    private boolean seeking = false;
    private String currentMid = "";
    private boolean currentFav = false;

    // ===== 歌词列表状态 =====
    private LyricAdapter lyricAdapter;
    /** 当前高亮行下标 */
    private int currentLyricLine = -1;
    /** 用户拖动歌词 → 暂离自动跟随（浏览态） */
    private boolean lyricBrowsing = false;
    /** 首尾行居中所需上下留白是否已设置 */
    private boolean lyricPaddingApplied = false;
    private final Handler lyricHandler = new Handler(Looper.getMainLooper());

    /** 6 秒滚回：到期且正在播放才滚回播放行；不在播放则保持浏览态不滚回 */
    private final Runnable lyricRollback = new Runnable() {
        @Override
        public void run() {
            if (binding == null) {
                return;
            }
            binding.tvLyricHint.setVisibility(View.GONE);
            Boolean playing = playback.playing().getValue();
            if (playing == null || !playing) {
                return;
            }
            lyricBrowsing = false;
            if (currentLyricLine >= 0) {
                scrollToLyricLine(currentLyricLine, true);
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentPlayerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    /** 视图装配：歌词列表/进度条/手势（下滑关闭+歌词拖动）/三条状态流订阅 */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        MainActivity main = (MainActivity) requireActivity();
        playback = PlaybackManager.get(requireContext());
        favorites = FavoritesRepository.get(requireContext());
        viewModel = new ViewModelProvider(this).get(PlayerViewModel.class);

        binding.btnBack.setOnClickListener(v -> main.goBack());
        binding.btnPrev.setOnClickListener(v -> playback.prev());
        binding.btnNext.setOnClickListener(v -> playback.next());
        binding.btnPlay.setOnClickListener(v -> playback.toggle());
        binding.btnQueue.setOnClickListener(v -> new QueuePanelFragment().show(
                getParentFragmentManager(), "queue_panel"));
        binding.btnPlayMode.setOnClickListener(v ->
                main.showToast("播放模式：列表循环（更多模式待扩展）"));

        // 下滑关闭：在未被子控件消费的空白区域下滑，收起播放页（播放不中断）
        float swipeMin = 60f * getResources().getDisplayMetrics().density;
        GestureDetector closeGesture = new GestureDetector(requireContext(),
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                        if (e1 == null || e2 == null) {
                            return false;
                        }
                        float downY = e2.getY() - e1.getY();
                        if (downY > swipeMin && Math.abs(downY) > Math.abs(e2.getX() - e1.getX()) * 1.2f) {
                            main.goBack();
                            return true;
                        }
                        return false;
                    }
                });
        binding.getRoot().setOnTouchListener((v, event) -> {
            closeGesture.onTouchEvent(event);
            return true;
        });

        // 收藏（本地，异步写库）
        binding.btnFav.setOnClickListener(v -> {
            OnlineSong song = currentSong();
            if (song == null) {
                return;
            }
            String mid = song.mid;
            favorites.toggleSongFavorite(song, fav -> {
                if (binding == null || !mid.equals(currentMid)) {
                    return;
                }
                currentFav = fav;
                updateFavIcon(fav);
                main.showToast(fav ? "已加入我喜欢的歌曲" : "已取消收藏");
            });
        });

        // 进度条
        binding.seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    binding.tvPosition.setText(SongAdapter.formatTime(progress / 1000));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                seeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                playback.seekTo(seekBar.getProgress());
                seeking = false;
            }
        });

        // 歌词列表：多行平滑滚动，当前行居中高亮，点行直接跳播该句
        binding.rvLyric.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvLyric.setItemAnimator(null);
        lyricAdapter = new LyricAdapter(this::onLyricLineClick);
        binding.rvLyric.setAdapter(lyricAdapter);

        // 首尾行也能滚到居中位置：上下留白（clipToPadding=false 已在 XML 声明）
        binding.rvLyric.addOnLayoutChangeListener(
                (v, l, t, r, b, ol, ot, orr, ob) -> {
                    int height = b - t;
                    if (height > 0 && !lyricPaddingApplied) {
                        lyricPaddingApplied = true;
                        float density = getResources().getDisplayMetrics().density;
                        int padHorizontal = (int) (24f * density);
                        int padVertical = Math.max(0, height / 2 - (int) (60f * density));
                        v.setPadding(padHorizontal, padVertical, padHorizontal, padVertical);
                    }
                });

        // 拖动歌词：进入浏览态（暂离自动跟随）+ 显示滚回提示；停手起 6 秒滚回计时
        binding.rvLyric.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    lyricBrowsing = true;
                    lyricHandler.removeCallbacks(lyricRollback);
                    binding.tvLyricHint.setVisibility(View.VISIBLE);
                } else if (newState == RecyclerView.SCROLL_STATE_IDLE && lyricBrowsing) {
                    lyricHandler.removeCallbacks(lyricRollback);
                    lyricHandler.postDelayed(lyricRollback, LYRIC_ROLLBACK_MS);
                }
            }
        });

        // 歌词结果：只处理当前歌曲的结果，避免切歌后旧请求覆盖
        viewModel.getLyricResult().observe(getViewLifecycleOwner(), result -> {
            if (result == null || !result.songMid.equals(lyricSongMid)) {
                return;
            }
            lyricLoaded = true;
            if (result.lrc != null) {
                lyricLines = LyricParser.parse(result.lrc);
                if (lyricLines.isEmpty()) {
                    lyricLines = LyricLine.emptyFallback(lyricSongName);
                }
            } else {
                lyricLines = LyricLine.emptyFallback(lyricSongName);
            }
            resetLyricScrollState();
            lyricAdapter.submitLines(lyricLines);
            PlaybackManager.Progress p = playback.progress().getValue();
            updateLyric(p == null ? 0 : p.position, true);
        });

        // 换歌：一次性行为（标题/封面/歌词重置/收藏查询），只在歌曲变化时触发
        playback.song().observe(getViewLifecycleOwner(), song -> {
            if (song == null) {
                // 队列被清空：回空闲态
                currentMid = "";
                currentFav = false;
                binding.tvSongName.setText(getString(R.string.not_playing));
                binding.tvSinger.setText(getString(R.string.tap_to_play_song));
                updateFavIcon(false);
                lyricLoaded = false;
                lyricLines = Collections.emptyList();
                resetLyricScrollState();
                lyricAdapter.submitLines(Collections.emptyList());
                return;
            }
            currentMid = song.mid;
            onSongChanged(song);
        });

        // 播放/暂停：只在变化时刷图标（不再每 500ms 重复 setImageResource）
        playback.playing().observe(getViewLifecycleOwner(), playing ->
                binding.ivPlayIcon.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play));

        // 进度：轮询流只推进进度条/时间/歌词行；播放页随开随关，关闭即停止消费
        playback.progress().observe(getViewLifecycleOwner(), p -> {
            if (p == null) {
                return;
            }
            if (!seeking && p.duration > 0) {
                binding.seekBar.setMax((int) p.duration);
                binding.seekBar.setProgress((int) p.position);
            }
            binding.tvPosition.setText(SongAdapter.formatTime((int) (p.position / 1000)));
            binding.tvDuration.setText(p.duration > 0
                    ? SongAdapter.formatTime((int) (p.duration / 1000)) : "--:--");
            updateLyric(p.position, false);
        });
    }

    /** 当前歌曲快照（song 流的 getValue，同步读不走观察者） */
    private OnlineSong currentSong() {
        return playback.song().getValue();
    }

    /**
     * 换歌一次性动作（song 流触发，只在歌变化时执行一次）：
     * 标题/歌手/背景封面刷新 → 歌词重置"加载中"并发起加载 → 收藏状态异步查库回显。
     * 与 progress 流分离：进度刷新不会重复触发这些重操作。
     */
    private void onSongChanged(OnlineSong song) {
        binding.tvSongName.setText(song.name);
        binding.tvSinger.setText(song.singer == null || song.singer.isEmpty()
                ? getString(R.string.unknown_singer) : song.singer);

        // 封面仅作为背景展示
        ImageLoader.get().load(binding.ivBg, coverUrl(song.albumMid));

        // 歌词：重置为加载中占位
        lyricLoaded = false;
        lyricLines = Collections.emptyList();
        resetLyricScrollState();
        lyricAdapter.submitLines(Collections.singletonList(new LyricLine(0, "歌词加载中…")));
        lyricAdapter.setCurrentLine(0);
        lyricSongMid = song.mid;
        lyricSongName = song.name;
        viewModel.loadLyric(song.mid);

        // 收藏状态随歌异步查询一次（替代原先每 500ms 主线程查库）
        String mid = song.mid;
        favorites.isSongFavorite(mid, fav -> {
            if (binding == null || !mid.equals(currentMid)) {
                return;
            }
            currentFav = fav;
            updateFavIcon(fav);
        });
    }

    /** 收藏红心图标切换（实心红/空心灰） */
    private void updateFavIcon(boolean fav) {
        binding.btnFav.setImageResource(fav ? R.drawable.ic_fav : R.drawable.ic_fav_border);
        binding.btnFav.setColorFilter(fav
                ? requireContext().getColor(R.color.color_red)
                : requireContext().getColor(R.color.text_secondary));
    }

    // ================= 歌词列表逻辑 =================

    /** 点任意歌词行：seek 到该行时间戳播放，并恢复跟随居中 */
    private void onLyricLineClick(int position) {
        if (lyricLines.isEmpty() || position < 0 || position >= lyricLines.size()) {
            return;
        }
        lyricBrowsing = false;
        lyricHandler.removeCallbacks(lyricRollback);
        binding.tvLyricHint.setVisibility(View.GONE);
        playback.seekTo(lyricLines.get(position).timeMs);
        currentLyricLine = position;
        lyricAdapter.setCurrentLine(position);
        scrollToLyricLine(position, true);
    }

    /**
     * 按播放进度推进歌词：当前行变化时增量刷新高亮；
     * 浏览态只刷高亮不滚动；instant=true 时立即贴位（歌词就绪）。
     */
    private void updateLyric(long positionMs, boolean instant) {
        if (!lyricLoaded || lyricLines == null || lyricLines.isEmpty()) {
            return;
        }
        int index = LyricParser.findLineIndex(lyricLines, positionMs);
        if (index < 0) {
            return;
        }
        boolean changed = index != currentLyricLine;
        if (changed) {
            currentLyricLine = index;
            lyricAdapter.setCurrentLine(index);
        }
        if (!lyricBrowsing && (changed || instant)) {
            scrollToLyricLine(index, !instant);
        }
    }

    /** 滚动到指定歌词行并垂直居中（smooth=false 时快速贴位） */
    private void scrollToLyricLine(int index, boolean smooth) {
        RecyclerView.LayoutManager lm = binding.rvLyric.getLayoutManager();
        if (!(lm instanceof LinearLayoutManager) || index < 0) {
            return;
        }
        CenterSmoothScroller scroller = new CenterSmoothScroller(requireContext(), !smooth);
        scroller.setTargetPosition(index);
        lm.startSmoothScroll(scroller);
    }

    /** 切歌/歌词重载时清空浏览态与滚回计时 */
    private void resetLyricScrollState() {
        currentLyricLine = -1;
        lyricBrowsing = false;
        lyricHandler.removeCallbacks(lyricRollback);
        if (binding != null) {
            binding.tvLyricHint.setVisibility(View.GONE);
        }
    }

    /** 把目标行滚到列表垂直居中位置 */
    private static class CenterSmoothScroller extends LinearSmoothScroller {
        private final boolean instant;

        CenterSmoothScroller(Context context, boolean instant) {
            super(context);
            this.instant = instant;
        }

        @Override
        protected int calculateTimeForScrolling(int dx) {
            return instant ? 1 : super.calculateTimeForScrolling(dx);
        }

        @Override
        public int calculateDtToFit(int viewStart, int viewEnd, int boxStart, int boxEnd,
                                    int snapPreference) {
            return (boxStart + (boxEnd - boxStart) / 2) - (viewStart + (viewEnd - viewStart) / 2);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        lyricHandler.removeCallbacks(lyricRollback);
        binding = null;
    }
}