package com.weddingmemory.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.weddingmemory.app.data.local.entity.AlbumEntity
import com.weddingmemory.app.data.local.entity.FrameEntity
import kotlinx.coroutines.flow.Flow

/**
 * AlbumDao — Room DAO for album and frame persistence.
 *
 * All suspend functions run on whichever coroutine dispatcher the caller provides.
 * [observeAlbum] returns a [Flow] that emits whenever the row changes.
 */
@Dao
interface AlbumDao {

    // -------------------------------------------------------------------------
    // Writes
    // -------------------------------------------------------------------------

    /**
     * Insert or replace an album. REPLACE strategy ensures an unlock with
     * the same code always refreshes the local record.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbum(album: AlbumEntity)

    /**
     * Insert or replace a batch of frames. Existing frames for this album
     * are replaced to stay in sync with the server manifest.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFrames(frames: List<FrameEntity>)

    /** Update an existing album row (used to change status). */
    @Update
    suspend fun updateAlbum(album: AlbumEntity)

    /** Hard-delete an album; cascade removes its frames automatically. */
    @Query("DELETE FROM albums WHERE id = :albumId")
    suspend fun deleteAlbum(albumId: String)

    // -------------------------------------------------------------------------
    // Reads
    // -------------------------------------------------------------------------

    /** Single shot — returns null if no row exists. */
    @Query("SELECT * FROM albums WHERE id = :albumId")
    suspend fun getAlbum(albumId: String): AlbumEntity?

    /** Reactive — emits a new value whenever the albums row changes. */
    @Query("SELECT * FROM albums WHERE id = :albumId")
    fun observeAlbum(albumId: String): Flow<AlbumEntity?>

    /** Returns all albums ordered by creation time, newest first. */
    @Query("SELECT * FROM albums ORDER BY createdAt DESC")
    suspend fun getAllAlbums(): List<AlbumEntity>

    /** Returns all frames for a given album, ordered by frame index. */
    @Query("SELECT * FROM frames WHERE albumId = :albumId ORDER BY `index` ASC")
    suspend fun getFrames(albumId: String): List<FrameEntity>
}
