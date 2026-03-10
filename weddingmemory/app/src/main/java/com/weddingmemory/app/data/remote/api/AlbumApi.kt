package com.weddingmemory.app.data.remote.api

import com.weddingmemory.app.data.remote.dto.UnlockResponseDto
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * AlbumApi — Retrofit interface for all album-related endpoints.
 *
 * Base URL is configured in [com.weddingmemory.app.di.NetworkModule].
 * All functions are suspend — called from coroutines, never from main thread.
 *
 * The mock OkHttp interceptor in [NetworkModule] intercepts these calls
 * and returns hardcoded JSON until a real backend is available.
 */
interface AlbumApi {

    /**
     * Verify an album unlock code and retrieve the full album manifest.
     *
     * Endpoint: GET /api/v1/albums/unlock/{code}
     *
     * @param code The short alphanumeric code entered by the guest (uppercased).
     * @return [UnlockResponseDto] containing the album and all its frames.
     */
    @GET("api/v1/albums/unlock/{code}")
    suspend fun unlockAlbum(@Path("code") code: String): UnlockResponseDto
}
