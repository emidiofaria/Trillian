package com.bmw.drivingcoach.data.db;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import com.bmw.drivingcoach.data.db.dao.CoachingInsightDao;
import com.bmw.drivingcoach.data.db.dao.CoachingInsightDao_Impl;
import com.bmw.drivingcoach.data.db.dao.LapDao;
import com.bmw.drivingcoach.data.db.dao.LapDao_Impl;
import com.bmw.drivingcoach.data.db.dao.SessionDao;
import com.bmw.drivingcoach.data.db.dao.SessionDao_Impl;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class BMWDatabase_Impl extends BMWDatabase {
  private volatile SessionDao _sessionDao;

  private volatile LapDao _lapDao;

  private volatile CoachingInsightDao _coachingInsightDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(1) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `userId` TEXT NOT NULL, `trackName` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `endedAt` INTEGER, `rawFilePath` TEXT NOT NULL, `uploadStatus` TEXT NOT NULL, `processingStatus` TEXT NOT NULL, `remoteSessionId` TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `laps` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER NOT NULL, `lapNumber` INTEGER NOT NULL, `startTs` INTEGER NOT NULL, `endTs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `sector1Ms` INTEGER NOT NULL, `sector2Ms` INTEGER NOT NULL, `sector3Ms` INTEGER NOT NULL, `isBestLap` INTEGER NOT NULL, FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_laps_sessionId` ON `laps` (`sessionId`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `coaching_insights` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER NOT NULL, `headline` TEXT NOT NULL, `detail` TEXT NOT NULL, `generatedAt` INTEGER NOT NULL, FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_coaching_insights_sessionId` ON `coaching_insights` (`sessionId`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '5ef466c690e8abcef4d7478019c9fb69')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `sessions`");
        db.execSQL("DROP TABLE IF EXISTS `laps`");
        db.execSQL("DROP TABLE IF EXISTS `coaching_insights`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        db.execSQL("PRAGMA foreign_keys = ON");
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsSessions = new HashMap<String, TableInfo.Column>(9);
        _columnsSessions.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSessions.put("userId", new TableInfo.Column("userId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSessions.put("trackName", new TableInfo.Column("trackName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSessions.put("startedAt", new TableInfo.Column("startedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSessions.put("endedAt", new TableInfo.Column("endedAt", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSessions.put("rawFilePath", new TableInfo.Column("rawFilePath", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSessions.put("uploadStatus", new TableInfo.Column("uploadStatus", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSessions.put("processingStatus", new TableInfo.Column("processingStatus", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSessions.put("remoteSessionId", new TableInfo.Column("remoteSessionId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysSessions = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesSessions = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoSessions = new TableInfo("sessions", _columnsSessions, _foreignKeysSessions, _indicesSessions);
        final TableInfo _existingSessions = TableInfo.read(db, "sessions");
        if (!_infoSessions.equals(_existingSessions)) {
          return new RoomOpenHelper.ValidationResult(false, "sessions(com.bmw.drivingcoach.data.db.entity.SessionEntity).\n"
                  + " Expected:\n" + _infoSessions + "\n"
                  + " Found:\n" + _existingSessions);
        }
        final HashMap<String, TableInfo.Column> _columnsLaps = new HashMap<String, TableInfo.Column>(10);
        _columnsLaps.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLaps.put("sessionId", new TableInfo.Column("sessionId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLaps.put("lapNumber", new TableInfo.Column("lapNumber", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLaps.put("startTs", new TableInfo.Column("startTs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLaps.put("endTs", new TableInfo.Column("endTs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLaps.put("durationMs", new TableInfo.Column("durationMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLaps.put("sector1Ms", new TableInfo.Column("sector1Ms", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLaps.put("sector2Ms", new TableInfo.Column("sector2Ms", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLaps.put("sector3Ms", new TableInfo.Column("sector3Ms", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLaps.put("isBestLap", new TableInfo.Column("isBestLap", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysLaps = new HashSet<TableInfo.ForeignKey>(1);
        _foreignKeysLaps.add(new TableInfo.ForeignKey("sessions", "CASCADE", "NO ACTION", Arrays.asList("sessionId"), Arrays.asList("id")));
        final HashSet<TableInfo.Index> _indicesLaps = new HashSet<TableInfo.Index>(1);
        _indicesLaps.add(new TableInfo.Index("index_laps_sessionId", false, Arrays.asList("sessionId"), Arrays.asList("ASC")));
        final TableInfo _infoLaps = new TableInfo("laps", _columnsLaps, _foreignKeysLaps, _indicesLaps);
        final TableInfo _existingLaps = TableInfo.read(db, "laps");
        if (!_infoLaps.equals(_existingLaps)) {
          return new RoomOpenHelper.ValidationResult(false, "laps(com.bmw.drivingcoach.data.db.entity.LapEntity).\n"
                  + " Expected:\n" + _infoLaps + "\n"
                  + " Found:\n" + _existingLaps);
        }
        final HashMap<String, TableInfo.Column> _columnsCoachingInsights = new HashMap<String, TableInfo.Column>(5);
        _columnsCoachingInsights.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCoachingInsights.put("sessionId", new TableInfo.Column("sessionId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCoachingInsights.put("headline", new TableInfo.Column("headline", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCoachingInsights.put("detail", new TableInfo.Column("detail", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCoachingInsights.put("generatedAt", new TableInfo.Column("generatedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysCoachingInsights = new HashSet<TableInfo.ForeignKey>(1);
        _foreignKeysCoachingInsights.add(new TableInfo.ForeignKey("sessions", "CASCADE", "NO ACTION", Arrays.asList("sessionId"), Arrays.asList("id")));
        final HashSet<TableInfo.Index> _indicesCoachingInsights = new HashSet<TableInfo.Index>(1);
        _indicesCoachingInsights.add(new TableInfo.Index("index_coaching_insights_sessionId", false, Arrays.asList("sessionId"), Arrays.asList("ASC")));
        final TableInfo _infoCoachingInsights = new TableInfo("coaching_insights", _columnsCoachingInsights, _foreignKeysCoachingInsights, _indicesCoachingInsights);
        final TableInfo _existingCoachingInsights = TableInfo.read(db, "coaching_insights");
        if (!_infoCoachingInsights.equals(_existingCoachingInsights)) {
          return new RoomOpenHelper.ValidationResult(false, "coaching_insights(com.bmw.drivingcoach.data.db.entity.CoachingInsightEntity).\n"
                  + " Expected:\n" + _infoCoachingInsights + "\n"
                  + " Found:\n" + _existingCoachingInsights);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "5ef466c690e8abcef4d7478019c9fb69", "5429d7d89d989659725aea486631ef93");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "sessions","laps","coaching_insights");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    final boolean _supportsDeferForeignKeys = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP;
    try {
      if (!_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA foreign_keys = FALSE");
      }
      super.beginTransaction();
      if (_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA defer_foreign_keys = TRUE");
      }
      _db.execSQL("DELETE FROM `sessions`");
      _db.execSQL("DELETE FROM `laps`");
      _db.execSQL("DELETE FROM `coaching_insights`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      if (!_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA foreign_keys = TRUE");
      }
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(SessionDao.class, SessionDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(LapDao.class, LapDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(CoachingInsightDao.class, CoachingInsightDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public SessionDao sessionDao() {
    if (_sessionDao != null) {
      return _sessionDao;
    } else {
      synchronized(this) {
        if(_sessionDao == null) {
          _sessionDao = new SessionDao_Impl(this);
        }
        return _sessionDao;
      }
    }
  }

  @Override
  public LapDao lapDao() {
    if (_lapDao != null) {
      return _lapDao;
    } else {
      synchronized(this) {
        if(_lapDao == null) {
          _lapDao = new LapDao_Impl(this);
        }
        return _lapDao;
      }
    }
  }

  @Override
  public CoachingInsightDao coachingInsightDao() {
    if (_coachingInsightDao != null) {
      return _coachingInsightDao;
    } else {
      synchronized(this) {
        if(_coachingInsightDao == null) {
          _coachingInsightDao = new CoachingInsightDao_Impl(this);
        }
        return _coachingInsightDao;
      }
    }
  }
}
