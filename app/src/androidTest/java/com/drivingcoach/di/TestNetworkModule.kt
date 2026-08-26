package com.drivingcoach.di

import android.content.Context
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.drivingcoach.data.api.ApiService
import com.drivingcoach.data.api.AuthInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [NetworkModule::class]
)
object TestNetworkModule {

    // Static base URL avoids MockWebServer.url() call during DI initialization
    // Tests that need MockWebServer can start it and it will listen on port 8080
    private const val TEST_BASE_URL = "http://localhost:8080/"

    @Provides
    @Singleton
    fun provideMockWebServer(): MockWebServer {
        // Create but don't start - tests will start it when needed
        return MockWebServer()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(TEST_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService {
        return retrofit.create(ApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
        // The app disables WorkManagerInitializer in its manifest and initialises WorkManager
        // itself, which never happens under instrumentation. Any ViewModel that injects
        // WorkManager -- SessionResultViewModel, for one -- would otherwise crash the whole
        // instrumentation process on the first screen that uses it.
        //
        // The test initialiser also swaps in a test driver, so enqueued work stays parked
        // behind its constraints instead of firing real uploads during a UI test.
        return runCatching { WorkManager.getInstance(context) }.getOrElse {
            WorkManagerTestInitHelper.initializeTestWorkManager(context)
            WorkManager.getInstance(context)
        }
    }
}
