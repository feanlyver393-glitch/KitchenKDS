package com.kitchenkds.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kitchenkds.data.local.entity.OrderEntity
import com.kitchenkds.data.local.entity.OrderItemEntity

/**
 * 本地缓存库。
 *
 * V4：版本 1 → 2，order_items 增加 isCancelled 列。
 *
 * 不开 fallbackToDestructiveMigration —— 这是生产数据，
 * 宁可启动失败也不能静默清库。
 */
@Database(
    entities = [OrderEntity::class, OrderItemEntity::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun orderDao(): OrderDao

    companion object {
        private const val DB_NAME = "kitchenkds.db"

        /** V4：POS 取消单功能需要的列 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE order_items ADD COLUMN isCancelled INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_order_items_isCancelled ON order_items(isCancelled)"
                )
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
