package com.car.audioplayer.data.model;

/**
 * 专辑（专辑详情 / 歌手专辑列表共用）。
 * 点击凭 albumMid 拉取专辑歌曲，封面同样由 albumMid 拼接。
 */
public class Album {

    /** 专辑 MID（getAlbumSongs 的钥匙） */
    public final String albumMid;

    /** 专辑名 */
    public final String name;

    /** 歌手名 */
    public final String singerName;

    /** 歌手 MID */
    public final String singerMid;

    /** 发行时间 */
    public final String pubTime;

    public Album(String albumMid, String name, String singerName, String singerMid, String pubTime) {
        this.albumMid = albumMid;
        this.name = name;
        this.singerName = singerName;
        this.singerMid = singerMid;
        this.pubTime = pubTime;
    }
}