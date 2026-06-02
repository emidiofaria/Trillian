package com.bmw.drivingcoach.data.db.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0010\n\u0002\b\b\b\u0086\u0081\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00000\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002j\u0002\b\u0003j\u0002\b\u0004j\u0002\b\u0005j\u0002\b\u0006j\u0002\b\u0007j\u0002\b\b\u00a8\u0006\t"}, d2 = {"Lcom/bmw/drivingcoach/data/db/entity/ProcessingStatus;", "", "(Ljava/lang/String;I)V", "PENDING", "UPLOADING", "DETECTING_LAPS", "GENERATING_COACHING", "COMPLETE", "FAILED", "app_release"})
public enum ProcessingStatus {
    /*public static final*/ PENDING /* = new PENDING() */,
    /*public static final*/ UPLOADING /* = new UPLOADING() */,
    /*public static final*/ DETECTING_LAPS /* = new DETECTING_LAPS() */,
    /*public static final*/ GENERATING_COACHING /* = new GENERATING_COACHING() */,
    /*public static final*/ COMPLETE /* = new COMPLETE() */,
    /*public static final*/ FAILED /* = new FAILED() */;
    
    ProcessingStatus() {
    }
    
    @org.jetbrains.annotations.NotNull()
    public static kotlin.enums.EnumEntries<com.bmw.drivingcoach.data.db.entity.ProcessingStatus> getEntries() {
        return null;
    }
}