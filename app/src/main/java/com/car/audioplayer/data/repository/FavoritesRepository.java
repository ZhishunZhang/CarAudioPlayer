package com.car.audioplayer.data.repository;

import android.content.Context;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;

import com.car.audioplayer.data.local.AppDatabase;
import com.car.audioplayer.data.local.dao.FavoritesDao;
import com.car.audioplayer.data.local.entity.FavoritePlaylist;
import com.car.audioplayer.data.local.entity.FavoriteSong;
import com.car.audioplayer.data.model.OnlineSong;
import com.car.audioplayer.data.model.Playlist;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 收藏仓库：统一的本地收藏入口（歌曲 + 歌单）。
 * 数据库不允许主线程查询：所有同步读写走单线程池，结果回抛主线程。
 * 列表观察（LiveData）由 Room 自动在后台计算并驱动 UI 刷新。
 */
public class FavoritesRepository {

    private static final String TAG = "FavoritesRepo";

    /** 异步结果回调（始终在主线程执行） */
    public interface ResultCallback<T> {
        void onResult(T result);
    }

    private static volatile FavoritesRepository instance;

    private final FavoritesDao dao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private FavoritesRepository(Context context) {
        dao = AppDatabase.getInstance(context.getApplicationContext()).favoritesDao();
    }

    public static FavoritesRepository get(Context context) {
        if (instance == null) {
            synchronized (FavoritesRepository.class) {
                if (instance == null) {
                    instance = new FavoritesRepository(context);
                }
            }
        }
        return instance;
    }

    // ===== 歌曲收藏 =====

    public LiveData<List<FavoriteSong>> observeFavoriteSongs() {
        return dao.observeFavoriteSongs();
    }

    /** 是否已收藏（后台查询，结果回主线程） */
    public void isSongFavorite(String songMid, ResultCallback<Boolean> callback) {
        Log.d(TAG, "isSongFavorite: " + songMid);
        executor.execute(() -> {
            boolean fav = dao.isSongFavorite(songMid) > 0;
            main.post(() -> callback.onResult(fav));
        });
    }

    /** 切换收藏状态，回调返回切换后的最新状态（主线程） */
    public void toggleSongFavorite(OnlineSong song, ResultCallback<Boolean> callback) {
        Log.d(TAG, "toggleSongFavorite: " + (song == null ? "null" : song.name));
        if (song == null || song.mid == null || song.mid.isEmpty()) {
            main.post(() -> callback.onResult(false));
            return;
        }
        executor.execute(() -> {
            boolean nowFavorite;
            if (dao.isSongFavorite(song.mid) > 0) {
                dao.deleteFavoriteSong(song.mid);
                nowFavorite = false;
            } else {
                dao.insertFavoriteSong(new FavoriteSong(
                        song.mid, song.name, song.singer, song.singerMid,
                        song.album, song.albumMid, song.interval, System.currentTimeMillis()));
                nowFavorite = true;
            }
            final boolean result = nowFavorite;
            main.post(() -> callback.onResult(result));
        });
    }

    // ===== 歌单收藏 =====

    public LiveData<List<FavoritePlaylist>> observeFavoritePlaylists() {
        return dao.observeFavoritePlaylists();
    }

    /** 是否已收藏歌单（后台查询，结果回主线程） */
    public void isPlaylistFavorite(String disstid, ResultCallback<Boolean> callback) {
        Log.d(TAG, "isPlaylistFavorite: " + disstid);
        executor.execute(() -> {
            boolean fav = dao.isPlaylistFavorite(disstid) > 0;
            main.post(() -> callback.onResult(fav));
        });
    }

    /** 切换歌单收藏状态，回调返回切换后的最新状态（主线程） */
    public void togglePlaylistFavorite(Playlist playlist, ResultCallback<Boolean> callback) {
        Log.d(TAG, "togglePlaylistFavorite: " + (playlist == null ? "null" : playlist.name));
        if (playlist == null || playlist.disstid == null || playlist.disstid.isEmpty()) {
            main.post(() -> callback.onResult(false));
            return;
        }
        executor.execute(() -> {
            boolean nowFavorite;
            if (dao.isPlaylistFavorite(playlist.disstid) > 0) {
                dao.deleteFavoritePlaylist(playlist.disstid);
                nowFavorite = false;
            } else {
                dao.insertFavoritePlaylist(new FavoritePlaylist(
                        playlist.disstid, playlist.name, playlist.cover, System.currentTimeMillis()));
                nowFavorite = true;
            }
            final boolean result = nowFavorite;
            main.post(() -> callback.onResult(result));
        });
    }
}