package com.car.audioplayer.service;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

import com.car.audioplayer.player.AudioFocusHelper;
import com.car.audioplayer.ui.main.MainActivity;

/**
 * 播放前台服务（阶段二播放核心 + 阶段三音频焦点）。
 * 继承 MediaSessionService：前台服务、MediaStyle 通知、MediaSession 生命周期自动托管；
 * 播放引擎用 ExoPlayer；音频焦点由 AudioFocusHelper 手动管理（LOSS 暂停不恢复、
 * TRANSIENT 暂停自动恢复、DUCK 降音量到 20%）。
 */
public class PlaybackService extends MediaSessionService {

    private MediaSession mediaSession;
    private ExoPlayer player;
    private AudioFocusHelper audioFocusHelper;
    /** TRANSIENT 丢失时标记：焦点回来后是否自动恢复播放（导航播报类临时打断才恢复） */
    private boolean resumeOnGain = false;

    /**
     * 服务创建：装配 ExoPlayer（媒体音频属性）+ MediaSession（系统媒体控制）+ 音频焦点。
     * 关键点：handleAudioFocus=false 把焦点处理权从 ExoPlayer 收回来自己管，
     * 四种焦点事件（LOSS/TRANSIENT/DUCK/GAIN）分别对应暂停/暂停并标记恢复/降音量/恢复。
     */
    @Override
    public void onCreate() {
        super.onCreate();

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build();
        // handleAudioFocus=false：关闭 ExoPlayer 内置焦点处理，改由 AudioFocusHelper 手动管理，避免双重抢焦点
        player = new ExoPlayer.Builder(this)
                .setAudioAttributes(audioAttributes, false)
                .setHandleAudioBecomingNoisy(true)
                .build();
        mediaSession = new MediaSession.Builder(this, player)
                .setSessionActivity(getSessionActivityPendingIntent())
                .build();

        audioFocusHelper = new AudioFocusHelper(this, new AudioFocusHelper.FocusListener() {
            @Override
            public void onFocusLoss() {
                resumeOnGain = false;
                player.pause();
            }

            @Override
            public void onFocusLossTransient() {
                resumeOnGain = true;
                player.pause();
            }

            @Override
            public void onFocusDuck() {
                player.setVolume(0.2f);
            }

            @Override
            public void onFocusGain() {
                player.setVolume(1.0f);
                if (resumeOnGain) {
                    resumeOnGain = false;
                    player.play();
                }
            }
        });

        // 开始播放时请求焦点；播放到结尾（STATE_ENDED）时放弃焦点
        player.addListener(new Player.Listener() {
            @Override
            public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
                if (playWhenReady) {
                    audioFocusHelper.requestFocus();
                }
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_ENDED) {
                    audioFocusHelper.abandonFocus();
                }
            }
        });
    }

    /** 点通知回到 App 的意图：单实例打开 MainActivity（避免叠两个主页面） */
    private PendingIntent getSessionActivityPendingIntent() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(this, 0, intent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? PendingIntent.FLAG_IMMUTABLE : 0);
    }

    /** MediaController 连接入口：把本服务的 session 交给 PlaybackManager 侧的 controller */
    @Nullable
    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }

    /** 服务销毁：按顺序释放焦点 → 播放器 → 会话（逆序释放防空指针） */
    @Override
    public void onDestroy() {
        if (audioFocusHelper != null) {
            audioFocusHelper.abandonFocus();
            audioFocusHelper = null;
        }
        if (mediaSession != null) {
            mediaSession.getPlayer().release();
            mediaSession.release();
            mediaSession = null;
        }
        super.onDestroy();
    }
}