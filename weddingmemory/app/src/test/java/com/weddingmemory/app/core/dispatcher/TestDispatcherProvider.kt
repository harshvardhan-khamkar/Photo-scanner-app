package com.weddingmemory.app.core.dispatcher

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * TestDispatcherProvider — test double for [DispatcherProvider].
 *
 * Uses [UnconfinedTestDispatcher] for all dispatchers so that coroutines
 * launched in tests execute immediately without the need for runTest or
 * advanceUntilIdle in simple assertion scenarios.
 *
 * Usage in unit tests:
 * ```kotlin
 * private val testDispatchers = TestDispatcherProvider()
 *
 * @Before fun setUp() {
 *     Dispatchers.setMain(testDispatchers.main)
 * }
 *
 * @After fun tearDown() {
 *     Dispatchers.resetMain()
 * }
 * ```
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestDispatcherProvider(
    override val main: CoroutineDispatcher = UnconfinedTestDispatcher(),
    override val mainImmediate: CoroutineDispatcher = UnconfinedTestDispatcher(),
    override val io: CoroutineDispatcher = UnconfinedTestDispatcher(),
    override val default: CoroutineDispatcher = UnconfinedTestDispatcher(),
    override val unconfined: CoroutineDispatcher = UnconfinedTestDispatcher(),
) : DispatcherProvider
