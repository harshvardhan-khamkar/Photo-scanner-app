package com.weddingmemory.app.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.weddingmemory.app.data.local.dao.AlbumDao
import com.weddingmemory.app.data.local.entity.AlbumEntity
import com.weddingmemory.app.data.local.entity.FrameEntity

/**
 * AppDatabase — single Room database instance for the app.
 *
 * Bump [version] whenever the schema changes.
 * Add a [androidx.room.migration.Migration] for each version bump to
 * preserve user data. Set exportSchema = true in production builds.
 */
@Database(
    entities = [AlbumEntity::class, FrameEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun albumDao(): AlbumDao
}
