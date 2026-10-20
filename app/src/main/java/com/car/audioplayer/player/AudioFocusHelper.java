package com.car.audioplayer.player;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.util.Log;

/**
 * 音频焦点管理助手（阶段三音频焦点）。
 * 封装 AudioManager 的焦点请求/放弃/变化监听，把系统焦点变化翻译成
 * LOSS / TRANSIENT / DUCK / GAIN 四类回调，交给调用方（PlaybackService）执行具体播放动作。
 * 统一用 TAG="AudioFocus" 打日志，方便在 logcat 过滤观察焦点回调。
 */
public class AudioFocusHelper {

    private static final String TAG = "AudioFocus";

    /** 焦点变化回调 */
    public interface FocusListener {
        /** 永久失去焦点：暂停，不自动恢复 */
        void onFocusLoss();

        /** 暂时失去焦点：暂停，焦点回来自动恢复 */
        void onFocusLossTransient();

        /** 可降低音量：降到 20% 继续播，不暂停 */
        void onFocusDuck();

        /** 重新获得焦点：恢复音量，必要时恢复播放 */
        void onFocusGain();
    }

    private final AudioManager audioManager;
    private final FocusListener listener;
    private final AudioManager.OnAudioFocusChangeListener focusChangeListener;
    private AudioFocusRequest focusRequest;

    public AudioFocusHelper(Context context, FocusListener listener) {
        this.audioManager = (AudioManager) context.getApplicationContext()
                .getSystemService(Context.AUDIO_SERVICE);
        this.listener = listener;
        // 在 listener 赋值后再初始化回调，避免字段初始化器引用尚未赋值的 final 字段
        this.focusChangeListener = change -> {
            switch (change) {
                case AudioManager.AUDIOFOCUS_LOSS:
                    Log.d(TAG, "焦点变化 -> LOSS（永久失去）");
                    this.listener.onFocusLoss();
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    Log.d(TAG, "焦点变化 -> TRANSIENT（暂时失去）");
                    this.listener.onFocusLossTransient();
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    Log.d(TAG, "焦点变化 -> CAN_DUCK（降音量）");
                    this.listener.onFocusDuck();
                    break;
                case AudioManager.AUDIOFOCUS_GAIN:
                    Log.d(TAG, "焦点变化 -> GAIN（重新获得）");
                    this.listener.onFocusGain();
                    break;
                default:
                    break;
            }
        };
    }

    /** 请求音乐播放焦点，成功返回 true */
    public boolean requestFocus() {
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        boolean granted;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener(focusChangeListener)
                    .build();
            granted = audioManager.requestAudioFocus(focusRequest)
                    == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        } else {
            granted = audioManager.requestAudioFocus(
                    focusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
                    == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        }
        Log.d(TAG, "requestFocus -> " + (granted ? "GRANTED" : "FAILED"));
        return granted;
    }

    /** 放弃焦点 */
    public void abandonFocus() {
        Log.d(TAG, "abandonFocus -> 放弃焦点");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (focusRequest != null) {
                audioManager.abandonAudioFocusRequest(focusRequest);
            }
        } else {
            audioManager.abandonAudioFocus(focusChangeListener);
        }
    }
}