package com.weddingmemory.app.di

import android.content.Context
import androidx.room.Room
import com.weddingmemory.app.data.local.dao.AlbumDao
import com.weddingmemory.app.data.local.database.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * DatabaseModule — provides the Room [AppDatabase] and its DAO.
 *
 * Installed in [SingletonComponent] so only one DB instance exists
 * for the lifetime of the process.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "wedding_memory.db",
        )
            // TODO: replace with proper Migrations before release
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideAlbumDao(database: AppDatabase): AlbumDao {
        return database.albumDao()
    }
}
