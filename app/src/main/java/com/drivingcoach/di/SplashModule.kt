package com.drivingcoach.di

import com.drivingcoach.ui.splash.SplashTimings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Startup timing budget (SRS UI-02, UI-03, UI-07).
 *
 * Isolated so instrumented tests can `@UninstallModules(SplashModule::class)` and pin the
 * loading screen on screen (long `minDisplayMs`) or force the fallback path (short
 * `timeoutMs`) deterministically, instead of racing it with sleeps.
 */
@Module
@InstallIn(SingletonComponent::class)
object SplashModule {

    @Provides
    @Singleton
    fun provideSplashTimings(): SplashTimings = SplashTimings()
}
