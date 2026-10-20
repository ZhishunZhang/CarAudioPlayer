package com.car.audioplayer.data.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * 本地用户资料存储（无账号体系的"我的"页数据源）：
 * 昵称存 SharedPreferences；头像从相册选图后拷贝到 filesDir/avatar.jpg。
 */
public class UserProfileStore {

    private static final String PREFS = "user_profile";
    private static final String KEY_NICKNAME = "nickname";
    private static final String AVATAR_FILE = "avatar.jpg";

    private static volatile UserProfileStore instance;

    private final SharedPreferences prefs;
    private final File avatarFile;

    private UserProfileStore(Context context) {
        Context app = context.getApplicationContext();
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        avatarFile = new File(app.getFilesDir(), AVATAR_FILE);
    }

    public static UserProfileStore get(Context context) {
        if (instance == null) {
            synchronized (UserProfileStore.class) {
                if (instance == null) {
                    instance = new UserProfileStore(context);
                }
            }
        }
        return instance;
    }

    /** 读昵称（空串表示未设置，UI 显示"点击设置昵称"占位） */
    public String getNickname() {
        String stored = prefs.getString(KEY_NICKNAME, "");
        // 兼容旧版脏数据：占位文字曾被拼进保存值，整条视为无效并清空
        if (stored.startsWith("点击设置昵称")) {
            setNickname("");
            return "";
        }
        return stored;
    }

    /** 写昵称（apply 异步落盘，不阻塞主线程） */
    public void setNickname(String nickname) {
        prefs.edit().putString(KEY_NICKNAME, nickname).apply();
    }

    /** 保存头像（相册选出的 Bitmap，压缩为 JPEG 到私有目录），返回保存后的文件 */
    public File saveAvatar(Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }
        FileOutputStream out = null;
        try {
            Bitmap scaled = scaleToSquare(bitmap, 512);
            out = new FileOutputStream(avatarFile);
            scaled.compress(Bitmap.CompressFormat.JPEG, 90, out);
            out.flush();
            return avatarFile;
        } catch (IOException e) {
            return null;
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /** 头像文件（供图片选择回调直接判断存在性） */
    public File getAvatarFile() {
        return avatarFile;
    }

    /** 读取头像 Bitmap（不存在返回 null，UI 用默认图标） */
    public Bitmap loadAvatar() {
        if (!avatarFile.exists()) {
            return null;
        }
        return BitmapFactory.decodeFile(avatarFile.getAbsolutePath());
    }

    /** 居中裁方形再缩放到 size（512px 控制文件体积，头像不需要原图分辨率） */
    private static Bitmap scaleToSquare(Bitmap src, int size) {
        int w = src.getWidth();
        int h = src.getHeight();
        int edge = Math.min(w, h);
        int x = (w - edge) / 2;
        int y = (h - edge) / 2;
        Bitmap cropped = Bitmap.createBitmap(src, x, y, edge, edge);
        if (edge == size) {
            return cropped;
        }
        return Bitmap.createScaledBitmap(cropped, size, size, true);
    }
}