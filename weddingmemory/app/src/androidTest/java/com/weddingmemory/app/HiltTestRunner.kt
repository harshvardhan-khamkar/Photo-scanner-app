package com.weddingmemory.app

import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * HiltTestRunner — custom instrumentation test runner for Hilt.
 *
 * Required by Hilt to replace the real Application class with
 * [HiltTestApplication] during instrumented tests, so that Hilt can
 * swap out modules with test doubles without affecting production code.
 *
 * Register in app/build.gradle.kts:
 *   testInstrumentationRunner = "com.weddingmemory.app.HiltTestRunner"
 */
class HiltTestRunner : AndroidJUnitRunner() {

    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: android.content.Context?
    ): android.app.Application {
        return super.newApplication(cl, HiltTestApplication::class.java.name, context)
    }
}
