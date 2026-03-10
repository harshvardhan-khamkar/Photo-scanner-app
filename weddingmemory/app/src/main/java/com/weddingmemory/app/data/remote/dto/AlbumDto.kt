package com.weddingmemory.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * FrameDto — JSON representation of a recognisable frame from the API.
 * Mapped to domain [com.weddingmemory.app.domain.model.Frame] via [AlbumMapper].
 */
@JsonClass(generateAdapter = true)
data class FrameDto(
    @Json(name = "id")            val id: String,
    @Json(name = "album_id")      val albumId: String,
    @Json(name = "index")         val index: Int,
    @Json(name = "image_signature") val imageSignature: String,
    @Json(name = "video_url")     val videoUrl: String,
    @Json(name = "thumbnail_url") val thumbnailUrl: String,
    @Json(name = "duration_ms")   val durationMs: Long? = null,
    @Json(name = "start_time_ms") val startTimeMs: Long? = null,
    // Legacy keys still sent by older backend builds.
    @Json(name = "start_time")    val legacyStartTimeSec: Double? = null,
    @Json(name = "startTime")     val legacyStartTimeCamelSec: Double? = null,
    @Json(name = "end_time")      val legacyEndTimeSec: Double? = null,
    @Json(name = "endTime")       val legacyEndTimeCamelSec: Double? = null,
    @Json(name = "metadata")      val metadata: Map<String, String> = emptyMap(),
) {
    fun resolvedStartTimeMs(): Long {
        startTimeMs?.let { return it.coerceAtLeast(0L) }
        val startSeconds = legacyStartTimeSec ?: legacyStartTimeCamelSec ?: 0.0
        return (startSeconds * 1000.0).toLong().coerceAtLeast(0L)
    }

    fun resolvedDurationMs(): Long {
        durationMs?.let { return it.coerceAtLeast(0L) }
        val startSeconds = legacyStartTimeSec ?: legacyStartTimeCamelSec
        val endSeconds = legacyEndTimeSec ?: legacyEndTimeCamelSec
        if (startSeconds == null || endSeconds == null) return 0L
        return ((endSeconds - startSeconds) * 1000.0).toLong().coerceAtLeast(0L)
    }
}

/**
 * AlbumDto — JSON representation of an album from the API.
 * Mapped to domain [com.weddingmemory.app.domain.model.Album] via [AlbumMapper].
 */
@JsonClass(generateAdapter = true)
data class AlbumDto(
    @Json(name = "id")             val id: String,
    @Json(name = "code")           val code: String,
    @Json(name = "name")           val name: String,
    @Json(name = "cover_image_url") val coverImageUrl: String,
    @Json(name = "frames")         val frames: List<FrameDto>,
    @Json(name = "status")         val status: String,
    @Json(name = "created_at")     val createdAt: Long,
    @Json(name = "expires_at")     val expiresAt: Long?,
    @Json(name = "total_frames")   val totalFrames: Int,
)

/**
 * UnlockResponseDto — wraps the server response for a code-unlock request.
 */
@JsonClass(generateAdapter = true)
data class UnlockResponseDto(
    @Json(name = "album")   val album: AlbumDto,
    @Json(name = "message") val message: String = "Success",
)
