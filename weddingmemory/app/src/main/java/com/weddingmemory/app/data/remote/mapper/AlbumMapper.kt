package com.weddingmemory.app.data.remote.mapper

import com.weddingmemory.app.data.remote.dto.AlbumDto
import com.weddingmemory.app.data.remote.dto.FrameDto
import com.weddingmemory.app.domain.model.Album
import com.weddingmemory.app.domain.model.AlbumStatus
import com.weddingmemory.app.domain.model.Frame

/**
 * AlbumMapper — pure Kotlin functions that translate API DTOs to domain models.
 *
 * Lives in the data layer; the domain layer has zero knowledge of DTOs.
 * No framework dependencies — fully unit-testable.
 */
object AlbumMapper {

    fun AlbumDto.toDomain(): Album = Album(
        id           = id,
        code         = code,
        name         = name,
        coverImageUrl = coverImageUrl,
        frames       = frames.map { it.toDomain() },
        status       = status.toAlbumStatus(),
        createdAt    = createdAt,
        expiresAt    = expiresAt,
        totalFrames  = totalFrames,
    )

    fun FrameDto.toDomain(): Frame = Frame(
        id             = id,
        albumId        = albumId,
        index          = index,
        imageSignature = imageSignature,
        videoUrl       = videoUrl,
        thumbnailUrl   = thumbnailUrl,
        durationMs     = resolvedDurationMs(),
        startTimeMs    = resolvedStartTimeMs(),
        metadata       = metadata,
    )

    /**
     * Safely maps a status string from the API to [AlbumStatus].
     * Defaults to [AlbumStatus.LOCKED] if the server sends an unknown value,
     * so the app never crashes on new status strings.
     */
    private fun String.toAlbumStatus(): AlbumStatus =
        AlbumStatus.entries.firstOrNull {
            it.name.equals(this, ignoreCase = true)
        } ?: AlbumStatus.LOCKED
}
