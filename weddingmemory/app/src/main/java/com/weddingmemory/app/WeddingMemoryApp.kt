package com.weddingmemory.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * WeddingMemoryApp — root Application class.
 *
 * Responsibilities:
 *  - Bootstrap Hilt's dependency injection graph.
 *  - Initialize global singletons safe to create at startup.
 *  - Keep this class thin; all business-level initialisation belongs
 *    in dedicated Hilt modules or initializers.
 */
@HiltAndroidApp
class WeddingMemoryApp : Application() {

    override fun onCreate() {
        super.onCreate()
        initLogging()
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Enable Timber logging in debug builds only.
     * In release builds, logs are suppressed — no sensitive data leaks.
     */
    private fun initLogging() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
