package com.car.audioplayer.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * 本地收藏的歌单（"我的歌单"数据源）。
 * @Entity 映射 favorite_playlist 表，disstid 为主键，重复收藏同主键覆盖。
 */
@Entity(tableName = "favorite_playlist")
public class FavoritePlaylist {

    /** 歌单 ID：主键唯一标识（@NonNull 保证 Room 主键不为 null） */
    @PrimaryKey
    @NonNull
    public String disstid;

    /** 歌单名 */
    public String name;

    /** 封面地址 */
    public String cover;

    /** 收藏时间戳 */
    public long favoriteTime;

    /** 空构造：Room 反序列化必需 */
    public FavoritePlaylist() {
    }

    /** 业务构造：@Ignore 标记不给 Room 用 */
    @Ignore
    public FavoritePlaylist(String disstid, String name, String cover, long favoriteTime) {
        this.disstid = disstid;
        this.name = name;
        this.cover = cover;
        this.favoriteTime = favoriteTime;
    }
}