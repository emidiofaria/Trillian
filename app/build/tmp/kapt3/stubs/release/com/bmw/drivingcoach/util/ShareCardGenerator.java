package com.bmw.drivingcoach.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import androidx.core.content.ContextCompat;
import com.bmw.drivingcoach.R;
import com.bmw.drivingcoach.data.db.entity.LapEntity;
import com.bmw.drivingcoach.data.db.entity.SessionEntity;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00002\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u0007\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J(\u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\n2\b\u0010\u000b\u001a\u0004\u0018\u00010\f2\u0006\u0010\r\u001a\u00020\u00062\u0006\u0010\u000e\u001a\u00020\u000fR\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0010"}, d2 = {"Lcom/bmw/drivingcoach/util/ShareCardGenerator;", "", "()V", "CARD_SIZE", "", "PADDING", "", "generate", "Landroid/graphics/Bitmap;", "session", "Lcom/bmw/drivingcoach/data/db/entity/SessionEntity;", "bestLap", "Lcom/bmw/drivingcoach/data/db/entity/LapEntity;", "consistencyScore", "context", "Landroid/content/Context;", "app_release"})
public final class ShareCardGenerator {
    private static final int CARD_SIZE = 1080;
    private static final float PADDING = 80.0F;
    @org.jetbrains.annotations.NotNull()
    public static final com.bmw.drivingcoach.util.ShareCardGenerator INSTANCE = null;
    
    private ShareCardGenerator() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final android.graphics.Bitmap generate(@org.jetbrains.annotations.NotNull()
    com.bmw.drivingcoach.data.db.entity.SessionEntity session, @org.jetbrains.annotations.Nullable()
    com.bmw.drivingcoach.data.db.entity.LapEntity bestLap, float consistencyScore, @org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        return null;
    }
}