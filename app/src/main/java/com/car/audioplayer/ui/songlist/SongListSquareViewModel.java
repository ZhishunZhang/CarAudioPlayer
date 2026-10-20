package com.car.audioplayer.ui.songlist;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.car.audioplayer.R;
import com.car.audioplayer.data.model.Category;
import com.car.audioplayer.data.model.Playlist;
import com.car.audioplayer.data.remote.QQMusicApi;

import java.util.List;

/**
 * 歌单广场 ViewModel：网络回调只写 LiveData，不触碰任何 UI。
 * SongListSquareFragment 用 getViewLifecycleOwner 订阅，
 * 视图销毁后观察者自动解绑，从结构上消除回调 NPE。
 */
public class SongListSquareViewModel extends ViewModel {

    /** 分类结果；null 表示加载失败（只保留"全部"，不影响主流程） */
    private final MutableLiveData<List<Category>> categories = new MutableLiveData<>();
    private final MutableLiveData<List<Playlist>> playlists = new MutableLiveData<>();
    /** 提示文案资源 ID（无 Context，传 R.string 引用，由 Fragment 解析成文字） */
    private final MutableLiveData<Integer> hint = new MutableLiveData<>(R.string.loading);

    /** 分类列表；null 表示加载失败（Fragment 只保留「全部」入口） */
    public LiveData<List<Category>> getCategories() {
        return categories;
    }

    /** 当前选中分类下的歌单数据 */
    public LiveData<List<Playlist>> getPlaylists() {
        return playlists;
    }

    /** 提示文案资源 ID（0/null 表示清除提示） */
    public LiveData<Integer> getHint() {
        return hint;
    }

    /** 歌单分类：回调只更新数据，不碰 UI */
    public void loadCategories() {
        QQMusicApi.songListCategories(new QQMusicApi.Callback<List<Category>>() {
            @Override
            public void onSuccess(List<Category> list) {
                categories.setValue(list);
            }

            @Override
            public void onError(String message) {
                categories.setValue(null);
            }
        });
    }

    /** 歌单列表：回调只更新数据，不碰 UI */
    public void loadPlaylists(long categoryId, int sortId, int page) {
        hint.setValue(R.string.loading);
        QQMusicApi.songLists(categoryId, sortId, page, new QQMusicApi.Callback<List<Playlist>>() {
            @Override
            public void onSuccess(List<Playlist> list) {
                playlists.setValue(list);
                hint.setValue(list.isEmpty() ? R.string.category_empty : 0);
            }

            @Override
            public void onError(String message) {
                hint.setValue(R.string.load_failed_check_relay);
            }
        });
    }
}