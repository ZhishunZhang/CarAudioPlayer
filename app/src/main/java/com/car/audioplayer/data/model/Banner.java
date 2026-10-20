package com.car.audioplayer.data.model;

/**
 * 首页轮播 Banner。
 * 点击跳转由 jumpType 决定：1=歌单（jumpId 为 disstid），0=不跳转。
 */
public class Banner {

    /** 封面图地址 */
    public final String cover;

    /** 主标题 */
    public final String title;

    /** 副标题 */
    public final String subTitle;

    /** 跳转类型：0 无跳转；1 歌单（跳转 ID 为 disstid） */
    public final int jumpType;

    /** 跳转目标 ID */
    public final String jumpId;

    public Banner(String cover, String title, String subTitle, int jumpType, String jumpId) {
        this.cover = cover;
        this.title = title;
        this.subTitle = subTitle;
        this.jumpType = jumpType;
        this.jumpId = jumpId;
    }
}