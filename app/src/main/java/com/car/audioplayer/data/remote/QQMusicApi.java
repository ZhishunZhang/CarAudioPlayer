package com.car.audioplayer.data.remote;

import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.util.Log;

import com.car.audioplayer.data.model.Album;
import com.car.audioplayer.data.model.Banner;
import com.car.audioplayer.data.model.Category;
import com.car.audioplayer.data.model.OnlineSong;
import com.car.audioplayer.data.model.Playlist;
import com.car.audioplayer.data.model.Rank;
import com.car.audioplayer.data.repository.ApiCache;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * QQ 音乐中转服务的网络请求层（按服务端实测契约实现）。
 * 响应统一包装为 {response: ...}，部分接口的 response 内还嵌套一层 data；
 * httpGetJson 会做一次"纯数据壳"剥除。所有字段解析做多 key 兼容。
 * BASE_URL 指向电脑上跑的中转服务，平板需与电脑同一局域网（或 USB 隧道）。
 */
public class QQMusicApi {

    private static final String TAG = "QQMusicApi";

    /** 中转服务地址：电脑的局域网 IP + 端口 3200。换网络环境只改这里 */
    public static final String BASE_URL = "http://127.0.0.1:3200";

    /** QQ 音乐官方图床模板：用封面 MID 直接拼 URL（客户端本地拼，不额外调接口） */
    public static final String IMG_BASE = "https://y.gtimg.cn/music/photo_new/T002R300x300M000";

    /** 并发请求池：原单线程会让首页等多个请求串行排队，改为 4 并发后聚合页按最慢请求计耗时 */
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** 缓存击穿保护：单飞合并器。key → 在途请求 Future，同 key 并发只发一次网络。 */
    private static final Map<String, CompletableFuture<JSONObject>> IN_FLIGHT = new ConcurrentHashMap<>();
    private static final Map<String, CompletableFuture<String>> STRING_FLIGHT = new ConcurrentHashMap<>();

    public interface Callback<T> {
        void onSuccess(T result);

        void onError(String message);
    }

    // ================= 基础能力 =================

    /** 用封面 MID 拼完整封面图 URL */
    public static String coverUrl(String mid) {
        if (mid == null || mid.isEmpty()) {
            return "";
        }
        return IMG_BASE + mid + ".jpg?max_age=2592000";
    }

    /** 歌手头像 URL 模板（T001 为歌手头像规格） */
    public static String singerAvatarUrl(String singerMid) {
        if (singerMid == null || singerMid.isEmpty()) {
            return "";
        }
        return "https://y.gtimg.cn/music/photo_new/T001R300x300M000" + singerMid + ".jpg?max_age=2592000";
    }

    /** 后台执行网络任务，回调切回主线程 */
    private static void run(Runnable task) {
        EXECUTOR.execute(task);
    }

    private static <T> void post(Callback<T> callback, T value) {
        MAIN.post(() -> callback.onSuccess(value));
    }

    private static <T> void postError(Callback<T> callback, String message) {
        MAIN.post(() -> callback.onError(message));
    }

    /** 榜单/歌单等低频变更数据的缓存时效：1 小时 */
    private static final long CACHE_TTL_MS = 60 * 60 * 1000;

    /** 几乎不变的数据（歌单分类条）：24 小时 */
    private static final long CACHE_TTL_LONG_MS = 24 * 60 * 60 * 1000L;

    /** 搜索类数据（联想/结果）：10 分钟短时效 */
    private static final long CACHE_TTL_SHORT_MS = 10 * 60 * 1000;

    /** GET 并解析：剥 {response} 包装，再剥一层"无 code 的 data 壳"，返回负载 JSONObject */
    private static JSONObject httpGetJson(String path) throws Exception {
        return parseBody(httpGet(BASE_URL + path));
    }

    /** 带缓存的 GET：命中未过期秒回；网络失败回退过期旧数据 */
    private static JSONObject httpGetJsonCached(String path, String cacheKey) throws Exception {
        return httpGetJsonCached(path, cacheKey, CACHE_TTL_MS);
    }

    /** 带缓存的 GET（自定义时效）：命中未过期秒回；网络失败回退过期旧数据。单飞合并防击穿 */
    private static JSONObject httpGetJsonCached(String path, String cacheKey, long ttlMs) throws Exception {
        long start = System.currentTimeMillis();
        String cached = ApiCache.get(cacheKey, ttlMs);
        Log.d(TAG, "httpGetJsonCached: key=" + cacheKey + ", cacheHit=" + (cached != null));
        if (cached != null) {
            try {
                return parseBody(cached);
            } catch (Exception ignored) {
                // 缓存损坏，走网络
            }
        }
        // 单飞：同 key 有在途请求则复用其 Future，真正并发只发一次网络
        CompletableFuture<JSONObject> future =
                IN_FLIGHT.computeIfAbsent("cci_" + cacheKey, k -> CompletableFuture.supplyAsync(() -> {
                    try {
                        String body = httpGet(BASE_URL + path);
                        JSONObject result = parseBody(body);
                        ApiCache.put(cacheKey, body);
                        return result;
                    } catch (Exception e) {
                        // 网络失败回退过期旧数据；无则该原始异常抛给调用方
                        String stale = ApiCache.getStale(cacheKey);
                        if (stale != null) {
                            try {
                                return parseBody(stale);
                            } catch (Exception ignored) {
                            }
                        }
                        throw new java.util.concurrent.CompletionException(e);
                    }
                }, EXECUTOR));
        try {
            JSONObject result = future.get();
            Log.d(TAG, "httpGetJsonCached: key=" + cacheKey + ", cost="
                    + (System.currentTimeMillis() - start) + "ms");
            return result;
        } finally {
            IN_FLIGHT.remove("cci_" + cacheKey, future);
        }
    }

    /** 解析响应体：剥 {response} 包装，再剥一层"无 code 的 data 壳"，返回负载 JSONObject */
    private static JSONObject parseBody(String body) throws Exception {
        JSONObject root = new JSONObject(body);
        if (root.has("error")) {
            throw new IllegalStateException("接口返回错误：" + root.opt("error"));
        }
        Object resp = root.opt("response");
        JSONObject data;
        if (resp instanceof JSONObject) {
            data = (JSONObject) resp;
        } else if (resp instanceof String && !((String) resp).isEmpty()) {
            throw new IllegalStateException("接口返回异常：" + resp);
        } else {
            data = root;
        }
        return peel(data);
    }

    /** 剥除包装后的纯数据壳：若存在 data 子对象且其内部无 code/error 字段，则视为负载层 */
    private static JSONObject peel(JSONObject o) {
        JSONObject d = o.optJSONObject("data");
        if (d != null && !d.has("code") && !d.has("error")) {
            return d;
        }
        return o;
    }

    private static String httpGet(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setRequestMethod("GET");
        try {
            int code = conn.getResponseCode();
            StringBuilder sb = new StringBuilder();
            InputStream in = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            if (in != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();
            }
            if (code < 200 || code >= 300) {
                String msg = "HTTP " + code;
                try {
                    JSONObject err = new JSONObject(sb.toString());
                    Object e = err.opt("error");
                    Object r = err.opt("response");
                    if (e != null) {
                        msg = String.valueOf(e);
                    } else if (r != null) {
                        msg = String.valueOf(r);
                    }
                } catch (Exception ignored) {
                }
                throw new IllegalStateException(msg + "，请确认中转服务已启动");
            }
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }

    /** 通用字段兼容取值 */
    static String opt(JSONObject o, String... keys) {
        for (String k : keys) {
            String v = o.optString(k, "");
            if (!v.isEmpty()) {
                return v;
            }
        }
        return "";
    }

    /** 歌手字段兼容：singer 数组（name 拼接）或 singerName/singername 字符串 */
    private static String parseSinger(JSONObject songJson) {
        JSONArray singers = songJson.optJSONArray("singer");
        if (singers != null && singers.length() > 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < singers.length(); i++) {
                JSONObject s = singers.optJSONObject(i);
                if (s == null) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append("/");
                }
                sb.append(s.optString("name", ""));
            }
            if (sb.length() > 0) {
                return sb.toString();
            }
        }
        return songJson.optString("singerName", songJson.optString("singername", songJson.optString("singer", "未知歌手")));
    }

    /** 歌手 MID 兼容 */
    private static String parseSingerMid(JSONObject songJson) {
        JSONArray singers = songJson.optJSONArray("singer");
        if (singers != null && singers.length() > 0) {
            JSONObject s = singers.optJSONObject(0);
            if (s != null) {
                return s.optString("mid", "");
            }
        }
        return songJson.optString("singerMid", songJson.optString("singermid", ""));
    }

    /** 从一首歌 JSON 解析 OnlineSong（字段大兼容） */
    public static OnlineSong parseSong(JSONObject o) {
        String mid = opt(o, "songmid", "mid", "song_mid");
        if (mid.isEmpty()) {
            return null;
        }
        String name = opt(o, "songname", "songName", "name", "title");
        if (name.isEmpty()) {
            name = "未知歌曲";
        }
        String singer = parseSinger(o);
        String singerMid = parseSingerMid(o);
        // album 兼容两种形态：字符串字段 或 嵌套对象 {name, mid, pmid}
        JSONObject albumObj = o.optJSONObject("album");
        String album = albumObj != null
                ? opt(albumObj, "name", "title", "albumname")
                : opt(o, "albumname", "albumName", "album");
        String albumMid = albumObj != null
                ? opt(albumObj, "mid", "albummid", "pmid") : "";
        if (albumMid.isEmpty()) {
            albumMid = opt(o, "albummid", "albumMid", "strMediaMid", "imageMid");
        }
        int interval = o.optInt("interval", o.optInt("songInterval", 0));
        OnlineSong song = new OnlineSong(mid, name, singer, singerMid, album, albumMid, "", interval);
        // VIP 标记：搜索接口字段为 pay.payplay，详情/歌单/歌手/专辑为 pay.pay_play，兼容两种
        JSONObject pay = o.optJSONObject("pay");
        if (pay != null) {
            song.payPlay = pay.optInt("payplay", pay.optInt("pay_play", 0)) == 1;
        }
        return song;
    }

    /** 嵌套结构里找歌曲数组（songlist/songList/list/song/songInfoList 等） */
    static JSONArray findSongArray(JSONObject o) {
        if (o == null) {
            return null;
        }
        String[] keys = {"songlist", "songList", "list", "song", "songInfoList", "song_info_list", "song_list"};
        for (String k : keys) {
            JSONArray arr = o.optJSONArray(k);
            if (arr != null && arr.length() > 0) {
                return arr;
            }
        }
        return null;
    }

    /** 从歌曲数组元素解析（兼容 songInfo 包裹与平铺两种） */
    private static OnlineSong parseSongItem(JSONObject o) {
        if (o == null) {
            return null;
        }
        JSONObject info = o.optJSONObject("songInfo");
        OnlineSong s = parseSong(info != null ? info : o);
        if (s == null && info != null) {
            s = parseSong(o);
        }
        return s;
    }

    // ================= 搜索 =================

    /** 关键词搜索歌曲：GET /getSearchByKey */
    public static void searchSongs(String keyword, Callback<List<OnlineSong>> callback) {
        run(() -> {
            try {
                String encoded = URLEncoder.encode(keyword, "UTF-8");
                JSONObject data = httpGetJsonCached("/getSearchByKey?key=" + encoded + "&limit=30",
                        "search_" + encoded, CACHE_TTL_SHORT_MS);
                List<OnlineSong> songs = new ArrayList<>();
                JSONObject songObj = data.optJSONObject("song");
                JSONArray list = songObj == null ? null : songObj.optJSONArray("list");
                if (list != null) {
                    for (int i = 0; i < list.length(); i++) {
                        OnlineSong s = parseSong(list.optJSONObject(i));
                        if (s != null) {
                            songs.add(s);
                        }
                    }
                }
                post(callback, songs);
            } catch (Exception e) {
                Log.e(TAG, "searchSongs failed", e);
                postError(callback, "搜索失败：" + e.getMessage());
            }
        });
    }

    /** 热门搜索词：GET /getHotkey（data.hotkey[].k） */
    public static void hotkey(Callback<List<String>> callback) {
        run(() -> {
            try {
                JSONObject data = httpGetJsonCached("/getHotkey", "hotkey");
                List<String> keys = new ArrayList<>();
                JSONArray arr = data.optJSONArray("hotkey");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.optJSONObject(i);
                        String k = o == null ? "" : opt(o, "k", "hotkey");
                        if (!k.isEmpty()) {
                            keys.add(k);
                        }
                    }
                }
                post(callback, keys);
            } catch (Exception e) {
                Log.e(TAG, "hotkey failed", e);
                postError(callback, "获取热搜失败：" + e.getMessage());
            }
        });
    }


    // ================= 歌单 =================

    /** 歌单列表：GET /getSongLists（data.list[]） */
    public static void songLists(long categoryId, int sortId, int page, Callback<List<Playlist>> callback) {
        run(() -> {
            try {
                JSONObject data = httpGetJsonCached("/getSongLists?categoryId=" + categoryId
                        + "&sortId=" + sortId + "&page=" + page + "&limit=24",
                "songlists_" + categoryId + "_" + sortId + "_" + page);
                List<Playlist> list = new ArrayList<>();
                JSONArray arr = data.optJSONArray("list");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        Playlist pl = parsePlaylist(arr.optJSONObject(i));
                        if (pl != null) {
                            list.add(pl);
                        }
                    }
                }
                if (list.isEmpty()) {
                    // 上游返回空（多为临时故障）不缓存，避免"无数据"状态被缓存卡住 1 小时
                    ApiCache.remove("songlists_" + categoryId + "_" + sortId + "_" + page);
                }
                post(callback, list);
            } catch (Exception e) {
                Log.e(TAG, "songLists failed", e);
                postError(callback, "获取歌单失败：" + e.getMessage());
            }
        });
    }

    /** 隐私/下架等错误转成用户能看懂的提示 */
    private static String friendlyPlaylistError(String msg) {
        if (msg.toLowerCase().contains("privacy")) {
            return "该歌单已被创建者设为私密，无法查看";
        }
        return "歌单不可用：" + msg;
    }

    /** 歌单 ID 兼容：dissid/disstid/tid（推荐歌单用 tid） */
    private static Playlist parsePlaylist(JSONObject o) {
        if (o == null) {
            return null;
        }
        String id = String.valueOf(o.optLong("dissid", o.optLong("disstid", o.optLong("tid", 0))));
        if ("0".equals(id)) {
            id = opt(o, "dissid", "disstid", "tid");
        }
        if (id.isEmpty()) {
            return null;
        }
        String name = opt(o, "dissname", "dissName", "name", "title");
        if (name.isEmpty()) {
            name = "未知歌单";
        }
        String cover = opt(o, "imgurl", "logo", "coverUrl", "pic", "cover_url_big", "cover_url_medium");
        return new Playlist(id, name, cover, o.optLong("listennum", o.optLong("listen_num", 0)));
    }

    /** 歌单详情：GET /getSongListDetail（顶层 cdlist[0]；部分歌单受隐私限制返回 msg） */
    public static void songListDetail(String disstid, Callback<com.car.audioplayer.data.model.PlaylistDetail> callback) {
        run(() -> {
            try {
                JSONObject data = httpGetJsonCached("/getSongListDetail?disstid=" + disstid, "songlist_" + disstid);
                JSONArray cdlist = data.optJSONArray("cdlist");
                JSONObject cd = (cdlist != null && cdlist.length() > 0) ? cdlist.optJSONObject(0) : null;
                if (cd == null) {
                    String msg = data.optString("msg", "");
                    postError(callback, msg.isEmpty() ? "歌单不存在或已下架" : friendlyPlaylistError(msg));
                    return;
                }
                String name = opt(cd, "dissname", "dissName", "name");
                String cover = opt(cd, "logo", "imgurl", "coverUrl");
                String desc = clean(opt(cd, "desc", "dissdesc"));
                List<OnlineSong> songs = new ArrayList<>();
                Object songlistRaw = cd.opt("songlist");
                JSONArray songlist = null;
                if (songlistRaw instanceof JSONArray) {
                    songlist = (JSONArray) songlistRaw;
                } else if (songlistRaw instanceof String) {
                    try {
                        songlist = new JSONArray((String) songlistRaw);
                    } catch (Exception ignored) {
                    }
                }
                if (songlist != null) {
                    for (int i = 0; i < songlist.length(); i++) {
                        OnlineSong s = parseSongItem(songlist.optJSONObject(i));
                        if (s != null) {
                            songs.add(s);
                        }
                    }
                }
                post(callback, new com.car.audioplayer.data.model.PlaylistDetail(disstid, name, cover, desc, songs));
            } catch (Exception e) {
                Log.e(TAG, "songListDetail failed", e);
                postError(callback, "获取歌单详情失败：" + e.getMessage());
            }
        });
    }

    /** 歌单分类：GET /getSongListCategories（data.categories[].items[{categoryId,categoryName}]） */
    public static void songListCategories(Callback<List<Category>> callback) {
        run(() -> {
            try {
                JSONObject data = httpGetJsonCached("/getSongListCategories", "songlist_categories", CACHE_TTL_LONG_MS);
                List<Category> result = new ArrayList<>();
                collectCategories(data, result);
                post(callback, result);
            } catch (Exception e) {
                Log.e(TAG, "songListCategories failed", e);
                postError(callback, "获取歌单分类失败：" + e.getMessage());
            }
        });
    }
    /** 递归收集分类（items 数组 + group 数组两种结构），自动去重 */
    private static void collectCategories(JSONObject o, List<Category> out) {
        if (o == null) {
            return;
        }
        // 结构一：items[{categoryId, categoryName}]
        JSONArray items = o.optJSONArray("items");
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject it = items.optJSONObject(i);
                if (it == null) {
                    continue;
                }
                String name = opt(it, "categoryName", "name", "groupName");
                long id = it.optLong("categoryId", it.optLong("groupId", 0));
                addCategory(out, name, id);
            }
        }
        // 结构二：group[{groupId, groupName}]（兼容旧版）
        JSONArray groups = o.optJSONArray("group");
        if (groups != null) {
            for (int i = 0; i < groups.length(); i++) {
                JSONObject g = groups.optJSONObject(i);
                if (g == null) {
                    continue;
                }
                addCategory(out, opt(g, "groupName", "name"), g.optLong("groupId", 0));
            }
        }
        JSONArray keys = o.names();
        if (keys == null) {
            return;
        }
        for (int i = 0; i < keys.length(); i++) {
            Object v = o.opt(keys.optString(i));
            if (v instanceof JSONObject) {
                collectCategories((JSONObject) v, out);
            } else if (v instanceof JSONArray) {
                JSONArray arr = (JSONArray) v;
                for (int j = 0; j < arr.length(); j++) {
                    if (arr.opt(j) instanceof JSONObject) {
                        collectCategories(arr.optJSONObject(j), out);
                    }
                }
            }
        }
    }

    /** 还原上游 HTML 转义（如分类名 "R&#38;B" -> "R&B"），无 & 时原样返回 */
    private static String unescapeHtml(String s) {
        if (s == null || s.indexOf('&') < 0) {
            return s;
        }
        return Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString();
    }

    private static void addCategory(List<Category> out, String name, long id) {
        if (name == null || name.isEmpty() || id <= 0) {
            return;
        }
        name = unescapeHtml(name);
        for (Category c : out) {
            if (c.id == id || c.name.equals(name)) {
                return;
            }
        }
        out.add(new Category(name, id));
    }

    // ================= 榜单 =================

    /** 榜单列表：GET /getTopLists（data.topList[]，字段 topTitle/picUrl） */
    public static void topLists(Callback<List<Rank>> callback) {
        run(() -> {
            try {
                JSONObject data = httpGetJsonCached("/getTopLists", "toplists");
                List<Rank> list = new ArrayList<>();
                JSONArray arr = data.optJSONArray("topList");
                if (arr == null) {
                    arr = data.optJSONArray("toplist");
                }
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.optJSONObject(i);
                        if (o == null) {
                            continue;
                        }
                        String topId = String.valueOf(o.optLong("id", o.optLong("topId", 0)));
                        if ("0".equals(topId)) {
                            topId = opt(o, "id", "topId");
                        }
                        if (topId.isEmpty()) {
                            continue;
                        }
                        String name = opt(o, "topTitle", "ListName", "listName", "name", "title");
                        if (name.isEmpty()) {
                            name = "榜单";
                        }
                        String pic = opt(o, "picUrl", "pic", "headPicUrl", "coverUrl");
                        list.add(new Rank(topId, name, pic, clean(opt(o, "intro", "MacDetail"))));
                    }
                }
                post(callback, list);
            } catch (Exception e) {
                Log.e(TAG, "topLists failed", e);
                postError(callback, "获取榜单失败：" + e.getMessage());
            }
        });
    }

    /** 榜内歌曲（快路径）：GET /getRanks?topId（不带 resolveMid，秒回）。
     *  mid 可能为空（官方榜单只给 songId），播放前由 resolveMid 按需解析。 */
    public static void ranks(String topId, Callback<List<OnlineSong>> callback) {
        run(() -> {
            try {
                JSONObject data = httpGetJsonCached("/getRanks?topId=" + topId + "&limit=30", "ranks_" + topId);
                List<OnlineSong> songs = new ArrayList<>();
                JSONArray arr = null;
                JSONObject req1 = data.optJSONObject("req_1");
                if (req1 != null) {
                    JSONObject d1 = req1.optJSONObject("data");
                    if (d1 != null) {
                        arr = findSongArray(d1);
                        if (arr == null) {
                            arr = findSongArray(d1.optJSONObject("data"));
                        }
                    }
                }
                if (arr == null) {
                    arr = findSongArray(data);
                }
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.optJSONObject(i);
                        if (o == null) {
                            continue;
                        }
                        String mid = opt(o, "song_mid", "mid", "songmid", "songMid");
                        long songId = o.optLong("songId", o.optLong("song_id", 0));
                        if (mid.isEmpty() && songId <= 0) {
                            continue; // 既无 mid 也无 songId，无法播放也无法后续解析
                        }
                        String name = opt(o, "title", "songName", "songname", "name");
                        String singer = opt(o, "singerName", "singername", "singer");
                        String singerMid = opt(o, "singerMid", "singermid");
                        String albumMid = opt(o, "albumMid", "albummid");
                        OnlineSong song = new OnlineSong(mid, name, singer, singerMid, "", albumMid, "",
                                o.optInt("interval", o.optInt("songInterval", 0)));
                        song.songId = songId;
                        songs.add(song);
                    }
                }
                post(callback, songs);
            } catch (Exception e) {
                Log.e(TAG, "ranks failed", e);
                postError(callback, "获取榜单歌曲失败：" + e.getMessage());
            }
        });
    }

    /** 用数字 songId 解析歌曲 MID：GET /getSongInfo?songid=xx（songinfo.data.track_info.mid）。
     *  榜单快路径的歌曲播放前调用，解析结果回填 song.mid。 */
    public static void resolveMid(long songId, Callback<String> callback) {
        run(() -> {
            try {
                JSONObject data = httpGetJsonCached("/getSongInfo?songid=" + songId,
                        "songmid_" + songId, CACHE_TTL_LONG_MS);
                JSONObject songinfo = data.optJSONObject("songinfo");
                JSONObject d = songinfo == null ? null : songinfo.optJSONObject("data");
                JSONObject track = d == null ? null : d.optJSONObject("track_info");
                if (track == null && d != null) {
                    track = d.optJSONObject("trackInfo");
                }
                String mid = track == null ? "" : track.optString("mid", "");
                if (mid.isEmpty()) {
                    postError(callback, "歌曲信息解析失败");
                } else {
                    post(callback, mid);
                }
            } catch (Exception e) {
                Log.e(TAG, "resolveMid failed", e);
                postError(callback, "歌曲信息解析失败：" + e.getMessage());
            }
        });
    }
    // ================= 推荐 =================

    /** 推荐（首页聚合）：GET /getRecommend。
     *  实测结构：playlist.data.v_playlist[]（key 为 tid/title/cover_url_*）；
     *  toplist.data.group[].toplist[]（topId/title/headPicUrl）。 */
    public static void recommend(Callback<com.car.audioplayer.data.model.RecommendData> callback) {
        run(() -> {
            try {
                JSONObject data = httpGetJsonCached("/getRecommend", "recommend");
                com.car.audioplayer.data.model.RecommendData result =
                        new com.car.audioplayer.data.model.RecommendData();

                // 推荐歌单：playlist.data.v_playlist（fallback recomPlaylist.data.v_hot）
                JSONArray arr = null;
                JSONObject block = data.optJSONObject("playlist");
                if (block != null) {
                    JSONObject d = block.optJSONObject("data");
                    if (d != null) {
                        arr = d.optJSONArray("v_playlist");
                    }
                }
                if (arr == null) {
                    JSONObject block2 = data.optJSONObject("recomPlaylist");
                    if (block2 != null) {
                        JSONObject d = block2.optJSONObject("data");
                        if (d != null) {
                            arr = d.optJSONArray("v_hot");
                        }
                    }
                }
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        Playlist pl = parsePlaylist(arr.optJSONObject(i));
                        if (pl != null) {
                            result.playlists.add(pl);
                        }
                    }
                }

                // 榜单入口：toplist.data.group[].toplist[]
                JSONObject toplist = data.optJSONObject("toplist");
                if (toplist != null) {
                    JSONObject d = toplist.optJSONObject("data");
                    JSONArray groups = d == null ? null : d.optJSONArray("group");
                    if (groups != null) {
                        for (int gi = 0; gi < groups.length(); gi++) {
                            JSONObject g = groups.optJSONObject(gi);
                            JSONArray tl = g == null ? null : g.optJSONArray("toplist");
                            if (tl == null) {
                                continue;
                            }
                            for (int i = 0; i < tl.length(); i++) {
                                JSONObject o = tl.optJSONObject(i);
                                if (o == null) {
                                    continue;
                                }
                                String topId = String.valueOf(o.optLong("topId", 0));
                                if ("0".equals(topId)) {
                                    topId = opt(o, "topId", "id");
                                }
                                if (topId.isEmpty()) {
                                    continue;
                                }
                                String name = opt(o, "title", "ListName", "name");
                                String pic = opt(o, "headPicUrl", "frontPicUrl", "picUrl", "pic");
                                result.ranks.add(new Rank(topId, name, pic, clean(opt(o, "intro", "titleDetail"))));
                            }
                        }
                    }
                }
                post(callback, result);
            } catch (Exception e) {
                Log.e(TAG, "recommend failed", e);
                postError(callback, "获取推荐失败：" + e.getMessage());
            }
        });
    }

    /** 推荐 banner：GET /getRecommendBanner（focus.data.content[]；封面实测在 pic_info.url，title/sub_title 可能为空；jump_info 仅数字 ID 可跳转） */
    public static void recommendBanner(Callback<List<Banner>> callback) {
        run(() -> {
            try {
                JSONObject data = httpGetJsonCached("/getRecommendBanner", "banner");
                List<Banner> banners = new ArrayList<>();
                JSONObject focus = data.optJSONObject("focus");
                JSONObject d = focus == null ? null : focus.optJSONObject("data");
                JSONArray cards = d == null ? null : d.optJSONArray("content");
                if (cards == null && d != null) {
                    cards = d.optJSONArray("v_card");
                }
                if (cards != null) {
                    for (int i = 0; i < cards.length(); i++) {
                        JSONObject o = cards.optJSONObject(i);
                        if (o == null) {
                            continue;
                        }
                        // 封面：实测上游 cover/imgurl/pic 常为空，真图 URL 在 pic_info.url（urlex1 兜底）
                        String cover = opt(o, "cover", "imgurl", "pic");
                        if (cover.isEmpty()) {
                            JSONObject picInfo = o.optJSONObject("pic_info");
                            if (picInfo != null) {
                                cover = opt(picInfo, "url", "urlex1");
                            }
                        }
                        String title = opt(o, "title", "name");
                        String sub = opt(o, "sub_title", "subTitle", "desc");
                        // 跳转：只认数字 ID（getSongListDetail 仅接受数字 disstid）。
                        // jump_info.id 实测常为 0；其 url 是字母 MID（如 003ryYZm47hg1r），
                        // 中转服务不支持字母 MID 转换 —— 这类 banner 标记为不可跳转（jumpType=0），
                        // 避免点击进入必然报错的详情页；将来中转支持转换后在此启用。
                        String jumpId = "";
                        JSONObject jump = o.optJSONObject("jump_info");
                        if (jump != null) {
                            long jumpNum = jump.optLong("id", 0);
                            if (jumpNum > 0) {
                                jumpId = String.valueOf(jumpNum);
                            } else {
                                String candidate = opt(jump, "mid", "url");
                                if (candidate.matches("\\d+")) {
                                    jumpId = candidate;
                                }
                            }
                        }
                        if (cover.isEmpty()) {
                            continue;
                        }
                        banners.add(new Banner(cover, title, sub, jumpId.isEmpty() ? 0 : 1, jumpId));
                    }
                }
                post(callback, banners);
            } catch (Exception e) {
                Log.e(TAG, "recommendBanner failed", e);
                postError(callback, "获取 banner 失败：" + e.getMessage());
            }
        });
    }
    // ================= 专辑 / 歌手 / 歌词 / 播放地址 =================

    /** 专辑信息：GET /getAlbumInfo?albummid=xx（data 壳剥除后取 name/singername/aDate） */
    public static void albumInfo(String albumMid, Callback<Album> callback) {
        run(() -> {
            try {
                JSONObject data = httpGetJsonCached("/getAlbumInfo?albummid=" + albumMid,
                        "album_info_" + albumMid, CACHE_TTL_LONG_MS);
                Album album = new Album(albumMid,
                        opt(data, "name", "albumname", "albumName"),
                        opt(data, "singername", "singerName", "singer"),
                        opt(data, "singermid", "singerMid"),
                        opt(data, "aDate", "pubTime", "date"));
                post(callback, album);
            } catch (Exception e) {
                Log.e(TAG, "albumInfo failed", e);
                postError(callback, "获取专辑信息失败：" + e.getMessage());
            }
        });
    }

    /** 专辑歌曲：GET /getAlbumSongs?albummid=xx（albumSonglist.data.songList[]） */
    public static void albumSongs(String albumMid, Callback<List<OnlineSong>> callback) {
        run(() -> {
            try {
                JSONObject data = httpGetJsonCached("/getAlbumSongs?albummid=" + albumMid + "&limit=50",
                        "album_songs_" + albumMid, CACHE_TTL_MS);
                List<OnlineSong> songs = new ArrayList<>();
                JSONObject block = data.optJSONObject("albumSonglist");
                JSONArray arr = null;
                if (block != null) {
                    JSONObject d = block.optJSONObject("data");
                    arr = findSongArray(d);
                }
                if (arr == null) {
                    arr = findSongArray(data);
                }
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        OnlineSong s = parseSongItem(arr.optJSONObject(i));
                        if (s != null) {
                            songs.add(s);
                        }
                    }
                }
                post(callback, songs);
            } catch (Exception e) {
                Log.e(TAG, "albumSongs failed", e);
                postError(callback, "获取专辑歌曲失败：" + e.getMessage());
            }
        });
    }

    /** 歌手介绍：GET /getSingerDesc?songmid=xx（新版返回 XML 文本，做剥标签处理） */
    public static void singerDesc(String singerMid, Callback<String> callback) {
        run(() -> {
            try {
                String cacheKey = "singer_desc_" + singerMid;
                String body = ApiCache.get(cacheKey, CACHE_TTL_LONG_MS);
                if (body == null) {
                    // 单飞：同歌手并发只发一次网络，防击穿；无论成败都清理在途标记
                    CompletableFuture<String> descFuture =
                            STRING_FLIGHT.computeIfAbsent("sdf_" + singerMid,
                                    k -> CompletableFuture.supplyAsync(() -> {
                                        try {
                                            String b = httpGet(BASE_URL + "/getSingerDesc?singermid=" + singerMid);
                                            ApiCache.put(cacheKey, b);
                                            return b;
                                        } catch (Exception netErr) {
                                            String stale = ApiCache.getStale(cacheKey);
                                            if (stale != null) {
                                                return stale;
                                            }
                                            throw new java.util.concurrent.CompletionException(netErr);
                                        }
                                    }, EXECUTOR));
                    try {
                        body = descFuture.get();
                    } finally {
                        STRING_FLIGHT.remove("sdf_" + singerMid, descFuture);
                    }
                }
                String desc = "";
                JSONObject root = new JSONObject(body);
                Object resp = root.opt("response");
                if (resp instanceof String) {
                    desc = extractSingerDesc((String) resp);
                } else if (resp instanceof JSONObject) {
                    JSONObject o = (JSONObject) resp;
                    desc = opt(o, "desc", "description", "bio");
                }
                post(callback, desc);
            } catch (Exception e) {
                Log.e(TAG, "singerDesc failed", e);
                postError(callback, "获取歌手介绍失败：" + e.getMessage());
            }
        });
    }

    /** 清洗文本：把 <br> 换成换行、去掉其余 HTML 标签与实体（服务端 desc 常带这些） */
    public static String clean(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return s.replaceAll("(?i)<br\s*/?>", "\n")
                .replaceAll("<[^>]+", "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .trim();
    }

    /** 提取歌手介绍 XML 中 <desc> 的 CDATA 正文。
     *  老的 stripXml 剥标签会把 <![CDATA[正文]]> 整块误当标签删掉（正文里没有 > 时），
     *  只剩 code/subcode/default/id 的裸数字，页面显示成 "0 0 0 4558"；
     *  因此优先精确提取 desc 的 CDATA，取不到再回退剥标签（兼容无 CDATA 的旧形态）。 */
    private static String extractSingerDesc(String xml) {
        if (xml == null || xml.isEmpty()) {
            return "";
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("<desc>\\s*<!\\[CDATA\\[([\\s\\S]*?)\\]\\]>\\s*</desc>")
                .matcher(xml);
        if (m.find()) {
            return m.group(1).trim();
        }
        return stripXml(xml);
    }

    /** 去掉 XML/HTML 标签，压缩空白（仅作 extractSingerDesc 的兜底） */
    private static String stripXml(String xml) {
        if (xml == null) {
            return "";
        }
        return xml.replaceAll("<style[^>]*>[\\s\\S]*?</style>", " ")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /** 歌手热门歌曲：GET /getSingerHotsong?singermid=xx（singer.data.songlist[]） */
    public static void singerHotsong(String singerMid, Callback<List<OnlineSong>> callback) {
        run(() -> {
            try {
                JSONObject data = httpGetJsonCached("/getSingerHotsong?singermid=" + singerMid + "&limit=20",
                        "singer_hot_" + singerMid, CACHE_TTL_MS);
                List<OnlineSong> songs = new ArrayList<>();
                JSONObject singer = data.optJSONObject("singer");
                JSONArray arr = null;
                if (singer != null) {
                    JSONObject d = singer.optJSONObject("data");
                    arr = findSongArray(d);
                }
                if (arr == null) {
                    arr = findSongArray(data);
                }
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        OnlineSong s = parseSongItem(arr.optJSONObject(i));
                        if (s != null) {
                            songs.add(s);
                        }
                    }
                }
                post(callback, songs);
            } catch (Exception e) {
                Log.e(TAG, "singerHotsong failed", e);
                postError(callback, "获取歌手热歌失败：" + e.getMessage());
            }
        });
    }

    /** 歌手专辑：GET /getSingerAlbum?singermid=xx（singer.data.albumList[]） */
    public static void singerAlbum(String singerMid, Callback<List<Album>> callback) {
        run(() -> {
            try {
                JSONObject data = httpGetJsonCached("/getSingerAlbum?singermid=" + singerMid + "&limit=10",
                        "singer_album_" + singerMid, CACHE_TTL_MS);
                List<Album> albums = new ArrayList<>();
                JSONObject singer = data.optJSONObject("singer");
                JSONObject d = singer == null ? null : singer.optJSONObject("data");
                JSONArray arr = d == null ? null : d.optJSONArray("albumList");
                if (arr == null && d != null) {
                    arr = d.optJSONArray("list");
                }
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.optJSONObject(i);
                        if (o == null) {
                            continue;
                        }
                        String mid = opt(o, "albumMid", "albumMID", "albummid", "mid", "albumID");
                        if (!mid.isEmpty()) {
                            albums.add(new Album(mid,
                                    opt(o, "albumName", "name"),
                                    opt(o, "singerName", "singername"),
                                    singerMid,
                                    opt(o, "pubTime", "pub_time", "aDate")));
                        }
                    }
                }
                post(callback, albums);
            } catch (Exception e) {
                Log.e(TAG, "singerAlbum failed", e);
                postError(callback, "获取歌手专辑失败：" + e.getMessage());
            }
        });
    }

    /** 歌词：GET /getLyric?songmid=xx（顶层 lyric 字段为原始 LRC 文本） */
    public static void lyric(String songMid, Callback<String> callback) {
        run(() -> {
            try {
                JSONObject data = httpGetJsonCached("/getLyric?songmid=" + songMid,
                        "lyric_" + songMid, CACHE_TTL_LONG_MS);
                String lrc = data.optString("lyric", "");
                post(callback, lrc);
            } catch (Exception e) {
                Log.e(TAG, "lyric failed", e);
                postError(callback, "获取歌词失败：" + e.getMessage());
            }
        });
    }

    /** 用歌曲 MID 换真实播放地址：GET /getMusicPlay（data.playUrl.<mid>.url，双路径兜底） */
    public static void getPlayUrl(String songmid, Callback<String> callback) {
        run(() -> {
            try {
                JSONObject data = httpGetJson("/getMusicPlay?songmid=" + songmid);
                String url = "";
                JSONObject playUrl = data.optJSONObject("playUrl");
                JSONObject entry = playUrl == null ? null : playUrl.optJSONObject(songmid);
                if (entry != null) {
                    url = entry.optString("url", "");
                }
                if (url.isEmpty()) {
                    // 备用字段：data.data.playUrl 嵌套
                    JSONObject inner = data.optJSONObject("data");
                    JSONObject pu = inner == null ? null : inner.optJSONObject("playUrl");
                    JSONObject en = pu == null ? null : pu.optJSONObject(songmid);
                    if (en != null) {
                        url = en.optString("url", "");
                    }
                }
                if (url == null || url.isEmpty()) {
                    postError(callback, "该歌曲暂无播放链接（可能是 VIP 歌曲）");
                } else {
                    final String finalUrl = url;
                    post(callback, finalUrl);
                }
            } catch (Exception e) {
                Log.e(TAG, "getPlayUrl failed", e);
                postError(callback, "获取播放链接失败：" + e.getMessage());
            }
        });
    }
}