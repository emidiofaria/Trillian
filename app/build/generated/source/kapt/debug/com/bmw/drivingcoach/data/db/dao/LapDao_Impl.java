package com.bmw.drivingcoach.data.db.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.bmw.drivingcoach.data.db.entity.LapEntity;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class LapDao_Impl implements LapDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<LapEntity> __insertionAdapterOfLapEntity;

  private final SharedSQLiteStatement __preparedStmtOfClearBestLap;

  private final SharedSQLiteStatement __preparedStmtOfSetBestLap;

  public LapDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfLapEntity = new EntityInsertionAdapter<LapEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `laps` (`id`,`sessionId`,`lapNumber`,`startTs`,`endTs`,`durationMs`,`sector1Ms`,`sector2Ms`,`sector3Ms`,`isBestLap`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final LapEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getSessionId());
        statement.bindLong(3, entity.getLapNumber());
        statement.bindLong(4, entity.getStartTs());
        statement.bindLong(5, entity.getEndTs());
        statement.bindLong(6, entity.getDurationMs());
        statement.bindLong(7, entity.getSector1Ms());
        statement.bindLong(8, entity.getSector2Ms());
        statement.bindLong(9, entity.getSector3Ms());
        final int _tmp = entity.isBestLap() ? 1 : 0;
        statement.bindLong(10, _tmp);
      }
    };
    this.__preparedStmtOfClearBestLap = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE laps SET isBestLap = 0 WHERE sessionId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfSetBestLap = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE laps SET isBestLap = 1 WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insertLaps(final List<LapEntity> laps,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfLapEntity.insert(laps);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertLap(final LapEntity lap, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfLapEntity.insertAndReturnId(lap);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object clearBestLap(final long sessionId, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfClearBestLap.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, sessionId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfClearBestLap.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object setBestLap(final long lapId, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfSetBestLap.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, lapId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfSetBestLap.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object getLapById(final long lapId, final Continuation<? super LapEntity> $completion) {
    final String _sql = "SELECT * FROM laps WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, lapId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<LapEntity>() {
      @Override
      @Nullable
      public LapEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
          final int _cursorIndexOfLapNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "lapNumber");
          final int _cursorIndexOfStartTs = CursorUtil.getColumnIndexOrThrow(_cursor, "startTs");
          final int _cursorIndexOfEndTs = CursorUtil.getColumnIndexOrThrow(_cursor, "endTs");
          final int _cursorIndexOfDurationMs = CursorUtil.getColumnIndexOrThrow(_cursor, "durationMs");
          final int _cursorIndexOfSector1Ms = CursorUtil.getColumnIndexOrThrow(_cursor, "sector1Ms");
          final int _cursorIndexOfSector2Ms = CursorUtil.getColumnIndexOrThrow(_cursor, "sector2Ms");
          final int _cursorIndexOfSector3Ms = CursorUtil.getColumnIndexOrThrow(_cursor, "sector3Ms");
          final int _cursorIndexOfIsBestLap = CursorUtil.getColumnIndexOrThrow(_cursor, "isBestLap");
          final LapEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpSessionId;
            _tmpSessionId = _cursor.getLong(_cursorIndexOfSessionId);
            final int _tmpLapNumber;
            _tmpLapNumber = _cursor.getInt(_cursorIndexOfLapNumber);
            final long _tmpStartTs;
            _tmpStartTs = _cursor.getLong(_cursorIndexOfStartTs);
            final long _tmpEndTs;
            _tmpEndTs = _cursor.getLong(_cursorIndexOfEndTs);
            final long _tmpDurationMs;
            _tmpDurationMs = _cursor.getLong(_cursorIndexOfDurationMs);
            final long _tmpSector1Ms;
            _tmpSector1Ms = _cursor.getLong(_cursorIndexOfSector1Ms);
            final long _tmpSector2Ms;
            _tmpSector2Ms = _cursor.getLong(_cursorIndexOfSector2Ms);
            final long _tmpSector3Ms;
            _tmpSector3Ms = _cursor.getLong(_cursorIndexOfSector3Ms);
            final boolean _tmpIsBestLap;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsBestLap);
            _tmpIsBestLap = _tmp != 0;
            _result = new LapEntity(_tmpId,_tmpSessionId,_tmpLapNumber,_tmpStartTs,_tmpEndTs,_tmpDurationMs,_tmpSector1Ms,_tmpSector2Ms,_tmpSector3Ms,_tmpIsBestLap);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<LapEntity>> getLapsForSession(final long sessionId) {
    final String _sql = "SELECT * FROM laps WHERE sessionId = ? ORDER BY lapNumber ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, sessionId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"laps"}, new Callable<List<LapEntity>>() {
      @Override
      @NonNull
      public List<LapEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
          final int _cursorIndexOfLapNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "lapNumber");
          final int _cursorIndexOfStartTs = CursorUtil.getColumnIndexOrThrow(_cursor, "startTs");
          final int _cursorIndexOfEndTs = CursorUtil.getColumnIndexOrThrow(_cursor, "endTs");
          final int _cursorIndexOfDurationMs = CursorUtil.getColumnIndexOrThrow(_cursor, "durationMs");
          final int _cursorIndexOfSector1Ms = CursorUtil.getColumnIndexOrThrow(_cursor, "sector1Ms");
          final int _cursorIndexOfSector2Ms = CursorUtil.getColumnIndexOrThrow(_cursor, "sector2Ms");
          final int _cursorIndexOfSector3Ms = CursorUtil.getColumnIndexOrThrow(_cursor, "sector3Ms");
          final int _cursorIndexOfIsBestLap = CursorUtil.getColumnIndexOrThrow(_cursor, "isBestLap");
          final List<LapEntity> _result = new ArrayList<LapEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final LapEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpSessionId;
            _tmpSessionId = _cursor.getLong(_cursorIndexOfSessionId);
            final int _tmpLapNumber;
            _tmpLapNumber = _cursor.getInt(_cursorIndexOfLapNumber);
            final long _tmpStartTs;
            _tmpStartTs = _cursor.getLong(_cursorIndexOfStartTs);
            final long _tmpEndTs;
            _tmpEndTs = _cursor.getLong(_cursorIndexOfEndTs);
            final long _tmpDurationMs;
            _tmpDurationMs = _cursor.getLong(_cursorIndexOfDurationMs);
            final long _tmpSector1Ms;
            _tmpSector1Ms = _cursor.getLong(_cursorIndexOfSector1Ms);
            final long _tmpSector2Ms;
            _tmpSector2Ms = _cursor.getLong(_cursorIndexOfSector2Ms);
            final long _tmpSector3Ms;
            _tmpSector3Ms = _cursor.getLong(_cursorIndexOfSector3Ms);
            final boolean _tmpIsBestLap;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsBestLap);
            _tmpIsBestLap = _tmp != 0;
            _item = new LapEntity(_tmpId,_tmpSessionId,_tmpLapNumber,_tmpStartTs,_tmpEndTs,_tmpDurationMs,_tmpSector1Ms,_tmpSector2Ms,_tmpSector3Ms,_tmpIsBestLap);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<LapEntity> getBestLap(final long sessionId) {
    final String _sql = "SELECT * FROM laps WHERE sessionId = ? AND isBestLap = 1 LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, sessionId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"laps"}, new Callable<LapEntity>() {
      @Override
      @Nullable
      public LapEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
          final int _cursorIndexOfLapNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "lapNumber");
          final int _cursorIndexOfStartTs = CursorUtil.getColumnIndexOrThrow(_cursor, "startTs");
          final int _cursorIndexOfEndTs = CursorUtil.getColumnIndexOrThrow(_cursor, "endTs");
          final int _cursorIndexOfDurationMs = CursorUtil.getColumnIndexOrThrow(_cursor, "durationMs");
          final int _cursorIndexOfSector1Ms = CursorUtil.getColumnIndexOrThrow(_cursor, "sector1Ms");
          final int _cursorIndexOfSector2Ms = CursorUtil.getColumnIndexOrThrow(_cursor, "sector2Ms");
          final int _cursorIndexOfSector3Ms = CursorUtil.getColumnIndexOrThrow(_cursor, "sector3Ms");
          final int _cursorIndexOfIsBestLap = CursorUtil.getColumnIndexOrThrow(_cursor, "isBestLap");
          final LapEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpSessionId;
            _tmpSessionId = _cursor.getLong(_cursorIndexOfSessionId);
            final int _tmpLapNumber;
            _tmpLapNumber = _cursor.getInt(_cursorIndexOfLapNumber);
            final long _tmpStartTs;
            _tmpStartTs = _cursor.getLong(_cursorIndexOfStartTs);
            final long _tmpEndTs;
            _tmpEndTs = _cursor.getLong(_cursorIndexOfEndTs);
            final long _tmpDurationMs;
            _tmpDurationMs = _cursor.getLong(_cursorIndexOfDurationMs);
            final long _tmpSector1Ms;
            _tmpSector1Ms = _cursor.getLong(_cursorIndexOfSector1Ms);
            final long _tmpSector2Ms;
            _tmpSector2Ms = _cursor.getLong(_cursorIndexOfSector2Ms);
            final long _tmpSector3Ms;
            _tmpSector3Ms = _cursor.getLong(_cursorIndexOfSector3Ms);
            final boolean _tmpIsBestLap;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsBestLap);
            _tmpIsBestLap = _tmp != 0;
            _result = new LapEntity(_tmpId,_tmpSessionId,_tmpLapNumber,_tmpStartTs,_tmpEndTs,_tmpDurationMs,_tmpSector1Ms,_tmpSector2Ms,_tmpSector3Ms,_tmpIsBestLap);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getFastestLap(final long sessionId,
      final Continuation<? super LapEntity> $completion) {
    final String _sql = "SELECT * FROM laps WHERE sessionId = ? ORDER BY durationMs ASC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, sessionId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<LapEntity>() {
      @Override
      @Nullable
      public LapEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
          final int _cursorIndexOfLapNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "lapNumber");
          final int _cursorIndexOfStartTs = CursorUtil.getColumnIndexOrThrow(_cursor, "startTs");
          final int _cursorIndexOfEndTs = CursorUtil.getColumnIndexOrThrow(_cursor, "endTs");
          final int _cursorIndexOfDurationMs = CursorUtil.getColumnIndexOrThrow(_cursor, "durationMs");
          final int _cursorIndexOfSector1Ms = CursorUtil.getColumnIndexOrThrow(_cursor, "sector1Ms");
          final int _cursorIndexOfSector2Ms = CursorUtil.getColumnIndexOrThrow(_cursor, "sector2Ms");
          final int _cursorIndexOfSector3Ms = CursorUtil.getColumnIndexOrThrow(_cursor, "sector3Ms");
          final int _cursorIndexOfIsBestLap = CursorUtil.getColumnIndexOrThrow(_cursor, "isBestLap");
          final LapEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpSessionId;
            _tmpSessionId = _cursor.getLong(_cursorIndexOfSessionId);
            final int _tmpLapNumber;
            _tmpLapNumber = _cursor.getInt(_cursorIndexOfLapNumber);
            final long _tmpStartTs;
            _tmpStartTs = _cursor.getLong(_cursorIndexOfStartTs);
            final long _tmpEndTs;
            _tmpEndTs = _cursor.getLong(_cursorIndexOfEndTs);
            final long _tmpDurationMs;
            _tmpDurationMs = _cursor.getLong(_cursorIndexOfDurationMs);
            final long _tmpSector1Ms;
            _tmpSector1Ms = _cursor.getLong(_cursorIndexOfSector1Ms);
            final long _tmpSector2Ms;
            _tmpSector2Ms = _cursor.getLong(_cursorIndexOfSector2Ms);
            final long _tmpSector3Ms;
            _tmpSector3Ms = _cursor.getLong(_cursorIndexOfSector3Ms);
            final boolean _tmpIsBestLap;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsBestLap);
            _tmpIsBestLap = _tmp != 0;
            _result = new LapEntity(_tmpId,_tmpSessionId,_tmpLapNumber,_tmpStartTs,_tmpEndTs,_tmpDurationMs,_tmpSector1Ms,_tmpSector2Ms,_tmpSector3Ms,_tmpIsBestLap);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
