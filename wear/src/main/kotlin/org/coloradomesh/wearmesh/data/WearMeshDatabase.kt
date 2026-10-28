package org.coloradomesh.wearmesh.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The watch's message store. The schema of each version is exported to `wear/schemas/`, so a
 * later version can be checked against it and given a migration.
 */
@Database(
    entities = [RadioRow::class, ChannelRow::class, ContactRow::class, MessageRow::class],
    version = 2,
    exportSchema = true,
)
abstract class WearMeshDatabase : RoomDatabase() {
    abstract fun dao(): StoreDao

    companion object {
        private const val FILE = "wearmesh.db"

        @Volatile
        private var instance: WearMeshDatabase? = null

        fun get(context: Context): WearMeshDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, WearMeshDatabase::class.java, FILE)
                // Nothing is released yet, so a schema change may start the store afresh; the
                // radio's queue and the next full read refill it. Replace with migrations
                // before the first release.
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}
