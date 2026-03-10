package com.weddingmemory.app.data.repository

import com.weddingmemory.app.data.local.dao.AlbumDao
import com.weddingmemory.app.data.local.mapper.EntityMapper.toDomain
import com.weddingmemory.app.data.local.mapper.EntityMapper.toEntity
import com.weddingmemory.app.data.remote.api.AlbumApi
import com.weddingmemory.app.data.remote.mapper.AlbumMapper.toDomain
import com.weddingmemory.app.domain.exception.DomainException
import com.weddingmemory.app.domain.model.Album
import com.weddingmemory.app.domain.model.AlbumStatus
import com.weddingmemory.app.domain.model.Frame
import com.weddingmemory.app.domain.repository.AlbumRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

/**
 * AlbumRepositoryImpl — production repository using:
 *  - Retrofit ([AlbumApi]) for network operations (unlock)
 *  - Room ([AlbumDao]) for local persistence / offline cache
 *
 * Strategy: network-first for [unlockAlbum], cache-only for all other reads.
 */
class AlbumRepositoryImpl @Inject constructor(
    private val api: AlbumApi,
    private val dao: AlbumDao,
) : AlbumRepository {

    // -------------------------------------------------------------------------
    // Network + Cache
    // -------------------------------------------------------------------------

    override suspend fun unlockAlbum(code: String): Result<Album> {
        return try {
            // 1. Hit the (mock) API
            val response = api.unlockAlbum(code)
            val album = response.album.toDomain()

            // 2. Persist album + frames to Room
            dao.insertAlbum(album.toEntity())
            dao.insertFrames(album.frames.map { it.toEntity() })

            Result.success(album)
        } catch (e: HttpException) {
            Result.failure(
                when (e.code()) {
                    404  -> DomainException.AlbumNotFound(code)
                    401  -> DomainException.InvalidAlbumCode(code)
                    else -> DomainException.ServerError(e.code())
                }
            )
        } catch (e: IOException) {
            // Network unavailable — try local fallback
            val localAlbum = loadFromDb(code)
            if (localAlbum != null) Result.success(localAlbum)
            else Result.failure(DomainException.NetworkUnavailable(e))
        } catch (e: Exception) {
            Result.failure(DomainException.ServerError(500))
        }
    }

    // -------------------------------------------------------------------------
    // Cache reads
    // -------------------------------------------------------------------------

    override suspend fun getAlbum(albumId: String): Result<Album> {
        val entity = dao.getAlbum(albumId)
            ?: return Result.failure(DomainException.AlbumNotFound(albumId))
        val frames = dao.getFrames(albumId).map { it.toDomain() }
        return Result.success(entity.toDomain(frames))
    }

    override fun observeAlbum(albumId: String): Flow<Album?> {
        return dao.observeAlbum(albumId).map { entity ->
            entity?.let {
                val frames = dao.getFrames(albumId).map { f -> f.toDomain() }
                it.toDomain(frames)
            }
        }
    }

    override suspend fun getAllAlbums(): Result<List<Album>> {
        val albums = dao.getAllAlbums().map { entity ->
            val frames = dao.getFrames(entity.id).map { it.toDomain() }
            entity.toDomain(frames)
        }
        return Result.success(albums)
    }

    override suspend fun getFrames(albumId: String): Result<List<Frame>> {
        val frames = dao.getFrames(albumId).map { it.toDomain() }
        return Result.success(frames)
    }

    // -------------------------------------------------------------------------
    // Cache writes
    // -------------------------------------------------------------------------

    override suspend fun saveAlbum(album: Album): Result<Unit> {
        dao.insertAlbum(album.toEntity())
        dao.insertFrames(album.frames.map { it.toEntity() })
        return Result.success(Unit)
    }

    override suspend fun updateAlbumStatus(
        albumId: String,
        status: AlbumStatus,
    ): Result<Unit> {
        val entity = dao.getAlbum(albumId)
            ?: return Result.failure(DomainException.AlbumNotFound(albumId))
        dao.updateAlbum(entity.copy(status = status.name))
        return Result.success(Unit)
    }

    override suspend fun deleteAlbum(albumId: String): Result<Unit> {
        dao.deleteAlbum(albumId)   // cascade removes frames automatically
        return Result.success(Unit)
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** Fallback: look up an album by its unlock code from the local cache. */
    private suspend fun loadFromDb(code: String): Album? {
        return dao.getAllAlbums()
            .firstOrNull { it.code.equals(code, ignoreCase = true) }
            ?.let { entity ->
                val frames = dao.getFrames(entity.id).map { it.toDomain() }
                entity.toDomain(frames)
            }
    }
}
