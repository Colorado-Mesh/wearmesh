package org.coloradomesh.wearmesh.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The watch's message store. The schema of each version is exported to `wear/schemas/`, so a
 * later version can be checked against it and given a migration.
 */
@Database(
    entities = [RadioRow::class, ChannelRow::class, ContactRow::class, MessageRow::class],
    version = 3,
    exportSchema = true,
)
abstract class WearMeshDatabase : RoomDatabase() {
    abstract fun dao(): StoreDao

    companion object {
        private const val FILE = "wearmesh.db"

        /**
         * Version 3 (M3) adds sent messages: two columns, so the stored history is kept. The
         * statements match what Room expects of [MessageRow]; [MigrationTest] checks them.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `outgoing` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `status` TEXT")
            }
        }

        @Volatile
        private var instance: WearMeshDatabase? = null

        fun get(context: Context): WearMeshDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, WearMeshDatabase::class.java, FILE)
                .addMigrations(MIGRATION_2_3)
                // Only for a version with no migration (1, from before per-radio history): the
                // store starts afresh, and the radio's queue and the next full read refill it.
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}
