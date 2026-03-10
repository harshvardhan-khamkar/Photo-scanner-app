package com.weddingmemory.app.core.dispatcher

import kotlinx.coroutines.CoroutineDispatcher

/**
 * DispatcherProvider — abstraction over Kotlin coroutine dispatchers.
 *
 * Why:
 *  - Allows ViewModels and repositories to request a dispatcher by *role*
 *    rather than hard-coding `Dispatchers.IO`, which is untestable.
 *  - Tests supply a [TestDispatcherProvider] backed by `UnconfinedTestDispatcher`
 *    so coroutines run synchronously and Turbine/Flow assertions are deterministic.
 *
 * Usage:
 *  ```kotlin
 *  class AlbumRepository @Inject constructor(
 *      private val dispatchers: DispatcherProvider
 *  ) {
 *      suspend fun fetchAlbums() = withContext(dispatchers.io) { ... }
 *  }
 *  ```
 */
interface DispatcherProvider {

    /** Main / UI thread dispatcher.  Use for UI state updates. */
    val main: CoroutineDispatcher

    /** Immediate main dispatcher — skips frame delay. */
    val mainImmediate: CoroutineDispatcher

    /** Optimised for offloading blocking I/O (network, disk). */
    val io: CoroutineDispatcher

    /** Optimised for CPU-heavy work (image processing, diffing). */
    val default: CoroutineDispatcher

    /** Unconfined dispatcher — avoid in production; useful in tests only. */
    val unconfined: CoroutineDispatcher
}
