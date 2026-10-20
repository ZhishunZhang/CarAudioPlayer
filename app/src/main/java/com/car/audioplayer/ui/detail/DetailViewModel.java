package com.car.audioplayer.ui.detail;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.car.audioplayer.data.model.Album;
import com.car.audioplayer.data.model.OnlineSong;
import com.car.audioplayer.data.model.PlaylistDetail;
import com.car.audioplayer.data.remote.QQMusicApi;

import java.util.Collections;
import java.util.List;

/**
 * 详情页 ViewModel（榜单/歌单/专辑/歌手四个详情页共用）。
 * 网络回调只写 LiveData，不触碰任何 UI；Fragment 用 getViewLifecycleOwner 订阅，
 * 视图销毁后观察者自动解绑，从结构上消除"回调回来时 binding 已置空"的 NPE。
 */
public class DetailViewModel extends ViewModel {

    private final MutableLiveData<List<OnlineSong>> rankSongs = new MutableLiveData<>();
    private final MutableLiveData<List<OnlineSong>> singerSongs = new MutableLiveData<>();
    private final MutableLiveData<List<Album>> singerAlbums = new MutableLiveData<>();
    private final MutableLiveData<String> singerDesc = new MutableLiveData<>();
    private final MutableLiveData<List<OnlineSong>> albumSongs = new MutableLiveData<>();
    private final MutableLiveData<PlaylistDetail> songListDetail = new MutableLiveData<>();
    /** 提示文案（含网络层动态错误 message，保持 String） */
    private final MutableLiveData<String> hint = new MutableLiveData<>("加载中…");

    /** 榜单内歌曲（榜单详情页用） */
    public LiveData<List<OnlineSong>> getRankSongs() {
        return rankSongs;
    }

    /** 歌手热门歌曲（歌手详情页用） */
    public LiveData<List<OnlineSong>> getSingerSongs() {
        return singerSongs;
    }

    /** 歌手专辑流（歌手详情页用） */
    public LiveData<List<Album>> getSingerAlbums() {
        return singerAlbums;
    }

    /** 歌手简介；null 表示获取失败（Fragment 显示占位文案） */
    public LiveData<String> getSingerDesc() {
        return singerDesc;
    }

    /** 专辑歌曲列表（专辑详情页用） */
    public LiveData<List<OnlineSong>> getAlbumSongs() {
        return albumSongs;
    }

    /** 歌单详情：歌曲 + 头部名称/封面/简介（歌单详情页用） */
    public LiveData<PlaylistDetail> getSongListDetail() {
        return songListDetail;
    }

    /** 共用提示文案（空串表示正常） */
    public LiveData<String> getHint() {
        return hint;
    }

    /** 加载榜单歌曲：回调只更新数据，不碰 UI */
    public void loadRank(String topId) {
        QQMusicApi.ranks(topId, new QQMusicApi.Callback<List<OnlineSong>>() {
            @Override
            public void onSuccess(List<OnlineSong> list) {
                rankSongs.setValue(list);
                hint.setValue(list.isEmpty() ? "榜单暂无数据" : "");
            }

            @Override
            public void onError(String message) {
                hint.setValue(message);
            }
        });
    }

    /** 加载歌手热门歌曲：回调只更新数据，不碰 UI */
    public void loadSingerHotsong(String singerMid) {
        QQMusicApi.singerHotsong(singerMid, new QQMusicApi.Callback<List<OnlineSong>>() {
            @Override
            public void onSuccess(List<OnlineSong> list) {
                singerSongs.setValue(list);
                hint.setValue(list.isEmpty() ? "暂无热门歌曲数据" : "");
            }

            @Override
            public void onError(String message) {
                hint.setValue(message);
            }
        });
    }

    /** 加载歌手专辑流：回调只更新数据，不碰 UI（失败时给空列表，Fragment 隐藏专辑区） */
    public void loadSingerAlbums(String singerMid) {
        QQMusicApi.singerAlbum(singerMid, new QQMusicApi.Callback<List<Album>>() {
            @Override
            public void onSuccess(List<Album> albums) {
                singerAlbums.setValue(albums);
            }

            @Override
            public void onError(String message) {
                singerAlbums.setValue(Collections.emptyList());
            }
        });
    }

    /** 加载歌手简介：回调只更新数据，不碰 UI（失败时给 null，Fragment 显示占位文案） */
    public void loadSingerDesc(String singerMid) {
        QQMusicApi.singerDesc(singerMid, new QQMusicApi.Callback<String>() {
            @Override
            public void onSuccess(String desc) {
                singerDesc.setValue(desc);
            }

            @Override
            public void onError(String message) {
                singerDesc.setValue(null);
            }
        });
    }

    /** 加载专辑歌曲：回调只更新数据，不碰 UI */
    public void loadAlbumSongs(String albumMid) {
        QQMusicApi.albumSongs(albumMid, new QQMusicApi.Callback<List<OnlineSong>>() {
            @Override
            public void onSuccess(List<OnlineSong> list) {
                albumSongs.setValue(list);
                hint.setValue(list.isEmpty() ? "专辑暂无歌曲" : "");
            }

            @Override
            public void onError(String message) {
                hint.setValue(message);
            }
        });
    }

    /** 加载歌单详情：回调只更新数据，不碰 UI（头部 name/cover/desc 的 UI 更新留在 Fragment） */
    public void loadSongListDetail(String disstid) {
        QQMusicApi.songListDetail(disstid, new QQMusicApi.Callback<PlaylistDetail>() {
            @Override
            public void onSuccess(PlaylistDetail detail) {
                songListDetail.setValue(detail);
                hint.setValue(detail.songs.isEmpty() ? "歌单暂无歌曲" : "");
            }

            @Override
            public void onError(String message) {
                hint.setValue(message);
            }
        });
    }
}