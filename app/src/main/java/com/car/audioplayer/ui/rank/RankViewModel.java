package com.car.audioplayer.ui.rank;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.car.audioplayer.R;
import com.car.audioplayer.data.model.Rank;
import com.car.audioplayer.data.remote.QQMusicApi;

import java.util.List;

/**
 * 榜单页 ViewModel：网络回调只写 LiveData，不触碰任何 UI。
 * RankFragment 用 getViewLifecycleOwner 订阅，
 * 视图销毁后观察者自动解绑，从结构上消除回调 NPE。
 */
public class RankViewModel extends ViewModel {

    private final MutableLiveData<List<Rank>> topLists = new MutableLiveData<>();
    /** 提示文案资源 ID（无 Context，传 R.string 引用，由 Fragment 解析成文字） */
    private final MutableLiveData<Integer> hint = new MutableLiveData<>(R.string.loading);

    /** 全部榜单卡片数据 */
    public LiveData<List<Rank>> getTopLists() {
        return topLists;
    }

    /** 提示文案资源 ID（0/null 表示清除提示） */
    public LiveData<Integer> getHint() {
        return hint;
    }

    /** 加载全部榜单：回调只更新数据，不碰 UI */
    public void loadTopLists() {
        QQMusicApi.topLists(new QQMusicApi.Callback<List<Rank>>() {
            @Override
            public void onSuccess(List<Rank> ranks) {
                topLists.setValue(ranks);
                hint.setValue(ranks.isEmpty() ? R.string.rank_empty : 0);
            }

            @Override
            public void onError(String message) {
                hint.setValue(R.string.load_failed_check_relay);
            }
        });
    }
}