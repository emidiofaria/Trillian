package com.bmw.drivingcoach.di

import android.content.Context
import androidx.room.Room
import com.bmw.drivingcoach.data.db.BMWDatabase
import com.bmw.drivingcoach.data.db.DatabaseModule
import com.bmw.drivingcoach.data.db.dao.CoachingInsightDao
import com.bmw.drivingcoach.data.db.dao.LapDao
import com.bmw.drivingcoach.data.db.dao.SessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DatabaseModule::class]
)
object TestDatabaseModule {

    @Provides
    @Singleton
    fun provideInMemoryDatabase(@ApplicationContext context: Context): BMWDatabase {
        return Room.inMemoryDatabaseBuilder(
            context,
            BMWDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
    }

    @Provides
    fun provideSessionDao(database: BMWDatabase): SessionDao {
        return database.sessionDao()
    }

    @Provides
    fun provideLapDao(database: BMWDatabase): LapDao {
        return database.lapDao()
    }

    @Provides
    fun provideCoachingInsightDao(database: BMWDatabase): CoachingInsightDao {
        return database.coachingInsightDao()
    }
}
