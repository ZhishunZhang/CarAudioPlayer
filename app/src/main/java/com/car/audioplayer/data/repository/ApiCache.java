package com.car.audioplayer.data.repository;

import com.car.audioplayer.App;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 接口数据缓存（内存 + 磁盘两级）：
 * 命中未过期 → 秒开不发请求；过期/无缓存 → 走网络并回填；网络失败 → 回退过期旧数据。
 * 榜单/歌单等低频变更数据用，播放地址等时效数据不用。
 */
public class ApiCache {

    private static class Entry {
        final String json;
        final long time;

        Entry(String json, long time) {
            this.json = json;
            this.time = time;
        }
    }

    private static final Map<String, Entry> MEMORY = new ConcurrentHashMap<>();
    private static volatile File dir;

    private static File dir() {
        if (dir == null) {
            synchronized (ApiCache.class) {
                if (dir == null) {
                    dir = new File(App.getAppContext().getCacheDir(), "api_cache");
                    dir.mkdirs();
                }
            }
        }
        return dir;
    }

    /** 缓存文件路径：key 里的 / 替换成 _（避免被当成目录分隔符） */
    private static File file(String key) {
        return new File(dir(), key.replace('/', '_'));
    }

    /**
     * 取未过期缓存（内存 → 磁盘），无则 null。
     * 读取顺序：内存 ConcurrentHashMap 命中直接返回；未命中读磁盘文件
     * （用文件 lastModified 当写入时间比对 TTL），读到后回填内存加速下次。
     */
    public static String get(String key, long ttlMs) {
        long now = System.currentTimeMillis();
        Entry e = MEMORY.get(key);
        if (e != null && now - e.time < ttlMs) {
            return e.json;
        }
        File f = file(key);
        if (f.exists() && now - f.lastModified() < ttlMs) {
            String json = read(f);
            if (json != null) {
                MEMORY.put(key, new Entry(json, f.lastModified()));
                return json;
            }
        }
        return null;
    }

    /**
     * 取过期缓存（网络失败时兜底显示旧数据），无则 null。
     * 不校验 TTL——它就是"数据过但总比空页面强"的兜底路径。
     */
    public static String getStale(String key) {
        Entry e = MEMORY.get(key);
        if (e != null) {
            return e.json;
        }
        File f = file(key);
        if (f.exists()) {
            String json = read(f);
            if (json != null) {
                MEMORY.put(key, new Entry(json, f.lastModified()));
            }
            return json;
        }
        return null;
    }

    /**
     * 写缓存（内存 + 磁盘双写）。
     * 磁盘写失败只忽略不影响内存——页面本次还能用，下次走网络重写。
     */
    public static void put(String key, String json) {
        long now = System.currentTimeMillis();
        MEMORY.put(key, new Entry(json, now));
        try {
            FileOutputStream out = new FileOutputStream(file(key));
            out.write(json.getBytes(StandardCharsets.UTF_8));
            out.close();
        } catch (Exception ignored) {
            // 磁盘写失败不影响内存缓存
        }
    }

    /** 删除缓存（内存 + 磁盘）：用于上游返回异常空数据时不留坏缓存 */
    public static void remove(String key) {
        MEMORY.remove(key);
        File f = file(key);
        if (f.exists()) {
            f.delete();
        }
    }

    /** 读文件字节流并转 UTF-8 字符串（异常返回 null，交由上层走网络） */
    private static String read(File f) {
        try {
            FileInputStream in = new FileInputStream(f);
            byte[] bytes = new byte[(int) f.length()];
            int read = in.read(bytes);
            in.close();
            return read > 0 ? new String(bytes, 0, read, StandardCharsets.UTF_8) : null;
        } catch (Exception e) {
            return null;
        }
    }
}