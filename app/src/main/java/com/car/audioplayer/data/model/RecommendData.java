package com.car.audioplayer.data.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 首页推荐聚合数据：推荐歌单 + 榜单入口。
 * 一次请求聚合两块数据，首页免多次串行请求；分别交给 PlaylistAdapter / RankAdapter 展示。
 */
public class RecommendData {

    /** 推荐歌单 */
    public final List<Playlist> playlists = new ArrayList<>();

    /** 榜单入口 */
    public final List<Rank> ranks = new ArrayList<>();
}