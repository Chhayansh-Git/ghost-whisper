package com.ghostwhisper.data.db;

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
import com.ghostwhisper.data.model.ChannelKey;
import com.ghostwhisper.data.model.ChannelMember;
import com.ghostwhisper.data.model.ContactSource;
import com.ghostwhisper.data.model.KeyDeliveryStatus;
import com.ghostwhisper.data.model.MemberRole;
import java.lang.Class;
import java.lang.Exception;
import java.lang.IllegalArgumentException;
import java.lang.Integer;
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
public final class KeyringDao_Impl implements KeyringDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<ChannelKey> __insertionAdapterOfChannelKey;

  private final EntityInsertionAdapter<ChannelMember> __insertionAdapterOfChannelMember;

  private final SharedSQLiteStatement __preparedStmtOfDeactivate;

  private final SharedSQLiteStatement __preparedStmtOfDelete;

  private final SharedSQLiteStatement __preparedStmtOfUpdateCoverMessage;

  private final SharedSQLiteStatement __preparedStmtOfUpdateLinkedGroup;

  private final SharedSQLiteStatement __preparedStmtOfRenameChannel;

  private final SharedSQLiteStatement __preparedStmtOfUpdateMemberDeliveryStatus;

  private final SharedSQLiteStatement __preparedStmtOfDeleteMember;

  private final SharedSQLiteStatement __preparedStmtOfDeleteMemberByPhone;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAllMembers;

  public KeyringDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfChannelKey = new EntityInsertionAdapter<ChannelKey>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `keyring` (`keyId`,`channelName`,`aesKeyBase64`,`createdAt`,`isActive`,`linkedGroupName`,`coverMessage`,`creatorUid`) VALUES (?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ChannelKey entity) {
        statement.bindString(1, entity.getKeyId());
        statement.bindString(2, entity.getChannelName());
        statement.bindString(3, entity.getAesKeyBase64());
        statement.bindLong(4, entity.getCreatedAt());
        final int _tmp = entity.isActive() ? 1 : 0;
        statement.bindLong(5, _tmp);
        if (entity.getLinkedGroupName() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getLinkedGroupName());
        }
        statement.bindString(7, entity.getCoverMessage());
        if (entity.getCreatorUid() == null) {
          statement.bindNull(8);
        } else {
          statement.bindString(8, entity.getCreatorUid());
        }
      }
    };
    this.__insertionAdapterOfChannelMember = new EntityInsertionAdapter<ChannelMember>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `channel_members` (`id`,`channelKeyId`,`contactName`,`phoneNumber`,`role`,`hasKey`,`contactSource`,`keyDeliveryStatus`,`addedAt`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ChannelMember entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getChannelKeyId());
        statement.bindString(3, entity.getContactName());
        statement.bindString(4, entity.getPhoneNumber());
        statement.bindString(5, __MemberRole_enumToString(entity.getRole()));
        final int _tmp = entity.getHasKey() ? 1 : 0;
        statement.bindLong(6, _tmp);
        statement.bindString(7, __ContactSource_enumToString(entity.getContactSource()));
        statement.bindString(8, __KeyDeliveryStatus_enumToString(entity.getKeyDeliveryStatus()));
        statement.bindLong(9, entity.getAddedAt());
      }
    };
    this.__preparedStmtOfDeactivate = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE keyring SET isActive = 0 WHERE keyId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDelete = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM keyring WHERE keyId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateCoverMessage = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE keyring SET coverMessage = ? WHERE keyId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateLinkedGroup = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE keyring SET linkedGroupName = ? WHERE keyId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfRenameChannel = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE keyring SET channelName = ? WHERE keyId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateMemberDeliveryStatus = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE channel_members SET keyDeliveryStatus = ?, hasKey = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteMember = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM channel_members WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteMemberByPhone = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM channel_members WHERE channelKeyId = ? AND phoneNumber = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAllMembers = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM channel_members WHERE channelKeyId = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final ChannelKey channelKey, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfChannelKey.insert(channelKey);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertMember(final ChannelMember member,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfChannelMember.insert(member);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertMembers(final List<ChannelMember> members,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfChannelMember.insert(members);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deactivate(final String keyId, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeactivate.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, keyId);
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
          __preparedStmtOfDeactivate.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final String keyId, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDelete.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, keyId);
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
          __preparedStmtOfDelete.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object updateCoverMessage(final String keyId, final String coverMessage,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateCoverMessage.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, coverMessage);
        _argIndex = 2;
        _stmt.bindString(_argIndex, keyId);
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
          __preparedStmtOfUpdateCoverMessage.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object updateLinkedGroup(final String keyId, final String groupName,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateLinkedGroup.acquire();
        int _argIndex = 1;
        if (groupName == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, groupName);
        }
        _argIndex = 2;
        _stmt.bindString(_argIndex, keyId);
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
          __preparedStmtOfUpdateLinkedGroup.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object renameChannel(final String keyId, final String newName,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfRenameChannel.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, newName);
        _argIndex = 2;
        _stmt.bindString(_argIndex, keyId);
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
          __preparedStmtOfRenameChannel.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object updateMemberDeliveryStatus(final long memberId, final KeyDeliveryStatus status,
      final boolean hasKey, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateMemberDeliveryStatus.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, __KeyDeliveryStatus_enumToString(status));
        _argIndex = 2;
        final int _tmp = hasKey ? 1 : 0;
        _stmt.bindLong(_argIndex, _tmp);
        _argIndex = 3;
        _stmt.bindLong(_argIndex, memberId);
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
          __preparedStmtOfUpdateMemberDeliveryStatus.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteMember(final long memberId, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteMember.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, memberId);
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
          __preparedStmtOfDeleteMember.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteMemberByPhone(final String channelKeyId, final String phoneNumber,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteMemberByPhone.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, channelKeyId);
        _argIndex = 2;
        _stmt.bindString(_argIndex, phoneNumber);
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
          __preparedStmtOfDeleteMemberByPhone.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteAllMembers(final String channelKeyId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAllMembers.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, channelKeyId);
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
          __preparedStmtOfDeleteAllMembers.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<ChannelKey>> getAllActiveKeys() {
    final String _sql = "SELECT * FROM keyring WHERE isActive = 1 ORDER BY createdAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"keyring"}, new Callable<List<ChannelKey>>() {
      @Override
      @NonNull
      public List<ChannelKey> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfKeyId = CursorUtil.getColumnIndexOrThrow(_cursor, "keyId");
          final int _cursorIndexOfChannelName = CursorUtil.getColumnIndexOrThrow(_cursor, "channelName");
          final int _cursorIndexOfAesKeyBase64 = CursorUtil.getColumnIndexOrThrow(_cursor, "aesKeyBase64");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "isActive");
          final int _cursorIndexOfLinkedGroupName = CursorUtil.getColumnIndexOrThrow(_cursor, "linkedGroupName");
          final int _cursorIndexOfCoverMessage = CursorUtil.getColumnIndexOrThrow(_cursor, "coverMessage");
          final int _cursorIndexOfCreatorUid = CursorUtil.getColumnIndexOrThrow(_cursor, "creatorUid");
          final List<ChannelKey> _result = new ArrayList<ChannelKey>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ChannelKey _item;
            final String _tmpKeyId;
            _tmpKeyId = _cursor.getString(_cursorIndexOfKeyId);
            final String _tmpChannelName;
            _tmpChannelName = _cursor.getString(_cursorIndexOfChannelName);
            final String _tmpAesKeyBase64;
            _tmpAesKeyBase64 = _cursor.getString(_cursorIndexOfAesKeyBase64);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final boolean _tmpIsActive;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp != 0;
            final String _tmpLinkedGroupName;
            if (_cursor.isNull(_cursorIndexOfLinkedGroupName)) {
              _tmpLinkedGroupName = null;
            } else {
              _tmpLinkedGroupName = _cursor.getString(_cursorIndexOfLinkedGroupName);
            }
            final String _tmpCoverMessage;
            _tmpCoverMessage = _cursor.getString(_cursorIndexOfCoverMessage);
            final String _tmpCreatorUid;
            if (_cursor.isNull(_cursorIndexOfCreatorUid)) {
              _tmpCreatorUid = null;
            } else {
              _tmpCreatorUid = _cursor.getString(_cursorIndexOfCreatorUid);
            }
            _item = new ChannelKey(_tmpKeyId,_tmpChannelName,_tmpAesKeyBase64,_tmpCreatedAt,_tmpIsActive,_tmpLinkedGroupName,_tmpCoverMessage,_tmpCreatorUid);
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
  public Flow<List<ChannelKey>> getAllKeys() {
    final String _sql = "SELECT * FROM keyring ORDER BY createdAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"keyring"}, new Callable<List<ChannelKey>>() {
      @Override
      @NonNull
      public List<ChannelKey> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfKeyId = CursorUtil.getColumnIndexOrThrow(_cursor, "keyId");
          final int _cursorIndexOfChannelName = CursorUtil.getColumnIndexOrThrow(_cursor, "channelName");
          final int _cursorIndexOfAesKeyBase64 = CursorUtil.getColumnIndexOrThrow(_cursor, "aesKeyBase64");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "isActive");
          final int _cursorIndexOfLinkedGroupName = CursorUtil.getColumnIndexOrThrow(_cursor, "linkedGroupName");
          final int _cursorIndexOfCoverMessage = CursorUtil.getColumnIndexOrThrow(_cursor, "coverMessage");
          final int _cursorIndexOfCreatorUid = CursorUtil.getColumnIndexOrThrow(_cursor, "creatorUid");
          final List<ChannelKey> _result = new ArrayList<ChannelKey>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ChannelKey _item;
            final String _tmpKeyId;
            _tmpKeyId = _cursor.getString(_cursorIndexOfKeyId);
            final String _tmpChannelName;
            _tmpChannelName = _cursor.getString(_cursorIndexOfChannelName);
            final String _tmpAesKeyBase64;
            _tmpAesKeyBase64 = _cursor.getString(_cursorIndexOfAesKeyBase64);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final boolean _tmpIsActive;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp != 0;
            final String _tmpLinkedGroupName;
            if (_cursor.isNull(_cursorIndexOfLinkedGroupName)) {
              _tmpLinkedGroupName = null;
            } else {
              _tmpLinkedGroupName = _cursor.getString(_cursorIndexOfLinkedGroupName);
            }
            final String _tmpCoverMessage;
            _tmpCoverMessage = _cursor.getString(_cursorIndexOfCoverMessage);
            final String _tmpCreatorUid;
            if (_cursor.isNull(_cursorIndexOfCreatorUid)) {
              _tmpCreatorUid = null;
            } else {
              _tmpCreatorUid = _cursor.getString(_cursorIndexOfCreatorUid);
            }
            _item = new ChannelKey(_tmpKeyId,_tmpChannelName,_tmpAesKeyBase64,_tmpCreatedAt,_tmpIsActive,_tmpLinkedGroupName,_tmpCoverMessage,_tmpCreatorUid);
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
  public Object getByKeyId(final String keyId, final Continuation<? super ChannelKey> $completion) {
    final String _sql = "SELECT * FROM keyring WHERE keyId = ? AND isActive = 1 LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, keyId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<ChannelKey>() {
      @Override
      @Nullable
      public ChannelKey call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfKeyId = CursorUtil.getColumnIndexOrThrow(_cursor, "keyId");
          final int _cursorIndexOfChannelName = CursorUtil.getColumnIndexOrThrow(_cursor, "channelName");
          final int _cursorIndexOfAesKeyBase64 = CursorUtil.getColumnIndexOrThrow(_cursor, "aesKeyBase64");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "isActive");
          final int _cursorIndexOfLinkedGroupName = CursorUtil.getColumnIndexOrThrow(_cursor, "linkedGroupName");
          final int _cursorIndexOfCoverMessage = CursorUtil.getColumnIndexOrThrow(_cursor, "coverMessage");
          final int _cursorIndexOfCreatorUid = CursorUtil.getColumnIndexOrThrow(_cursor, "creatorUid");
          final ChannelKey _result;
          if (_cursor.moveToFirst()) {
            final String _tmpKeyId;
            _tmpKeyId = _cursor.getString(_cursorIndexOfKeyId);
            final String _tmpChannelName;
            _tmpChannelName = _cursor.getString(_cursorIndexOfChannelName);
            final String _tmpAesKeyBase64;
            _tmpAesKeyBase64 = _cursor.getString(_cursorIndexOfAesKeyBase64);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final boolean _tmpIsActive;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp != 0;
            final String _tmpLinkedGroupName;
            if (_cursor.isNull(_cursorIndexOfLinkedGroupName)) {
              _tmpLinkedGroupName = null;
            } else {
              _tmpLinkedGroupName = _cursor.getString(_cursorIndexOfLinkedGroupName);
            }
            final String _tmpCoverMessage;
            _tmpCoverMessage = _cursor.getString(_cursorIndexOfCoverMessage);
            final String _tmpCreatorUid;
            if (_cursor.isNull(_cursorIndexOfCreatorUid)) {
              _tmpCreatorUid = null;
            } else {
              _tmpCreatorUid = _cursor.getString(_cursorIndexOfCreatorUid);
            }
            _result = new ChannelKey(_tmpKeyId,_tmpChannelName,_tmpAesKeyBase64,_tmpCreatedAt,_tmpIsActive,_tmpLinkedGroupName,_tmpCoverMessage,_tmpCreatorUid);
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
  public Object getByChannelName(final String channelName,
      final Continuation<? super ChannelKey> $completion) {
    final String _sql = "SELECT * FROM keyring WHERE channelName = ? AND isActive = 1 LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, channelName);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<ChannelKey>() {
      @Override
      @Nullable
      public ChannelKey call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfKeyId = CursorUtil.getColumnIndexOrThrow(_cursor, "keyId");
          final int _cursorIndexOfChannelName = CursorUtil.getColumnIndexOrThrow(_cursor, "channelName");
          final int _cursorIndexOfAesKeyBase64 = CursorUtil.getColumnIndexOrThrow(_cursor, "aesKeyBase64");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "isActive");
          final int _cursorIndexOfLinkedGroupName = CursorUtil.getColumnIndexOrThrow(_cursor, "linkedGroupName");
          final int _cursorIndexOfCoverMessage = CursorUtil.getColumnIndexOrThrow(_cursor, "coverMessage");
          final int _cursorIndexOfCreatorUid = CursorUtil.getColumnIndexOrThrow(_cursor, "creatorUid");
          final ChannelKey _result;
          if (_cursor.moveToFirst()) {
            final String _tmpKeyId;
            _tmpKeyId = _cursor.getString(_cursorIndexOfKeyId);
            final String _tmpChannelName;
            _tmpChannelName = _cursor.getString(_cursorIndexOfChannelName);
            final String _tmpAesKeyBase64;
            _tmpAesKeyBase64 = _cursor.getString(_cursorIndexOfAesKeyBase64);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final boolean _tmpIsActive;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp != 0;
            final String _tmpLinkedGroupName;
            if (_cursor.isNull(_cursorIndexOfLinkedGroupName)) {
              _tmpLinkedGroupName = null;
            } else {
              _tmpLinkedGroupName = _cursor.getString(_cursorIndexOfLinkedGroupName);
            }
            final String _tmpCoverMessage;
            _tmpCoverMessage = _cursor.getString(_cursorIndexOfCoverMessage);
            final String _tmpCreatorUid;
            if (_cursor.isNull(_cursorIndexOfCreatorUid)) {
              _tmpCreatorUid = null;
            } else {
              _tmpCreatorUid = _cursor.getString(_cursorIndexOfCreatorUid);
            }
            _result = new ChannelKey(_tmpKeyId,_tmpChannelName,_tmpAesKeyBase64,_tmpCreatedAt,_tmpIsActive,_tmpLinkedGroupName,_tmpCoverMessage,_tmpCreatorUid);
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
  public Object getAllActiveKeysList(final Continuation<? super List<ChannelKey>> $completion) {
    final String _sql = "SELECT * FROM keyring WHERE isActive = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<ChannelKey>>() {
      @Override
      @NonNull
      public List<ChannelKey> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfKeyId = CursorUtil.getColumnIndexOrThrow(_cursor, "keyId");
          final int _cursorIndexOfChannelName = CursorUtil.getColumnIndexOrThrow(_cursor, "channelName");
          final int _cursorIndexOfAesKeyBase64 = CursorUtil.getColumnIndexOrThrow(_cursor, "aesKeyBase64");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "isActive");
          final int _cursorIndexOfLinkedGroupName = CursorUtil.getColumnIndexOrThrow(_cursor, "linkedGroupName");
          final int _cursorIndexOfCoverMessage = CursorUtil.getColumnIndexOrThrow(_cursor, "coverMessage");
          final int _cursorIndexOfCreatorUid = CursorUtil.getColumnIndexOrThrow(_cursor, "creatorUid");
          final List<ChannelKey> _result = new ArrayList<ChannelKey>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ChannelKey _item;
            final String _tmpKeyId;
            _tmpKeyId = _cursor.getString(_cursorIndexOfKeyId);
            final String _tmpChannelName;
            _tmpChannelName = _cursor.getString(_cursorIndexOfChannelName);
            final String _tmpAesKeyBase64;
            _tmpAesKeyBase64 = _cursor.getString(_cursorIndexOfAesKeyBase64);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final boolean _tmpIsActive;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp != 0;
            final String _tmpLinkedGroupName;
            if (_cursor.isNull(_cursorIndexOfLinkedGroupName)) {
              _tmpLinkedGroupName = null;
            } else {
              _tmpLinkedGroupName = _cursor.getString(_cursorIndexOfLinkedGroupName);
            }
            final String _tmpCoverMessage;
            _tmpCoverMessage = _cursor.getString(_cursorIndexOfCoverMessage);
            final String _tmpCreatorUid;
            if (_cursor.isNull(_cursorIndexOfCreatorUid)) {
              _tmpCreatorUid = null;
            } else {
              _tmpCreatorUid = _cursor.getString(_cursorIndexOfCreatorUid);
            }
            _item = new ChannelKey(_tmpKeyId,_tmpChannelName,_tmpAesKeyBase64,_tmpCreatedAt,_tmpIsActive,_tmpLinkedGroupName,_tmpCoverMessage,_tmpCreatorUid);
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
  public Object getByGroupName(final String groupName,
      final Continuation<? super List<ChannelKey>> $completion) {
    final String _sql = "SELECT * FROM keyring WHERE linkedGroupName = ? AND isActive = 1 ORDER BY createdAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, groupName);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<ChannelKey>>() {
      @Override
      @NonNull
      public List<ChannelKey> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfKeyId = CursorUtil.getColumnIndexOrThrow(_cursor, "keyId");
          final int _cursorIndexOfChannelName = CursorUtil.getColumnIndexOrThrow(_cursor, "channelName");
          final int _cursorIndexOfAesKeyBase64 = CursorUtil.getColumnIndexOrThrow(_cursor, "aesKeyBase64");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "isActive");
          final int _cursorIndexOfLinkedGroupName = CursorUtil.getColumnIndexOrThrow(_cursor, "linkedGroupName");
          final int _cursorIndexOfCoverMessage = CursorUtil.getColumnIndexOrThrow(_cursor, "coverMessage");
          final int _cursorIndexOfCreatorUid = CursorUtil.getColumnIndexOrThrow(_cursor, "creatorUid");
          final List<ChannelKey> _result = new ArrayList<ChannelKey>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ChannelKey _item;
            final String _tmpKeyId;
            _tmpKeyId = _cursor.getString(_cursorIndexOfKeyId);
            final String _tmpChannelName;
            _tmpChannelName = _cursor.getString(_cursorIndexOfChannelName);
            final String _tmpAesKeyBase64;
            _tmpAesKeyBase64 = _cursor.getString(_cursorIndexOfAesKeyBase64);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final boolean _tmpIsActive;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp != 0;
            final String _tmpLinkedGroupName;
            if (_cursor.isNull(_cursorIndexOfLinkedGroupName)) {
              _tmpLinkedGroupName = null;
            } else {
              _tmpLinkedGroupName = _cursor.getString(_cursorIndexOfLinkedGroupName);
            }
            final String _tmpCoverMessage;
            _tmpCoverMessage = _cursor.getString(_cursorIndexOfCoverMessage);
            final String _tmpCreatorUid;
            if (_cursor.isNull(_cursorIndexOfCreatorUid)) {
              _tmpCreatorUid = null;
            } else {
              _tmpCreatorUid = _cursor.getString(_cursorIndexOfCreatorUid);
            }
            _item = new ChannelKey(_tmpKeyId,_tmpChannelName,_tmpAesKeyBase64,_tmpCreatedAt,_tmpIsActive,_tmpLinkedGroupName,_tmpCoverMessage,_tmpCreatorUid);
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
  public Flow<Integer> getActiveChannelCount() {
    final String _sql = "SELECT COUNT(*) FROM keyring WHERE isActive = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"keyring"}, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
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
  public Flow<List<ChannelMember>> getMembersForChannel(final String channelKeyId) {
    final String _sql = "SELECT * FROM channel_members WHERE channelKeyId = ? ORDER BY contactName ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, channelKeyId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"channel_members"}, new Callable<List<ChannelMember>>() {
      @Override
      @NonNull
      public List<ChannelMember> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfChannelKeyId = CursorUtil.getColumnIndexOrThrow(_cursor, "channelKeyId");
          final int _cursorIndexOfContactName = CursorUtil.getColumnIndexOrThrow(_cursor, "contactName");
          final int _cursorIndexOfPhoneNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "phoneNumber");
          final int _cursorIndexOfRole = CursorUtil.getColumnIndexOrThrow(_cursor, "role");
          final int _cursorIndexOfHasKey = CursorUtil.getColumnIndexOrThrow(_cursor, "hasKey");
          final int _cursorIndexOfContactSource = CursorUtil.getColumnIndexOrThrow(_cursor, "contactSource");
          final int _cursorIndexOfKeyDeliveryStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "keyDeliveryStatus");
          final int _cursorIndexOfAddedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "addedAt");
          final List<ChannelMember> _result = new ArrayList<ChannelMember>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ChannelMember _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpChannelKeyId;
            _tmpChannelKeyId = _cursor.getString(_cursorIndexOfChannelKeyId);
            final String _tmpContactName;
            _tmpContactName = _cursor.getString(_cursorIndexOfContactName);
            final String _tmpPhoneNumber;
            _tmpPhoneNumber = _cursor.getString(_cursorIndexOfPhoneNumber);
            final MemberRole _tmpRole;
            _tmpRole = __MemberRole_stringToEnum(_cursor.getString(_cursorIndexOfRole));
            final boolean _tmpHasKey;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfHasKey);
            _tmpHasKey = _tmp != 0;
            final ContactSource _tmpContactSource;
            _tmpContactSource = __ContactSource_stringToEnum(_cursor.getString(_cursorIndexOfContactSource));
            final KeyDeliveryStatus _tmpKeyDeliveryStatus;
            _tmpKeyDeliveryStatus = __KeyDeliveryStatus_stringToEnum(_cursor.getString(_cursorIndexOfKeyDeliveryStatus));
            final long _tmpAddedAt;
            _tmpAddedAt = _cursor.getLong(_cursorIndexOfAddedAt);
            _item = new ChannelMember(_tmpId,_tmpChannelKeyId,_tmpContactName,_tmpPhoneNumber,_tmpRole,_tmpHasKey,_tmpContactSource,_tmpKeyDeliveryStatus,_tmpAddedAt);
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
  public Object getMembersForChannelList(final String channelKeyId,
      final Continuation<? super List<ChannelMember>> $completion) {
    final String _sql = "SELECT * FROM channel_members WHERE channelKeyId = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, channelKeyId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<ChannelMember>>() {
      @Override
      @NonNull
      public List<ChannelMember> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfChannelKeyId = CursorUtil.getColumnIndexOrThrow(_cursor, "channelKeyId");
          final int _cursorIndexOfContactName = CursorUtil.getColumnIndexOrThrow(_cursor, "contactName");
          final int _cursorIndexOfPhoneNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "phoneNumber");
          final int _cursorIndexOfRole = CursorUtil.getColumnIndexOrThrow(_cursor, "role");
          final int _cursorIndexOfHasKey = CursorUtil.getColumnIndexOrThrow(_cursor, "hasKey");
          final int _cursorIndexOfContactSource = CursorUtil.getColumnIndexOrThrow(_cursor, "contactSource");
          final int _cursorIndexOfKeyDeliveryStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "keyDeliveryStatus");
          final int _cursorIndexOfAddedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "addedAt");
          final List<ChannelMember> _result = new ArrayList<ChannelMember>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ChannelMember _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpChannelKeyId;
            _tmpChannelKeyId = _cursor.getString(_cursorIndexOfChannelKeyId);
            final String _tmpContactName;
            _tmpContactName = _cursor.getString(_cursorIndexOfContactName);
            final String _tmpPhoneNumber;
            _tmpPhoneNumber = _cursor.getString(_cursorIndexOfPhoneNumber);
            final MemberRole _tmpRole;
            _tmpRole = __MemberRole_stringToEnum(_cursor.getString(_cursorIndexOfRole));
            final boolean _tmpHasKey;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfHasKey);
            _tmpHasKey = _tmp != 0;
            final ContactSource _tmpContactSource;
            _tmpContactSource = __ContactSource_stringToEnum(_cursor.getString(_cursorIndexOfContactSource));
            final KeyDeliveryStatus _tmpKeyDeliveryStatus;
            _tmpKeyDeliveryStatus = __KeyDeliveryStatus_stringToEnum(_cursor.getString(_cursorIndexOfKeyDeliveryStatus));
            final long _tmpAddedAt;
            _tmpAddedAt = _cursor.getLong(_cursorIndexOfAddedAt);
            _item = new ChannelMember(_tmpId,_tmpChannelKeyId,_tmpContactName,_tmpPhoneNumber,_tmpRole,_tmpHasKey,_tmpContactSource,_tmpKeyDeliveryStatus,_tmpAddedAt);
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
  public Object getPendingMembers(final String channelKeyId,
      final Continuation<? super List<ChannelMember>> $completion) {
    final String _sql = "SELECT * FROM channel_members WHERE channelKeyId = ? AND hasKey = 0";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, channelKeyId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<ChannelMember>>() {
      @Override
      @NonNull
      public List<ChannelMember> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfChannelKeyId = CursorUtil.getColumnIndexOrThrow(_cursor, "channelKeyId");
          final int _cursorIndexOfContactName = CursorUtil.getColumnIndexOrThrow(_cursor, "contactName");
          final int _cursorIndexOfPhoneNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "phoneNumber");
          final int _cursorIndexOfRole = CursorUtil.getColumnIndexOrThrow(_cursor, "role");
          final int _cursorIndexOfHasKey = CursorUtil.getColumnIndexOrThrow(_cursor, "hasKey");
          final int _cursorIndexOfContactSource = CursorUtil.getColumnIndexOrThrow(_cursor, "contactSource");
          final int _cursorIndexOfKeyDeliveryStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "keyDeliveryStatus");
          final int _cursorIndexOfAddedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "addedAt");
          final List<ChannelMember> _result = new ArrayList<ChannelMember>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ChannelMember _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpChannelKeyId;
            _tmpChannelKeyId = _cursor.getString(_cursorIndexOfChannelKeyId);
            final String _tmpContactName;
            _tmpContactName = _cursor.getString(_cursorIndexOfContactName);
            final String _tmpPhoneNumber;
            _tmpPhoneNumber = _cursor.getString(_cursorIndexOfPhoneNumber);
            final MemberRole _tmpRole;
            _tmpRole = __MemberRole_stringToEnum(_cursor.getString(_cursorIndexOfRole));
            final boolean _tmpHasKey;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfHasKey);
            _tmpHasKey = _tmp != 0;
            final ContactSource _tmpContactSource;
            _tmpContactSource = __ContactSource_stringToEnum(_cursor.getString(_cursorIndexOfContactSource));
            final KeyDeliveryStatus _tmpKeyDeliveryStatus;
            _tmpKeyDeliveryStatus = __KeyDeliveryStatus_stringToEnum(_cursor.getString(_cursorIndexOfKeyDeliveryStatus));
            final long _tmpAddedAt;
            _tmpAddedAt = _cursor.getLong(_cursorIndexOfAddedAt);
            _item = new ChannelMember(_tmpId,_tmpChannelKeyId,_tmpContactName,_tmpPhoneNumber,_tmpRole,_tmpHasKey,_tmpContactSource,_tmpKeyDeliveryStatus,_tmpAddedAt);
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
  public Flow<Integer> getMemberCount(final String channelKeyId) {
    final String _sql = "SELECT COUNT(*) FROM channel_members WHERE channelKeyId = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, channelKeyId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"channel_members"}, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }

  private String __MemberRole_enumToString(@NonNull final MemberRole _value) {
    switch (_value) {
      case ADMIN: return "ADMIN";
      case MEMBER: return "MEMBER";
      default: throw new IllegalArgumentException("Can't convert enum to string, unknown enum value: " + _value);
    }
  }

  private String __ContactSource_enumToString(@NonNull final ContactSource _value) {
    switch (_value) {
      case SCANNED: return "SCANNED";
      case CONTACTS: return "CONTACTS";
      case MANUAL: return "MANUAL";
      default: throw new IllegalArgumentException("Can't convert enum to string, unknown enum value: " + _value);
    }
  }

  private String __KeyDeliveryStatus_enumToString(@NonNull final KeyDeliveryStatus _value) {
    switch (_value) {
      case PENDING: return "PENDING";
      case SENT_WHATSAPP: return "SENT_WHATSAPP";
      case SENT_SMS: return "SENT_SMS";
      case DELIVERED: return "DELIVERED";
      case FAILED: return "FAILED";
      default: throw new IllegalArgumentException("Can't convert enum to string, unknown enum value: " + _value);
    }
  }

  private MemberRole __MemberRole_stringToEnum(@NonNull final String _value) {
    switch (_value) {
      case "ADMIN": return MemberRole.ADMIN;
      case "MEMBER": return MemberRole.MEMBER;
      default: throw new IllegalArgumentException("Can't convert value to enum, unknown value: " + _value);
    }
  }

  private ContactSource __ContactSource_stringToEnum(@NonNull final String _value) {
    switch (_value) {
      case "SCANNED": return ContactSource.SCANNED;
      case "CONTACTS": return ContactSource.CONTACTS;
      case "MANUAL": return ContactSource.MANUAL;
      default: throw new IllegalArgumentException("Can't convert value to enum, unknown value: " + _value);
    }
  }

  private KeyDeliveryStatus __KeyDeliveryStatus_stringToEnum(@NonNull final String _value) {
    switch (_value) {
      case "PENDING": return KeyDeliveryStatus.PENDING;
      case "SENT_WHATSAPP": return KeyDeliveryStatus.SENT_WHATSAPP;
      case "SENT_SMS": return KeyDeliveryStatus.SENT_SMS;
      case "DELIVERED": return KeyDeliveryStatus.DELIVERED;
      case "FAILED": return KeyDeliveryStatus.FAILED;
      default: throw new IllegalArgumentException("Can't convert value to enum, unknown value: " + _value);
    }
  }
}
