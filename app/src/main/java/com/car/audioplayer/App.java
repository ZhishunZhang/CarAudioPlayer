package com.car.audioplayer;

import android.app.Application;
import android.content.Context;
import android.os.StrictMode;

import com.car.audioplayer.player.ImageLoader;

/**
 * 全局 Application：提供全局 Context 访问入口，并初始化图片磁盘缓存。
 */
public class App extends Application {

    private static App instance;

    @Override
    public void onCreate() {
        super.onCreate();
        enableStrictModeIfDebug();
        instance = this;
        ImageLoader.init(this);
    }

    /**
     * 开发期严格模式：仅 debug 开启，主线程磁盘/网络违规 + 内存泄漏在 logcat 标红。
     * penaltyLog 只打印不打断；release 不走此路径，零影响。
     */
    private void enableStrictModeIfDebug() {
        boolean isDebug = (getApplicationInfo().flags
                & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        if (!isDebug) {
            return;
        }
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()

                .penaltyLog()
                .build());
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .detectActivityLeaks()
                .penaltyLog()
                .build());
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
                || level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            ImageLoader.get().clearMemory();
        }
    }

    public static App get() {
        return instance;
    }

    public static Context getAppContext() {
        return instance.getApplicationContext();
    }
}