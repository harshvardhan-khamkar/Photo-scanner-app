package com.weddingmemory.app.di

import com.weddingmemory.app.core.dispatcher.DefaultDispatcherProvider
import com.weddingmemory.app.core.dispatcher.DispatcherProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * AppModule — root Hilt module installed in [SingletonComponent].
 *
 * Provides application-scoped dependencies that live for the full app lifetime.
 * Add new singleton bindings here as the app grows (e.g. NetworkClient,
 * DatabaseInstance, SharedPreferences, etc.).
 *
 * Separation strategy:
 *  - [AppModule]       → cross-cutting concerns (dispatchers, context wrappers)
 *  - [NetworkModule]   → Retrofit, OkHttp (added in Step 2)
 *  - [DatabaseModule]  → Room (added in Step 2)
 *  - [RepositoryModule]→ Repository bindings (added in Step 3)
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    /**
     * Bind [DefaultDispatcherProvider] as [DispatcherProvider].
     * Tests can override this binding with a [TestDispatcherProvider].
     */
    @Binds
    @Singleton
    abstract fun bindDispatcherProvider(
        impl: DefaultDispatcherProvider
    ): DispatcherProvider
}
