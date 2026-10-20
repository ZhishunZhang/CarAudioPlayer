package com.car.audioplayer.data.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.car.audioplayer.data.local.dao.FavoritesDao;
import com.car.audioplayer.data.local.entity.FavoritePlaylist;
import com.car.audioplayer.data.local.entity.FavoriteSong;

/**
 * Room 数据库（单例）。
 * v3：移除阶段一铃声曲库 song 表（旧曲库页面已删除）。
 * 不允许主线程查询：所有同步读写必须走后台线程（FavoritesRepository 内置线程池）。
 */
// exportSchema=false：不导出版本 schema JSON；version 需与已登记迁移对齐
@Database(entities = {FavoriteSong.class, FavoritePlaylist.class},
        version = 3, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    /** v2→v3：删除废弃的铃声曲库表；收藏表保持原样（用户数据不丢） */
    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("DROP TABLE IF EXISTS `song`");
        }
    };

    /** 数据库单例：volatile 保证多线程间的可见性 */
    private static volatile AppDatabase instance;

    /** Room 编译期生成实现（SQL 构建时校验，拼错直接编译失败） */
    public abstract FavoritesDao favoritesDao();

    /**
     * 双重检查锁获取单例。
     * 持有 applicationContext，防止外部传入的短生命周期 Context 被单例长期引用而泄漏；
     * fallbackToDestructiveMigration：遇到未登记迁移的版本升级时直接重建库（数据全丢），
     * 以"丢数据"换"不崩溃"，正常升级路径由 MIGRATION_2_3 这类显式迁移保数据。
     */
    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "car_audio.db")
                            .addMigrations(MIGRATION_2_3)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return instance;
    }
}