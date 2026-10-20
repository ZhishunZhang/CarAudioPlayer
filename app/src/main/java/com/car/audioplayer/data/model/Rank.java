package com.car.audioplayer.data.model;

/**
 * 榜单卡片（getTopLists 列表项）。
 * 注意：服务端语义中 getTopLists 返回榜单列表，getRanks(topId) 返回榜内歌曲。
 * 数据流：榜单页卡片列表展示 → 点击凭 topId 拉取榜内歌曲。
 */
public class Rank {

    /** 榜单 ID（getRanks 的钥匙） */
    public final String topId;

    /** 榜单名 */
    public final String name;

    /** 封面地址 */
    public final String pic;

    /** 榜单说明（如"每周五更新"） */
    public final String desc;

    public Rank(String topId, String name, String pic, String desc) {
        this.topId = topId;
        this.name = name;
        this.pic = pic;
        this.desc = desc;
    }
}