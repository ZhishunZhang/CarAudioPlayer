package com.car.audioplayer.ui.player;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.car.audioplayer.data.remote.QQMusicApi;

/**
 * 播放页 ViewModel：网络回调只写 LiveData，不触碰任何 UI。
 * PlayerFragment 用 getViewLifecycleOwner 订阅，
 * 视图销毁后观察者自动解绑，从结构上消除回调 NPE。
 */
public class PlayerViewModel extends ViewModel {

    /** 歌词结果（带歌曲 mid，用于区分切歌后的先后请求） */
    public static class LyricResult {
        public final String songMid;
        /** 原始 LRC 文本；null 表示加载失败 */
        public final String lrc;

        /** lrc=null 表示加载失败，便于 UI 区分“无歌词”与“获取失败” */
        public LyricResult(String songMid, String lrc) {
            this.songMid = songMid;
            this.lrc = lrc;
        }
    }

    private final MutableLiveData<LyricResult> lyricResult = new MutableLiveData<>();

    /** 歌词结果，附带歌曲 mid（用于丢弃切歌前的过期响应） */
    public LiveData<LyricResult> getLyricResult() {
        return lyricResult;
    }

    /** 加载歌词：回调只更新数据，不碰 UI */
    public void loadLyric(String songMid) {
        QQMusicApi.lyric(songMid, new QQMusicApi.Callback<String>() {
            @Override
            public void onSuccess(String lrc) {
                lyricResult.setValue(new LyricResult(songMid, lrc));
            }

            @Override
            public void onError(String message) {
                lyricResult.setValue(new LyricResult(songMid, null));
            }
        });
    }
}