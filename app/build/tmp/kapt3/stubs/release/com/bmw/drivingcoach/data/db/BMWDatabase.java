package com.bmw.drivingcoach.data.db;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import com.bmw.drivingcoach.data.db.dao.CoachingInsightDao;
import com.bmw.drivingcoach.data.db.dao.LapDao;
import com.bmw.drivingcoach.data.db.dao.SessionDao;
import com.bmw.drivingcoach.data.db.entity.CoachingInsightEntity;
import com.bmw.drivingcoach.data.db.entity.LapEntity;
import com.bmw.drivingcoach.data.db.entity.SessionEntity;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\b\'\u0018\u0000 \t2\u00020\u0001:\u0001\tB\u0005\u00a2\u0006\u0002\u0010\u0002J\b\u0010\u0003\u001a\u00020\u0004H&J\b\u0010\u0005\u001a\u00020\u0006H&J\b\u0010\u0007\u001a\u00020\bH&\u00a8\u0006\n"}, d2 = {"Lcom/bmw/drivingcoach/data/db/BMWDatabase;", "Landroidx/room/RoomDatabase;", "()V", "coachingInsightDao", "Lcom/bmw/drivingcoach/data/db/dao/CoachingInsightDao;", "lapDao", "Lcom/bmw/drivingcoach/data/db/dao/LapDao;", "sessionDao", "Lcom/bmw/drivingcoach/data/db/dao/SessionDao;", "Companion", "app_release"})
@androidx.room.Database(entities = {com.bmw.drivingcoach.data.db.entity.SessionEntity.class, com.bmw.drivingcoach.data.db.entity.LapEntity.class, com.bmw.drivingcoach.data.db.entity.CoachingInsightEntity.class}, version = 1, exportSchema = true)
public abstract class BMWDatabase extends androidx.room.RoomDatabase {
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String DATABASE_NAME = "bmw_driving_coach.db";
    @org.jetbrains.annotations.NotNull()
    public static final com.bmw.drivingcoach.data.db.BMWDatabase.Companion Companion = null;
    
    public BMWDatabase() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public abstract com.bmw.drivingcoach.data.db.dao.SessionDao sessionDao();
    
    @org.jetbrains.annotations.NotNull()
    public abstract com.bmw.drivingcoach.data.db.dao.LapDao lapDao();
    
    @org.jetbrains.annotations.NotNull()
    public abstract com.bmw.drivingcoach.data.db.dao.CoachingInsightDao coachingInsightDao();
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0005"}, d2 = {"Lcom/bmw/drivingcoach/data/db/BMWDatabase$Companion;", "", "()V", "DATABASE_NAME", "", "app_release"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}