package com.drivingcoach.di

import com.drivingcoach.data.location.ApplicationScope
import com.drivingcoach.data.location.FusedLocationUpdates
import com.drivingcoach.data.location.LocationUpdates
import com.drivingcoach.data.location.WarmUpTimings
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * GPS warm-up bindings.
 *
 * Isolated so instrumented tests can `@UninstallModules(LocationModule::class)` and feed the
 * warm-up a scripted sequence of fixes, instead of trying to coax real satellites out of an
 * emulator.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LocationModule {

    @Binds
    @Singleton
    abstract fun bindLocationUpdates(impl: FusedLocationUpdates): LocationUpdates

    companion object {

        @Provides
        @Singleton
        fun provideWarmUpTimings(): WarmUpTimings = WarmUpTimings()

        /**
         * Deliberately never cancelled: it is the process. [SupervisorJob] keeps one failed
         * warm-up from poisoning the scope for every later one.
         */
        @Provides
        @Singleton
        @ApplicationScope
        fun provideApplicationScope(
            @IoDispatcher dispatcher: kotlinx.coroutines.CoroutineDispatcher
        ): CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
    }
}
