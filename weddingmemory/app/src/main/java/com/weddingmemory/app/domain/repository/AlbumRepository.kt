package com.weddingmemory.app.domain.repository

import com.weddingmemory.app.domain.model.Album
import com.weddingmemory.app.domain.model.AlbumStatus
import com.weddingmemory.app.domain.model.Frame
import kotlinx.coroutines.flow.Flow

/**
 * AlbumRepository — domain contract for all album data operations.
 *
 * Implementations live in the data layer (e.g. [AlbumRepositoryImpl]).
 * This interface has zero knowledge of Room, Retrofit, or any framework.
 *
 * Error handling: every suspend function returns [Result] so callers
 * can use idiomatic Kotlin `getOrElse`, `fold`, `onFailure` chains
 * without try/catch at the use-case boundary.
 */
interface AlbumRepository {

    // -------------------------------------------------------------------------
    // Unlock
    // -------------------------------------------------------------------------

    /**
     * Verify [code] with the server and return the corresponding [Album].
     *
     * - On success: the album is persisted locally and returned with
     *   status [AlbumStatus.INITIALIZING].
     * - On failure: returns [Result.failure] with a typed [DomainException].
     *
     * This is a network call. Callers must handle offline scenarios.
     */
    suspend fun unlockAlbum(code: String): Result<Album>

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    /**
     * Load a previously unlocked album from the local store.
     * Returns [Result.failure] with [DomainException.AlbumNotFound] if absent.
     */
    suspend fun getAlbum(albumId: String): Result<Album>

    /**
     * Observe an album as a cold [Flow].
     * Emits a new value whenever the locally cached album changes
     * (e.g. status transitions, frame additions).
     * Emits null if the album is not found.
     */
    fun observeAlbum(albumId: String): Flow<Album?>

    /**
     * Return all locally cached albums, ordered by [Album.createdAt] descending.
     */
    suspend fun getAllAlbums(): Result<List<Album>>

    /**
     * Return all [Frame]s for the given album from the local store.
     * Result is empty if the album has not been initialised yet.
     */
    suspend fun getFrames(albumId: String): Result<List<Frame>>

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    /**
     * Persist (insert or replace) an album and all its nested frames.
     * Used by [com.weddingmemory.app.domain.usecase.InitializeAlbumUseCase]
     * after the server delivers the full frame manifest.
     */
    suspend fun saveAlbum(album: Album): Result<Unit>

    /**
     * Update the [AlbumStatus] of an existing album without touching frames.
     */
    suspend fun updateAlbumStatus(albumId: String, status: AlbumStatus): Result<Unit>

    /**
     * Remove an album and all associated frames from the local store.
     * Does not affect server-side data.
     */
    suspend fun deleteAlbum(albumId: String): Result<Unit>
}
