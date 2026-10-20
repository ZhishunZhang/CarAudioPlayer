package com.car.audioplayer.ui.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.car.audioplayer.R;
import com.car.audioplayer.data.model.Banner;
import com.car.audioplayer.data.model.RecommendData;
import com.car.audioplayer.data.remote.QQMusicApi;

import java.util.Collections;
import java.util.List;

/**
 * 首页 ViewModel：网络回调只写 LiveData，不触碰任何 UI。
 * HomeFragment 用 getViewLifecycleOwner 订阅，
 * 视图销毁后观察者自动解绑，从结构上消除回调 NPE。
 */
public class HomeViewModel extends ViewModel {

    private final MutableLiveData<RecommendData> recommend = new MutableLiveData<>();
    private final MutableLiveData<List<Banner>> banners = new MutableLiveData<>();
    private final MutableLiveData<List<String>> hotkeys = new MutableLiveData<>();
    /** 提示文案资源 ID（无 Context，传 R.string 引用，由 Fragment 解析成文字） */
    private final MutableLiveData<Integer> hint = new MutableLiveData<>(R.string.loading);

    /** 推荐数据（歌单 + 榜单），供 Fragment 订阅 */
    public LiveData<RecommendData> getRecommend() {
        return recommend;
    }

    /** Banner 列表 */
    public LiveData<List<Banner>> getBanners() {
        return banners;
    }

    /** 热搜词列表 */
    public LiveData<List<String>> getHotkeys() {
        return hotkeys;
    }

    /** 提示文案资源 ID（0/null 表示清除提示） */
    public LiveData<Integer> getHint() {
        return hint;
    }

    /** 推荐（歌单 + 榜单）：回调只更新数据，不碰 UI */
    public void loadRecommend() {
        QQMusicApi.recommend(new QQMusicApi.Callback<RecommendData>() {
            @Override
            public void onSuccess(RecommendData result) {
                recommend.setValue(result);
                hint.setValue(null);
            }

            @Override
            public void onError(String message) {
                hint.setValue(R.string.home_load_failed);
            }
        });
    }

    /** Banner：回调只更新数据，不碰 UI */
    public void loadBanners() {
        QQMusicApi.recommendBanner(new QQMusicApi.Callback<List<Banner>>() {
            @Override
            public void onSuccess(List<Banner> list) {
                banners.setValue(list);
            }

            @Override
            public void onError(String message) {
                banners.setValue(Collections.emptyList());
            }
        });
    }

    /** 热搜词：回调只更新数据，不碰 UI */
    public void loadHotkeys() {
        QQMusicApi.hotkey(new QQMusicApi.Callback<List<String>>() {
            @Override
            public void onSuccess(List<String> keys) {
                hotkeys.setValue(keys);
            }

            @Override
            public void onError(String message) {
                hotkeys.setValue(Collections.emptyList());
            }
        });
    }
}