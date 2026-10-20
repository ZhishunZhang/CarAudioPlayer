package com.car.audioplayer.data.model;

import java.util.List;

/**
 * 歌单详情：元信息 + 歌曲列表。
 * songs 即可直接播放的 OnlineSong 列表，详情页直接交给 SongAdapter 展示。
 */
public class PlaylistDetail {

    /** 歌单 ID */
    public final String disstid;

    /** 歌单名 */
    public final String name;

    /** 封面地址 */
    public final String cover;

    /** 简介 */
    public final String desc;

    /** 歌曲列表（已含播放所需全部信息） */
    public final List<OnlineSong> songs;

    public PlaylistDetail(String disstid, String name, String cover, String desc, List<OnlineSong> songs) {
        this.disstid = disstid;
        this.name = name;
        this.cover = cover;
        this.desc = desc;
        this.songs = songs;
    }
}