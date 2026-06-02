package com.bmw.drivingcoach.data.db;

import android.content.Context;
import androidx.room.Room;
import com.bmw.drivingcoach.data.db.dao.CoachingInsightDao;
import com.bmw.drivingcoach.data.db.dao.LapDao;
import com.bmw.drivingcoach.data.db.dao.SessionDao;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;
import javax.inject.Singleton;

@dagger.Module()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000,\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u00c7\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0007J\u0012\u0010\u0007\u001a\u00020\u00062\b\b\u0001\u0010\b\u001a\u00020\tH\u0007J\u0010\u0010\n\u001a\u00020\u000b2\u0006\u0010\u0005\u001a\u00020\u0006H\u0007J\u0010\u0010\f\u001a\u00020\r2\u0006\u0010\u0005\u001a\u00020\u0006H\u0007\u00a8\u0006\u000e"}, d2 = {"Lcom/bmw/drivingcoach/data/db/DatabaseModule;", "", "()V", "provideCoachingInsightDao", "Lcom/bmw/drivingcoach/data/db/dao/CoachingInsightDao;", "database", "Lcom/bmw/drivingcoach/data/db/BMWDatabase;", "provideDatabase", "context", "Landroid/content/Context;", "provideLapDao", "Lcom/bmw/drivingcoach/data/db/dao/LapDao;", "provideSessionDao", "Lcom/bmw/drivingcoach/data/db/dao/SessionDao;", "app_release"})
@dagger.hilt.InstallIn(value = {dagger.hilt.components.SingletonComponent.class})
public final class DatabaseModule {
    @org.jetbrains.annotations.NotNull()
    public static final com.bmw.drivingcoach.data.db.DatabaseModule INSTANCE = null;
    
    private DatabaseModule() {
        super();
    }
    
    @dagger.Provides()
    @javax.inject.Singleton()
    @org.jetbrains.annotations.NotNull()
    public final com.bmw.drivingcoach.data.db.BMWDatabase provideDatabase(@dagger.hilt.android.qualifiers.ApplicationContext()
    @org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        return null;
    }
    
    @dagger.Provides()
    @org.jetbrains.annotations.NotNull()
    public final com.bmw.drivingcoach.data.db.dao.SessionDao provideSessionDao(@org.jetbrains.annotations.NotNull()
    com.bmw.drivingcoach.data.db.BMWDatabase database) {
        return null;
    }
    
    @dagger.Provides()
    @org.jetbrains.annotations.NotNull()
    public final com.bmw.drivingcoach.data.db.dao.LapDao provideLapDao(@org.jetbrains.annotations.NotNull()
    com.bmw.drivingcoach.data.db.BMWDatabase database) {
        return null;
    }
    
    @dagger.Provides()
    @org.jetbrains.annotations.NotNull()
    public final com.bmw.drivingcoach.data.db.dao.CoachingInsightDao provideCoachingInsightDao(@org.jetbrains.annotations.NotNull()
    com.bmw.drivingcoach.data.db.BMWDatabase database) {
        return null;
    }
}