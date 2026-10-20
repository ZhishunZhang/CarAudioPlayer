package com.car.audioplayer.data.model;

/**
 * 歌单卡片（歌单广场 / 推荐歌单 / 我的收藏共用）。
 * 点击卡片后凭 disstid 拉取歌单详情（PlaylistDetail）。
 */
public class Playlist {

    /** 歌单 ID（数字串，getSongListDetail 的钥匙） */
    public final String disstid;

    /** 歌单名 */
    public final String name;

    /** 封面图地址（完整 URL） */
    public final String cover;

    /** 播放量（人次） */
    public final long listenCount;

    public Playlist(String disstid, String name, String cover, long listenCount) {
        this.disstid = disstid;
        this.name = name;
        this.cover = cover;
        this.listenCount = listenCount;
    }
}