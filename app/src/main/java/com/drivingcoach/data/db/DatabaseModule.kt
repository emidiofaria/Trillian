package com.drivingcoach.data.db

import android.content.Context
import androidx.room.Room
import com.drivingcoach.data.db.dao.CoachingInsightDao
import com.drivingcoach.data.db.dao.LapDao
import com.drivingcoach.data.db.dao.SessionDao
import com.drivingcoach.data.db.dao.SessionPreferenceDao
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
    fun provideDatabase(@ApplicationContext context: Context): DrivingCoachDatabase {
        return Room.databaseBuilder(
            context,
            DrivingCoachDatabase::class.java,
            DrivingCoachDatabase.DATABASE_NAME
        )
            .addMigrations(
                DrivingCoachDatabase.MIGRATION_1_2, 
                DrivingCoachDatabase.MIGRATION_2_3,
                DrivingCoachDatabase.MIGRATION_3_4
            )
            .fallbackToDestructiveMigration()
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
}
