package com.car.audioplayer.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * 本地收藏的在线歌曲（"我喜欢的歌曲"数据源）。
 * 收藏走本地 Room，与账号体系无关。
 * @Entity 映射 favorite_song 表；songMid 为 @PrimaryKey 主键，同歌重复收藏即同主键整行覆盖。
 */
@Entity(tableName = "favorite_song")
public class FavoriteSong {

    /** 歌曲 MID：主键唯一标识（@NonNull 保证 Room 主键不为 null） */
    @PrimaryKey
    @NonNull
    public String songMid;

    /** 歌名 */
    public String name;

    /** 歌手 */
    public String singer;

    /** 歌手 MID */
    public String singerMid;

    /** 专辑名 */
    public String album;

    /** 封面/专辑 MID */
    public String albumMid;

    /** 时长（秒） */
    public int interval;

    /** 收藏时间戳 */
    public long favoriteTime;

    /** 空构造：Room 反序列化必需，业务代码不用 */
    public FavoriteSong() {
    }

    /** 业务构造：@Ignore 标记不给 Room 用（Room 只认空构造 + 字段） */
    @Ignore
    public FavoriteSong(String songMid, String name, String singer, String singerMid,
                        String album, String albumMid, int interval, long favoriteTime) {
        this.songMid = songMid;
        this.name = name;
        this.singer = singer;
        this.singerMid = singerMid;
        this.album = album;
        this.albumMid = albumMid;
        this.interval = interval;
        this.favoriteTime = favoriteTime;
    }
}