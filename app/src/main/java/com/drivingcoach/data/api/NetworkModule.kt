package com.drivingcoach.data.api

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TelemetryApiModule {

    @Provides
    @Singleton
    fun provideTelemetryApiService(retrofit: Retrofit): TelemetryApiService {
        return retrofit.create(TelemetryApiService::class.java)
    }
}
