package com.car.audioplayer.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.car.audioplayer.data.local.entity.FavoritePlaylist;
import com.car.audioplayer.data.local.entity.FavoriteSong;

import java.util.List;

/**
 * 收藏 DAO：歌曲收藏 + 歌单收藏，全部本地 Room。
 */
@Dao
public interface FavoritesDao {

    // ===== 歌曲收藏 =====

    /** 观察"我喜欢的歌曲"：LiveData 形式，收藏表一有增删自动重发，收藏页列表随库自动刷新 */
    @Query("SELECT * FROM favorite_song ORDER BY favoriteTime DESC")
    LiveData<List<FavoriteSong>> observeFavoriteSongs();

    /** 是否已收藏（同步 COUNT 查询，列表页/播放页快速判断用） */
    @Query("SELECT COUNT(*) FROM favorite_song WHERE songMid = :songMid")
    int isSongFavorite(String songMid);


    /** 新增收藏；REPLACE 策略：主键 songMid 冲突时整行覆盖，重复收藏天然幂等 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertFavoriteSong(FavoriteSong song);

    /** 按 mid 取消收藏 */
    @Query("DELETE FROM favorite_song WHERE songMid = :songMid")
    void deleteFavoriteSong(String songMid);

    // ===== 歌单收藏 =====

    /** 观察收藏歌单：同 observeFavoriteSongs，LiveData 自动刷新 */
    @Query("SELECT * FROM favorite_playlist ORDER BY favoriteTime DESC")
    LiveData<List<FavoritePlaylist>> observeFavoritePlaylists();

    /** 歌单是否已收藏（同步 COUNT） */
    @Query("SELECT COUNT(*) FROM favorite_playlist WHERE disstid = :disstid")
    int isPlaylistFavorite(String disstid);

    /** 新增歌单收藏；REPLACE 同上，主键去重 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertFavoritePlaylist(FavoritePlaylist playlist);

    /** 按 disstid 取消歌单收藏 */
    @Query("DELETE FROM favorite_playlist WHERE disstid = :disstid")
    void deleteFavoritePlaylist(String disstid);
}