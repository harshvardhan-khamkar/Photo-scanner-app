package com.weddingmemory.app.domain.exception

/**
 * DomainException — sealed hierarchy of all domain-layer failures.
 *
 * Using a sealed class instead of raw Exception subclasses forces every
 * call-site to handle the exact failure contract — no silent swallowing
 * of unknown exceptions.
 *
 * Data-layer adapters translate framework exceptions (IOException,
 * HttpException, SQLiteException, etc.) into these typed failures
 * before they cross into the domain.
 */
sealed class DomainException(
    override val message: String,
    override val cause: Throwable? = null,
) : Exception(message, cause) {

    // -------------------------------------------------------------------------
    // Album exceptions
    // -------------------------------------------------------------------------

    /** The supplied album code was rejected by the server. */
    data class InvalidAlbumCode(val code: String) : DomainException(
        message = "Album code '$code' is invalid or not found."
    )

    /** The album exists on the server but has passed its expiry date. */
    data class AlbumExpired(val albumId: String) : DomainException(
        message = "Album '$albumId' has expired and can no longer be scanned."
    )

    /** A previously unlocked album was not found in the local store. */
    data class AlbumNotFound(val albumId: String) : DomainException(
        message = "Album '$albumId' not found in local store."
    )

    /** Album frames could not be downloaded or are incomplete. */
    data class AlbumInitializationFailed(
        val albumId: String,
        override val cause: Throwable? = null,
    ) : DomainException(
        message = "Failed to initialise album '$albumId'.",
        cause = cause,
    )

    // -------------------------------------------------------------------------
    // Recognition exceptions
    // -------------------------------------------------------------------------

    /** Recognition engine was not loaded before a scan was attempted. */
    data class EngineNotReady(val albumId: String) : DomainException(
        message = "Recognition engine not ready for album '$albumId'. Call loadSignatures() first."
    )

    /** Recognition engine failed during analysis (not a "no match" result). */
    data class RecognitionFailed(
        override val cause: Throwable? = null,
    ) : DomainException(
        message = "Recognition engine encountered an internal failure.",
        cause = cause,
    )

    // -------------------------------------------------------------------------
    // Network exceptions
    // -------------------------------------------------------------------------

    /** Device is offline or server is unreachable. */
    data class NetworkUnavailable(
        override val cause: Throwable? = null,
    ) : DomainException(
        message = "Network is unavailable. Please check your connection.",
        cause = cause,
    )

    /** Server returned an unexpected error. */
    data class ServerError(
        val httpCode: Int,
        override val cause: Throwable? = null,
    ) : DomainException(
        message = "Server returned HTTP $httpCode.",
        cause = cause,
    )
}
