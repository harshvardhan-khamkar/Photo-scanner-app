package com.weddingmemory.app.core.dispatcher

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DefaultDispatcherProvider — production implementation backed by the real
 * [Dispatchers] object from kotlinx-coroutines.
 *
 * Bound at the Hilt [Singleton] scope; one instance for the entire app lifetime.
 * Tests should replace this with a test double via [HiltAndroidTest] or a
 * constructor-injected fake.
 */
@Singleton
class DefaultDispatcherProvider @Inject constructor() : DispatcherProvider {

    override val main: CoroutineDispatcher
        get() = Dispatchers.Main

    override val mainImmediate: CoroutineDispatcher
        get() = Dispatchers.Main.immediate

    override val io: CoroutineDispatcher
        get() = Dispatchers.IO

    override val default: CoroutineDispatcher
        get() = Dispatchers.Default

    override val unconfined: CoroutineDispatcher
        get() = Dispatchers.Unconfined
}
