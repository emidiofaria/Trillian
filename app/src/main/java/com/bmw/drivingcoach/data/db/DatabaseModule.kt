package com.bmw.drivingcoach.data.db

import android.content.Context
import androidx.room.Room
import com.bmw.drivingcoach.data.db.dao.CoachingInsightDao
import com.bmw.drivingcoach.data.db.dao.LapDao
import com.bmw.drivingcoach.data.db.dao.SessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BMWDatabase {
        return Room.databaseBuilder(
            context,
            BMWDatabase::class.java,
            BMWDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
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
