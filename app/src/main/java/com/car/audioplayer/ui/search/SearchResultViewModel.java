package com.car.audioplayer.ui.search;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.car.audioplayer.data.model.OnlineSong;
import com.car.audioplayer.data.remote.QQMusicApi;

import java.util.List;

/**
 * 搜索结果 ViewModel：网络回调只写 LiveData，不触碰任何 UI。
 * SearchResultFragment 用 getViewLifecycleOwner 订阅，
 * 视图销毁后观察者自动解绑，从结构上消除回调 NPE。
 */
public class SearchResultViewModel extends ViewModel {

    private final MutableLiveData<List<OnlineSong>> results = new MutableLiveData<>();
    /** 提示文案（含网络层返回的动态错误 message，保持 String） */
    private final MutableLiveData<String> hint = new MutableLiveData<>("加载中…");

    /** 搜索结果歌曲列表 */
    public LiveData<List<OnlineSong>> getResults() {
        return results;
    }

    /** 提示文案（含网络层返回的动态错误信息；空串表示正常） */
    public LiveData<String> getHint() {
        return hint;
    }

    /** 关键词搜索：回调只更新数据，不碰 UI */
    public void search(String keyword) {
        QQMusicApi.searchSongs(keyword, new QQMusicApi.Callback<List<OnlineSong>>() {
            @Override
            public void onSuccess(List<OnlineSong> songs) {
                results.setValue(songs);
                hint.setValue(songs.isEmpty() ? "没有搜到相关歌曲" : "");
            }

            @Override
            public void onError(String message) {
                hint.setValue(message);
            }
        });
    }
}
