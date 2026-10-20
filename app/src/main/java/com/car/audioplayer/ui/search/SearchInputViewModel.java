package com.car.audioplayer.ui.search;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.car.audioplayer.data.model.OnlineSong;
import com.car.audioplayer.data.remote.QQMusicApi;

import java.util.List;

/**
 * 搜索输入页 ViewModel：热搜词 + 输入联想。
 * 联想改用 searchByKey（返回 albummid，可拼封面），替代 smartbox（无专辑字段、无封面）。
 * 回调只写 LiveData，不触碰任何 UI；generation 丢弃过期的联想响应，防止快速输入时旧结果覆盖新结果。
 */
public class SearchInputViewModel extends ViewModel {

    private final MutableLiveData<List<String>> hotkeys = new MutableLiveData<>();
    private final MutableLiveData<List<OnlineSong>> suggests = new MutableLiveData<>();

    /** 联想请求代数：快速输入时丢弃过期响应 */
    private int suggestGeneration = 0;

    /** 热搜词列表（加载失败保持空，不打断输入流程） */
    public LiveData<List<String>> getHotkeys() {
        return hotkeys;
    }

    /** 输入联想列表（过期响应按 generation 丢弃，不会发出） */
    public LiveData<List<OnlineSong>> getSuggests() {
        return suggests;
    }

    /** 热搜词：失败不打断页面（保持空列表） */
    public void loadHotkeys() {
        QQMusicApi.hotkey(new QQMusicApi.Callback<List<String>>() {
            @Override
            public void onSuccess(List<String> keys) {
                hotkeys.setValue(keys);
            }

            @Override
            public void onError(String message) {
                // 热词失败不打断页面
            }
        });
    }

    /** 输入联想：走搜索接口（有封面字段），过期响应直接丢弃 */
    public void loadSuggest(String key) {
        int gen = ++suggestGeneration;
        QQMusicApi.searchSongs(key, new QQMusicApi.Callback<List<OnlineSong>>() {
            @Override
            public void onSuccess(List<OnlineSong> songs) {
                if (gen != suggestGeneration) {
                    return;
                }
                suggests.setValue(songs);
            }

            @Override
            public void onError(String message) {
                // 联想失败保持热词
            }
        });
    }
}