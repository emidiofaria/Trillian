package com.drivingcoach.di

import android.content.Context
import androidx.room.Room
import com.drivingcoach.data.db.DrivingCoachDatabase
import com.drivingcoach.data.db.DatabaseModule
import com.drivingcoach.data.db.dao.CoachingInsightDao
import com.drivingcoach.data.db.dao.LapDao
import com.drivingcoach.data.db.dao.SessionDao
import com.drivingcoach.data.db.dao.SessionPreferenceDao
import com.drivingcoach.data.db.dao.TrackDao
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
    fun provideInMemoryDatabase(@ApplicationContext context: Context): DrivingCoachDatabase {
        return Room.inMemoryDatabaseBuilder(
            context,
            DrivingCoachDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
    }

    @Provides
    fun provideSessionDao(database: DrivingCoachDatabase): SessionDao {
        return database.sessionDao()
    }

    @Provides
    fun provideLapDao(database: DrivingCoachDatabase): LapDao {
        return database.lapDao()
    }

    @Provides
    fun provideCoachingInsightDao(database: DrivingCoachDatabase): CoachingInsightDao {
        return database.coachingInsightDao()
    }

    @Provides
    fun provideSessionPreferenceDao(database: DrivingCoachDatabase): SessionPreferenceDao {
        return database.sessionPreferenceDao()
    }

    @Provides
    fun provideTrackDao(database: DrivingCoachDatabase): TrackDao {
        return database.trackDao()
    }
}
