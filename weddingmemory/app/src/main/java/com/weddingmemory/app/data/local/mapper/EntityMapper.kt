package com.weddingmemory.app.data.local.mapper

import com.weddingmemory.app.data.local.entity.AlbumEntity
import com.weddingmemory.app.data.local.entity.FrameEntity
import com.weddingmemory.app.domain.model.Album
import com.weddingmemory.app.domain.model.AlbumStatus
import com.weddingmemory.app.domain.model.Frame

/**
 * EntityMapper — pure Kotlin functions that translate Room entities ↔ domain models.
 *
 * Data layer only. Domain models have no knowledge of Room entities.
 */
object EntityMapper {

    // -------------------------------------------------------------------------
    // Entity → Domain
    // -------------------------------------------------------------------------

    fun AlbumEntity.toDomain(frames: List<Frame>): Album = Album(
        id            = id,
        code          = code,
        name          = name,
        coverImageUrl = coverImageUrl,
        frames        = frames,
        status        = status.toAlbumStatus(),
        createdAt     = createdAt,
        expiresAt     = expiresAt,
        totalFrames   = totalFrames,
    )

    fun FrameEntity.toDomain(): Frame = Frame(
        id             = id,
        albumId        = albumId,
        index          = index,
        imageSignature = imageSignature,
        videoUrl       = videoUrl,
        thumbnailUrl   = thumbnailUrl,
        durationMs     = durationMs,
        startTimeMs    = startTimeMs,
        metadata       = emptyMap(), // metadata not persisted in DB (save space)
    )

    // -------------------------------------------------------------------------
    // Domain → Entity
    // -------------------------------------------------------------------------

    fun Album.toEntity(): AlbumEntity = AlbumEntity(
        id            = id,
        code          = code,
        name          = name,
        coverImageUrl = coverImageUrl,
        status        = status.name,
        createdAt     = createdAt,
        expiresAt     = expiresAt,
        totalFrames   = totalFrames,
    )

    fun Frame.toEntity(): FrameEntity = FrameEntity(
        id             = id,
        albumId        = albumId,
        index          = index,
        imageSignature = imageSignature,
        videoUrl       = videoUrl,
        thumbnailUrl   = thumbnailUrl,
        durationMs     = durationMs,
        startTimeMs    = startTimeMs,
    )

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun String.toAlbumStatus(): AlbumStatus =
        AlbumStatus.entries.firstOrNull { it.name.equals(this, ignoreCase = true) }
            ?: AlbumStatus.LOCKED
}
