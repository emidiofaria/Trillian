package com.bmw.drivingcoach.data.db.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.bmw.drivingcoach.data.db.entity.SessionEntity;
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
public final class SessionDao_Impl implements SessionDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<SessionEntity> __insertionAdapterOfSessionEntity;

  private final EntityDeletionOrUpdateAdapter<SessionEntity> __updateAdapterOfSessionEntity;

  private final SharedSQLiteStatement __preparedStmtOfUpdateUploadStatus;

  private final SharedSQLiteStatement __preparedStmtOfUpdateProcessingStatus;

  private final SharedSQLiteStatement __preparedStmtOfUpdateSessionEndTime;

  public SessionDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfSessionEntity = new EntityInsertionAdapter<SessionEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `sessions` (`id`,`userId`,`trackName`,`startedAt`,`endedAt`,`rawFilePath`,`uploadStatus`,`processingStatus`,`remoteSessionId`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final SessionEntity entity) {
        statement.bindLong(1, entity.getId());
        if (entity.getUserId() == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.getUserId());
        }
        if (entity.getTrackName() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getTrackName());
        }
        statement.bindLong(4, entity.getStartedAt());
        if (entity.getEndedAt() == null) {
          statement.bindNull(5);
        } else {
          statement.bindLong(5, entity.getEndedAt());
        }
        if (entity.getRawFilePath() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getRawFilePath());
        }
        if (entity.getUploadStatus() == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.getUploadStatus());
        }
        if (entity.getProcessingStatus() == null) {
          statement.bindNull(8);
        } else {
          statement.bindString(8, entity.getProcessingStatus());
        }
        if (entity.getRemoteSessionId() == null) {
          statement.bindNull(9);
        } else {
          statement.bindString(9, entity.getRemoteSessionId());
        }
      }
    };
    this.__updateAdapterOfSessionEntity = new EntityDeletionOrUpdateAdapter<SessionEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `sessions` SET `id` = ?,`userId` = ?,`trackName` = ?,`startedAt` = ?,`endedAt` = ?,`rawFilePath` = ?,`uploadStatus` = ?,`processingStatus` = ?,`remoteSessionId` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final SessionEntity entity) {
        statement.bindLong(1, entity.getId());
        if (entity.getUserId() == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.getUserId());
        }
        if (entity.getTrackName() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getTrackName());
        }
        statement.bindLong(4, entity.getStartedAt());
        if (entity.getEndedAt() == null) {
          statement.bindNull(5);
        } else {
          statement.bindLong(5, entity.getEndedAt());
        }
        if (entity.getRawFilePath() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getRawFilePath());
        }
        if (entity.getUploadStatus() == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.getUploadStatus());
        }
        if (entity.getProcessingStatus() == null) {
          statement.bindNull(8);
        } else {
          statement.bindString(8, entity.getProcessingStatus());
        }
        if (entity.getRemoteSessionId() == null) {
          statement.bindNull(9);
        } else {
          statement.bindString(9, entity.getRemoteSessionId());
        }
        statement.bindLong(10, entity.getId());
      }
    };
    this.__preparedStmtOfUpdateUploadStatus = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE sessions SET uploadStatus = ?, remoteSessionId = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateProcessingStatus = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE sessions SET processingStatus = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateSessionEndTime = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE sessions SET endedAt = ? WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insertSession(final SessionEntity session,
      final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfSessionEntity.insertAndReturnId(session);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateSession(final SessionEntity session,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfSessionEntity.handle(session);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateUploadStatus(final long id, final String status, final String remoteId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateUploadStatus.acquire();
        int _argIndex = 1;
        if (status == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, status);
        }
        _argIndex = 2;
        if (remoteId == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, remoteId);
        }
        _argIndex = 3;
        _stmt.bindLong(_argIndex, id);
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
          __preparedStmtOfUpdateUploadStatus.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object updateProcessingStatus(final long id, final String status,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateProcessingStatus.acquire();
        int _argIndex = 1;
        if (status == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, status);
        }
        _argIndex = 2;
        _stmt.bindLong(_argIndex, id);
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
          __preparedStmtOfUpdateProcessingStatus.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object updateSessionEndTime(final long id, final long endedAt,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateSessionEndTime.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, endedAt);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, id);
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
          __preparedStmtOfUpdateSessionEndTime.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<SessionEntity> getSessionById(final long id) {
    final String _sql = "SELECT * FROM sessions WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"sessions"}, new Callable<SessionEntity>() {
      @Override
      @Nullable
      public SessionEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfUserId = CursorUtil.getColumnIndexOrThrow(_cursor, "userId");
          final int _cursorIndexOfTrackName = CursorUtil.getColumnIndexOrThrow(_cursor, "trackName");
          final int _cursorIndexOfStartedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "startedAt");
          final int _cursorIndexOfEndedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "endedAt");
          final int _cursorIndexOfRawFilePath = CursorUtil.getColumnIndexOrThrow(_cursor, "rawFilePath");
          final int _cursorIndexOfUploadStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "uploadStatus");
          final int _cursorIndexOfProcessingStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "processingStatus");
          final int _cursorIndexOfRemoteSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "remoteSessionId");
          final SessionEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpUserId;
            if (_cursor.isNull(_cursorIndexOfUserId)) {
              _tmpUserId = null;
            } else {
              _tmpUserId = _cursor.getString(_cursorIndexOfUserId);
            }
            final String _tmpTrackName;
            if (_cursor.isNull(_cursorIndexOfTrackName)) {
              _tmpTrackName = null;
            } else {
              _tmpTrackName = _cursor.getString(_cursorIndexOfTrackName);
            }
            final long _tmpStartedAt;
            _tmpStartedAt = _cursor.getLong(_cursorIndexOfStartedAt);
            final Long _tmpEndedAt;
            if (_cursor.isNull(_cursorIndexOfEndedAt)) {
              _tmpEndedAt = null;
            } else {
              _tmpEndedAt = _cursor.getLong(_cursorIndexOfEndedAt);
            }
            final String _tmpRawFilePath;
            if (_cursor.isNull(_cursorIndexOfRawFilePath)) {
              _tmpRawFilePath = null;
            } else {
              _tmpRawFilePath = _cursor.getString(_cursorIndexOfRawFilePath);
            }
            final String _tmpUploadStatus;
            if (_cursor.isNull(_cursorIndexOfUploadStatus)) {
              _tmpUploadStatus = null;
            } else {
              _tmpUploadStatus = _cursor.getString(_cursorIndexOfUploadStatus);
            }
            final String _tmpProcessingStatus;
            if (_cursor.isNull(_cursorIndexOfProcessingStatus)) {
              _tmpProcessingStatus = null;
            } else {
              _tmpProcessingStatus = _cursor.getString(_cursorIndexOfProcessingStatus);
            }
            final String _tmpRemoteSessionId;
            if (_cursor.isNull(_cursorIndexOfRemoteSessionId)) {
              _tmpRemoteSessionId = null;
            } else {
              _tmpRemoteSessionId = _cursor.getString(_cursorIndexOfRemoteSessionId);
            }
            _result = new SessionEntity(_tmpId,_tmpUserId,_tmpTrackName,_tmpStartedAt,_tmpEndedAt,_tmpRawFilePath,_tmpUploadStatus,_tmpProcessingStatus,_tmpRemoteSessionId);
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
  public Object getSessionByIdSync(final long id,
      final Continuation<? super SessionEntity> $completion) {
    final String _sql = "SELECT * FROM sessions WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<SessionEntity>() {
      @Override
      @Nullable
      public SessionEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfUserId = CursorUtil.getColumnIndexOrThrow(_cursor, "userId");
          final int _cursorIndexOfTrackName = CursorUtil.getColumnIndexOrThrow(_cursor, "trackName");
          final int _cursorIndexOfStartedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "startedAt");
          final int _cursorIndexOfEndedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "endedAt");
          final int _cursorIndexOfRawFilePath = CursorUtil.getColumnIndexOrThrow(_cursor, "rawFilePath");
          final int _cursorIndexOfUploadStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "uploadStatus");
          final int _cursorIndexOfProcessingStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "processingStatus");
          final int _cursorIndexOfRemoteSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "remoteSessionId");
          final SessionEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpUserId;
            if (_cursor.isNull(_cursorIndexOfUserId)) {
              _tmpUserId = null;
            } else {
              _tmpUserId = _cursor.getString(_cursorIndexOfUserId);
            }
            final String _tmpTrackName;
            if (_cursor.isNull(_cursorIndexOfTrackName)) {
              _tmpTrackName = null;
            } else {
              _tmpTrackName = _cursor.getString(_cursorIndexOfTrackName);
            }
            final long _tmpStartedAt;
            _tmpStartedAt = _cursor.getLong(_cursorIndexOfStartedAt);
            final Long _tmpEndedAt;
            if (_cursor.isNull(_cursorIndexOfEndedAt)) {
              _tmpEndedAt = null;
            } else {
              _tmpEndedAt = _cursor.getLong(_cursorIndexOfEndedAt);
            }
            final String _tmpRawFilePath;
            if (_cursor.isNull(_cursorIndexOfRawFilePath)) {
              _tmpRawFilePath = null;
            } else {
              _tmpRawFilePath = _cursor.getString(_cursorIndexOfRawFilePath);
            }
            final String _tmpUploadStatus;
            if (_cursor.isNull(_cursorIndexOfUploadStatus)) {
              _tmpUploadStatus = null;
            } else {
              _tmpUploadStatus = _cursor.getString(_cursorIndexOfUploadStatus);
            }
            final String _tmpProcessingStatus;
            if (_cursor.isNull(_cursorIndexOfProcessingStatus)) {
              _tmpProcessingStatus = null;
            } else {
              _tmpProcessingStatus = _cursor.getString(_cursorIndexOfProcessingStatus);
            }
            final String _tmpRemoteSessionId;
            if (_cursor.isNull(_cursorIndexOfRemoteSessionId)) {
              _tmpRemoteSessionId = null;
            } else {
              _tmpRemoteSessionId = _cursor.getString(_cursorIndexOfRemoteSessionId);
            }
            _result = new SessionEntity(_tmpId,_tmpUserId,_tmpTrackName,_tmpStartedAt,_tmpEndedAt,_tmpRawFilePath,_tmpUploadStatus,_tmpProcessingStatus,_tmpRemoteSessionId);
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
  public Flow<List<SessionEntity>> getAllSessionsForUser(final String userId) {
    final String _sql = "SELECT * FROM sessions WHERE userId = ? ORDER BY startedAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (userId == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, userId);
    }
    return CoroutinesRoom.createFlow(__db, false, new String[] {"sessions"}, new Callable<List<SessionEntity>>() {
      @Override
      @NonNull
      public List<SessionEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfUserId = CursorUtil.getColumnIndexOrThrow(_cursor, "userId");
          final int _cursorIndexOfTrackName = CursorUtil.getColumnIndexOrThrow(_cursor, "trackName");
          final int _cursorIndexOfStartedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "startedAt");
          final int _cursorIndexOfEndedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "endedAt");
          final int _cursorIndexOfRawFilePath = CursorUtil.getColumnIndexOrThrow(_cursor, "rawFilePath");
          final int _cursorIndexOfUploadStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "uploadStatus");
          final int _cursorIndexOfProcessingStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "processingStatus");
          final int _cursorIndexOfRemoteSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "remoteSessionId");
          final List<SessionEntity> _result = new ArrayList<SessionEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final SessionEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpUserId;
            if (_cursor.isNull(_cursorIndexOfUserId)) {
              _tmpUserId = null;
            } else {
              _tmpUserId = _cursor.getString(_cursorIndexOfUserId);
            }
            final String _tmpTrackName;
            if (_cursor.isNull(_cursorIndexOfTrackName)) {
              _tmpTrackName = null;
            } else {
              _tmpTrackName = _cursor.getString(_cursorIndexOfTrackName);
            }
            final long _tmpStartedAt;
            _tmpStartedAt = _cursor.getLong(_cursorIndexOfStartedAt);
            final Long _tmpEndedAt;
            if (_cursor.isNull(_cursorIndexOfEndedAt)) {
              _tmpEndedAt = null;
            } else {
              _tmpEndedAt = _cursor.getLong(_cursorIndexOfEndedAt);
            }
            final String _tmpRawFilePath;
            if (_cursor.isNull(_cursorIndexOfRawFilePath)) {
              _tmpRawFilePath = null;
            } else {
              _tmpRawFilePath = _cursor.getString(_cursorIndexOfRawFilePath);
            }
            final String _tmpUploadStatus;
            if (_cursor.isNull(_cursorIndexOfUploadStatus)) {
              _tmpUploadStatus = null;
            } else {
              _tmpUploadStatus = _cursor.getString(_cursorIndexOfUploadStatus);
            }
            final String _tmpProcessingStatus;
            if (_cursor.isNull(_cursorIndexOfProcessingStatus)) {
              _tmpProcessingStatus = null;
            } else {
              _tmpProcessingStatus = _cursor.getString(_cursorIndexOfProcessingStatus);
            }
            final String _tmpRemoteSessionId;
            if (_cursor.isNull(_cursorIndexOfRemoteSessionId)) {
              _tmpRemoteSessionId = null;
            } else {
              _tmpRemoteSessionId = _cursor.getString(_cursorIndexOfRemoteSessionId);
            }
            _item = new SessionEntity(_tmpId,_tmpUserId,_tmpTrackName,_tmpStartedAt,_tmpEndedAt,_tmpRawFilePath,_tmpUploadStatus,_tmpProcessingStatus,_tmpRemoteSessionId);
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
  public Object getPendingUploadSessions(
      final Continuation<? super List<SessionEntity>> $completion) {
    final String _sql = "SELECT * FROM sessions WHERE uploadStatus = 'PENDING' OR uploadStatus = 'FAILED'";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<SessionEntity>>() {
      @Override
      @NonNull
      public List<SessionEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfUserId = CursorUtil.getColumnIndexOrThrow(_cursor, "userId");
          final int _cursorIndexOfTrackName = CursorUtil.getColumnIndexOrThrow(_cursor, "trackName");
          final int _cursorIndexOfStartedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "startedAt");
          final int _cursorIndexOfEndedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "endedAt");
          final int _cursorIndexOfRawFilePath = CursorUtil.getColumnIndexOrThrow(_cursor, "rawFilePath");
          final int _cursorIndexOfUploadStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "uploadStatus");
          final int _cursorIndexOfProcessingStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "processingStatus");
          final int _cursorIndexOfRemoteSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "remoteSessionId");
          final List<SessionEntity> _result = new ArrayList<SessionEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final SessionEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpUserId;
            if (_cursor.isNull(_cursorIndexOfUserId)) {
              _tmpUserId = null;
            } else {
              _tmpUserId = _cursor.getString(_cursorIndexOfUserId);
            }
            final String _tmpTrackName;
            if (_cursor.isNull(_cursorIndexOfTrackName)) {
              _tmpTrackName = null;
            } else {
              _tmpTrackName = _cursor.getString(_cursorIndexOfTrackName);
            }
            final long _tmpStartedAt;
            _tmpStartedAt = _cursor.getLong(_cursorIndexOfStartedAt);
            final Long _tmpEndedAt;
            if (_cursor.isNull(_cursorIndexOfEndedAt)) {
              _tmpEndedAt = null;
            } else {
              _tmpEndedAt = _cursor.getLong(_cursorIndexOfEndedAt);
            }
            final String _tmpRawFilePath;
            if (_cursor.isNull(_cursorIndexOfRawFilePath)) {
              _tmpRawFilePath = null;
            } else {
              _tmpRawFilePath = _cursor.getString(_cursorIndexOfRawFilePath);
            }
            final String _tmpUploadStatus;
            if (_cursor.isNull(_cursorIndexOfUploadStatus)) {
              _tmpUploadStatus = null;
            } else {
              _tmpUploadStatus = _cursor.getString(_cursorIndexOfUploadStatus);
            }
            final String _tmpProcessingStatus;
            if (_cursor.isNull(_cursorIndexOfProcessingStatus)) {
              _tmpProcessingStatus = null;
            } else {
              _tmpProcessingStatus = _cursor.getString(_cursorIndexOfProcessingStatus);
            }
            final String _tmpRemoteSessionId;
            if (_cursor.isNull(_cursorIndexOfRemoteSessionId)) {
              _tmpRemoteSessionId = null;
            } else {
              _tmpRemoteSessionId = _cursor.getString(_cursorIndexOfRemoteSessionId);
            }
            _item = new SessionEntity(_tmpId,_tmpUserId,_tmpTrackName,_tmpStartedAt,_tmpEndedAt,_tmpRawFilePath,_tmpUploadStatus,_tmpProcessingStatus,_tmpRemoteSessionId);
            _result.add(_item);
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
  public Object getStaleUploadSessions(final long beforeTimestamp,
      final Continuation<? super List<SessionEntity>> $completion) {
    final String _sql = "SELECT * FROM sessions WHERE uploadStatus = 'PENDING' AND startedAt < ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, beforeTimestamp);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<SessionEntity>>() {
      @Override
      @NonNull
      public List<SessionEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfUserId = CursorUtil.getColumnIndexOrThrow(_cursor, "userId");
          final int _cursorIndexOfTrackName = CursorUtil.getColumnIndexOrThrow(_cursor, "trackName");
          final int _cursorIndexOfStartedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "startedAt");
          final int _cursorIndexOfEndedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "endedAt");
          final int _cursorIndexOfRawFilePath = CursorUtil.getColumnIndexOrThrow(_cursor, "rawFilePath");
          final int _cursorIndexOfUploadStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "uploadStatus");
          final int _cursorIndexOfProcessingStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "processingStatus");
          final int _cursorIndexOfRemoteSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "remoteSessionId");
          final List<SessionEntity> _result = new ArrayList<SessionEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final SessionEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpUserId;
            if (_cursor.isNull(_cursorIndexOfUserId)) {
              _tmpUserId = null;
            } else {
              _tmpUserId = _cursor.getString(_cursorIndexOfUserId);
            }
            final String _tmpTrackName;
            if (_cursor.isNull(_cursorIndexOfTrackName)) {
              _tmpTrackName = null;
            } else {
              _tmpTrackName = _cursor.getString(_cursorIndexOfTrackName);
            }
            final long _tmpStartedAt;
            _tmpStartedAt = _cursor.getLong(_cursorIndexOfStartedAt);
            final Long _tmpEndedAt;
            if (_cursor.isNull(_cursorIndexOfEndedAt)) {
              _tmpEndedAt = null;
            } else {
              _tmpEndedAt = _cursor.getLong(_cursorIndexOfEndedAt);
            }
            final String _tmpRawFilePath;
            if (_cursor.isNull(_cursorIndexOfRawFilePath)) {
              _tmpRawFilePath = null;
            } else {
              _tmpRawFilePath = _cursor.getString(_cursorIndexOfRawFilePath);
            }
            final String _tmpUploadStatus;
            if (_cursor.isNull(_cursorIndexOfUploadStatus)) {
              _tmpUploadStatus = null;
            } else {
              _tmpUploadStatus = _cursor.getString(_cursorIndexOfUploadStatus);
            }
            final String _tmpProcessingStatus;
            if (_cursor.isNull(_cursorIndexOfProcessingStatus)) {
              _tmpProcessingStatus = null;
            } else {
              _tmpProcessingStatus = _cursor.getString(_cursorIndexOfProcessingStatus);
            }
            final String _tmpRemoteSessionId;
            if (_cursor.isNull(_cursorIndexOfRemoteSessionId)) {
              _tmpRemoteSessionId = null;
            } else {
              _tmpRemoteSessionId = _cursor.getString(_cursorIndexOfRemoteSessionId);
            }
            _item = new SessionEntity(_tmpId,_tmpUserId,_tmpTrackName,_tmpStartedAt,_tmpEndedAt,_tmpRawFilePath,_tmpUploadStatus,_tmpProcessingStatus,_tmpRemoteSessionId);
            _result.add(_item);
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
