package com.weddingmemory.app.domain.model

/**
 * Album — root aggregate for a wedding memory collection.
 *
 * Immutable value object. All mutations produce a new copy.
 *
 * @param id            Globally unique album identifier (server-assigned UUID).
 * @param code          Short unlock code that guests enter (e.g. "SMITH2024").
 * @param name          Human-readable album title (e.g. "Sarah & James Wedding").
 * @param coverImageUrl URL to the album cover thumbnail (optional at unlock time).
 * @param frames        Ordered list of all recognisable frames in this album.
 * @param status        Lifecycle state of the album on this device.
 * @param createdAt     Unix epoch millis when the album was created on the server.
 * @param expiresAt     Unix epoch millis after which the album can no longer be scanned.
 *                      Null = never expires.
 * @param totalFrames   Total number of frames declared by the server.
 *                      Used to detect partial local caches.
 */
data class Album(
    val id: String,
    val code: String,
    val name: String,
    val coverImageUrl: String,
    val frames: List<Frame>,
    val status: AlbumStatus,
    val createdAt: Long,
    val expiresAt: Long?,
    val totalFrames: Int,
) {
    /** True when all frames have been downloaded and are ready for recognition. */
    val isFullyCached: Boolean
        get() = frames.size == totalFrames && status == AlbumStatus.READY

    /** True when the album has a server-set expiry and it has already passed. */
    fun isExpired(nowMillis: Long): Boolean =
        expiresAt != null && nowMillis > expiresAt
}

/**
 * AlbumStatus — tracks the lifecycle of an album on this device.
 *
 * Transitions:
 *   LOCKED → UNLOCKING → INITIALIZING → READY
 *                      ↘ FAILED
 *                                     ↘ EXPIRED
 */
enum class AlbumStatus {
    /** Code not verified with server yet. */
    LOCKED,

    /** Unlock request sent, awaiting server confirmation. */
    UNLOCKING,

    /** Frames / signatures being downloaded and indexed. */
    INITIALIZING,

    /** All frames loaded and recognition engine is ready. */
    READY,

    /** Unlock or initialisation failed.*/
    FAILED,

    /** Album has expired — no new scans allowed. */
    EXPIRED,
}
