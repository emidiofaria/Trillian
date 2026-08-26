package com.drivingcoach.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Application-wide bindings that no test ever needs to replace.
 *
 * Storage, dispatchers and startup timings were moved to [DataStoreModule],
 * [DispatcherModule] and [SplashModule] so instrumented tests can uninstall exactly the
 * one collaborator they need to fake.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideApplicationContext(@ApplicationContext context: Context): Context = context
}
