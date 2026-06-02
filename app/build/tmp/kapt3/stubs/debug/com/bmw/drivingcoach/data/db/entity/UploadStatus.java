package com.bmw.drivingcoach.data.db.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0010\n\u0002\b\u0006\b\u0086\u0081\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00000\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002j\u0002\b\u0003j\u0002\b\u0004j\u0002\b\u0005j\u0002\b\u0006\u00a8\u0006\u0007"}, d2 = {"Lcom/bmw/drivingcoach/data/db/entity/UploadStatus;", "", "(Ljava/lang/String;I)V", "PENDING", "UPLOADING", "DONE", "FAILED", "app_debug"})
public enum UploadStatus {
    /*public static final*/ PENDING /* = new PENDING() */,
    /*public static final*/ UPLOADING /* = new UPLOADING() */,
    /*public static final*/ DONE /* = new DONE() */,
    /*public static final*/ FAILED /* = new FAILED() */;
    
    UploadStatus() {
    }
    
    @org.jetbrains.annotations.NotNull()
    public static kotlin.enums.EnumEntries<com.bmw.drivingcoach.data.db.entity.UploadStatus> getEntries() {
        return null;
    }
}