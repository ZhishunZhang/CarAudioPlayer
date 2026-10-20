package com.car.audioplayer.player;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import com.car.audioplayer.R;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 轻量图片加载器（零第三方依赖）：
 * 内存 LruCache + 磁盘缓存 + 降采样解码 + 固定线程池下载 + ImageView tag 防错位 + 失败回退占位图。
 * 工程原则沿用"不引 Glide"。磁盘缓存由 App.onCreate 调 init() 启用，未 init 时自动跳过（容错）。
 */
public class ImageLoader {

    private static final long DISK_CACHE_MAX = 50L * 1024 * 1024;
    private static final long DISK_CACHE_TRIM_TO = 40L * 1024 * 1024;
    private static final int DEFAULT_TARGET = 300;

    private static volatile ImageLoader instance;
    private static volatile File diskCacheDir;

    private final LruCache<String, Bitmap> cache;
    private final ExecutorService pool = Executors.newFixedThreadPool(6);
    private final Handler main = new Handler(Looper.getMainLooper());

    private ImageLoader() {
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSize = maxMemory / 8;
        cache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount() / 1024;
            }
        };
    }

    public static ImageLoader get() {
        if (instance == null) {
            synchronized (ImageLoader.class) {
                if (instance == null) {
                    instance = new ImageLoader();
                }
            }
        }
        return instance;
    }

    /** 由 App.onCreate 调用：启用磁盘缓存目录。未调用时磁盘缓存跳过，只走内存缓存 + 网络 */
    public static void init(android.content.Context appContext) {
        File dir = new File(appContext.getCacheDir(), "image_cache");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        diskCacheDir = dir;
    }

    /** 加载图片到 ImageView（URL 为空时直接显示占位图） */
    public void load(ImageView imageView, String url) {
        // 复用视图可能残留上一次未完成的淡入动画，先取消并复位，避免占位图半透明
        imageView.animate().cancel();
        imageView.setAlpha(1f);
        imageView.setImageResource(R.drawable.bg_cover_placeholder);
        if (url == null || url.isEmpty()) {
            return;
        }
        imageView.setTag(url);
        Bitmap cached = cache.get(url);
        if (cached != null) {
            imageView.setImageBitmap(cached);
            return;
        }
        // 捕获目标尺寸：列表行尺寸基本固定，宽高为 0（尚未布局）时用默认值
        int targetW = imageView.getWidth();
        int targetH = imageView.getHeight();
        int target = Math.max(targetW, targetH);
        if (target <= 0) {
            target = DEFAULT_TARGET;
        }
        final int targetSize = target;
        pool.execute(() -> {
            try {
                byte[] data = readFromDiskOrNetwork(url);
                if (data == null) {
                    return;
                }
                Bitmap bitmap = decodeSampled(data, targetSize);
                if (bitmap != null) {
                    cache.put(url, bitmap);
                    main.post(() -> {
                        if (url.equals(imageView.getTag())) {
                            // 渐进加载：真图就位后从占位图淡入（200ms），明示加载进度而非生硬闪切
                            imageView.setImageBitmap(bitmap);
                            imageView.setAlpha(0f);
                            imageView.animate().alpha(1f).setDuration(200).start();
                        }
                    });
                }
            } catch (Exception ignored) {
                // 加载失败保持占位图
            }
        });
    }

    /** 释放内存缓存（低内存回调时用） */
    public void clearMemory() {
        cache.evictAll();
    }

    /** 方形位图裁成圆形（头像显示用）：居中裁剪 + 圆形遮罩，源图不变 */
    public static Bitmap toCircle(Bitmap src) {
        if (src == null) {
            return null;
        }
        int edge = Math.min(src.getWidth(), src.getHeight());
        Bitmap out = Bitmap.createBitmap(edge, edge, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(out);
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        android.graphics.BitmapShader shader = new android.graphics.BitmapShader(
                src, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP);
        // 居中裁剪矩阵：短边铺满输出，长边居中
        float scale = edge / (float) Math.min(src.getWidth(), src.getHeight());
        android.graphics.Matrix matrix = new android.graphics.Matrix();
        matrix.setScale(scale, scale);
        matrix.postTranslate((edge - src.getWidth() * scale) / 2f,
                (edge - src.getHeight() * scale) / 2f);
        shader.setLocalMatrix(matrix);
        paint.setShader(shader);
        canvas.drawCircle(edge / 2f, edge / 2f, edge / 2f, paint);
        return out;
    }

    /** 先查磁盘缓存（文件名为 url 的 MD5），未命中再走网络并写入磁盘 */
    private static byte[] readFromDiskOrNetwork(String url) throws Exception {
        File dir = diskCacheDir;
        if (dir != null) {
            File file = new File(dir, md5Hex(url));
            if (file.exists() && file.length() > 0) {
                return readFile(file);
            }
        }
        byte[] data = download(url);
        if (data != null && dir != null) {
            File file = new File(dir, md5Hex(url));
            FileOutputStream out = new FileOutputStream(file);
            try {
                out.write(data);
            } finally {
                out.close();
            }
            trimDiskCache(dir);
        }
        return data;
    }

    /** 降采样解码：先取原始尺寸，inSampleSize 取 2 的幂，保证解码后不小于目标尺寸 */
    private static Bitmap decodeSampled(byte[] data, int target) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        int w = bounds.outWidth;
        int h = bounds.outHeight;
        int sample = 1;
        while (w / (sample * 2) >= target && h / (sample * 2) >= target) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        return BitmapFactory.decodeByteArray(data, 0, data.length, options);
    }

    /** 磁盘缓存超过 50MB 时，按 lastModified 从旧到新删到 40MB 以下（在下载线程执行） */
    private static void trimDiskCache(File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        long total = 0;
        for (File f : files) {
            total += f.length();
        }
        if (total <= DISK_CACHE_MAX) {
            return;
        }
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return Long.compare(a.lastModified(), b.lastModified());
            }
        });
        for (File f : files) {
            if (total <= DISK_CACHE_TRIM_TO) {
                break;
            }
            long len = f.length();
            if (f.delete()) {
                total -= len;
            }
        }
    }

    private static byte[] readFile(File file) throws Exception {
        FileInputStream in = new FileInputStream(file);
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, n);
            }
            return buffer.toByteArray();
        } finally {
            in.close();
        }
    }

    private static byte[] download(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setRequestMethod("GET");
        try {
            int code = conn.getResponseCode();
            if (code != 200) {
                return null;
            }
            InputStream in = conn.getInputStream();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, n);
            }
            in.close();
            return buffer.toByteArray();
        } finally {
            conn.disconnect();
        }
    }

    private static String md5Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}