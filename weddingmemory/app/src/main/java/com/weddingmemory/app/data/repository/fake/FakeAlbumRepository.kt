package com.weddingmemory.app.data.repository.fake

import com.weddingmemory.app.domain.model.Album
import com.weddingmemory.app.domain.model.AlbumStatus
import com.weddingmemory.app.domain.model.Frame
import com.weddingmemory.app.domain.repository.AlbumRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/**
 * FakeAlbumRepository — stub implementation used until the real
 * Retrofit + Room data layer is built.
 *
 * Behaviour:
 *  - Any non-blank code → succeeds after a 1 second simulated network delay.
 *  - Code "FAIL" → returns a failure so error-state UI can be tested.
 *
 * Remove this class when [AlbumRepositoryImpl] is wired up in Step N.
 */
class FakeAlbumRepository @Inject constructor() : AlbumRepository {

    // Simulated in-memory store
    private val store = mutableMapOf<String, Album>()

    override suspend fun unlockAlbum(code: String): Result<Album> {
        delay(1_200) // simulate network latency

        if (code == "FAIL") {
            return Result.failure(
                Exception("Invalid album code '$code'.")
            )
        }

        val album = Album(
            id = "fake-album-${code.lowercase()}",
            code = code,
            name = "Demo Wedding — $code",
            coverImageUrl = "https://example.com/covers/$code.jpg",
            frames = emptyList(),
            status = AlbumStatus.READY,
            createdAt = System.currentTimeMillis(),
            expiresAt = null,
            totalFrames = 0,
        )
        store[album.id] = album
        return Result.success(album)
    }

    override suspend fun getAlbum(albumId: String): Result<Album> {
        val album = store[albumId]
            ?: return Result.failure(Exception("Album '$albumId' not found."))
        return Result.success(album)
    }

    override fun observeAlbum(albumId: String): Flow<Album?> =
        flowOf(store[albumId])

    override suspend fun getAllAlbums(): Result<List<Album>> =
        Result.success(store.values.toList())

    override suspend fun getFrames(albumId: String): Result<List<Frame>> =
        Result.success(emptyList())

    override suspend fun saveAlbum(album: Album): Result<Unit> {
        store[album.id] = album
        return Result.success(Unit)
    }

    override suspend fun updateAlbumStatus(
        albumId: String,
        status: AlbumStatus
    ): Result<Unit> {
        store[albumId] = store[albumId]?.copy(status = status)
            ?: return Result.failure(Exception("Album '$albumId' not found."))
        return Result.success(Unit)
    }

    override suspend fun deleteAlbum(albumId: String): Result<Unit> {
        store.remove(albumId)
        return Result.success(Unit)
    }
}
