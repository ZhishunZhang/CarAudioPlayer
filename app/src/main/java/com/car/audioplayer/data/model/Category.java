package com.car.audioplayer.data.model;

/**
 * 歌单分类。
 * 分类栏点击后以 id 为参数调 getSongLists，拉取该分类下的歌单列表。
 */
public class Category {

    /** 分类名 */
    public final String name;

    /** 分类 ID（getSongLists 的参数） */
    public final long id;

    public Category(String name, long id) {
        this.name = name;
        this.id = id;
    }
}