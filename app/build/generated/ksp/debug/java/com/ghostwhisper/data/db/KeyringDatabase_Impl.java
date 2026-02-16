package com.ghostwhisper.data.db;

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
public final class KeyringDatabase_Impl extends KeyringDatabase {
  private volatile KeyringDao _keyringDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(2) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `keyring` (`keyId` TEXT NOT NULL, `channelName` TEXT NOT NULL, `aesKeyBase64` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, `linkedGroupName` TEXT, `coverMessage` TEXT NOT NULL, `creatorUid` TEXT, PRIMARY KEY(`keyId`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `channel_members` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `channelKeyId` TEXT NOT NULL, `contactName` TEXT NOT NULL, `phoneNumber` TEXT NOT NULL, `role` TEXT NOT NULL, `hasKey` INTEGER NOT NULL, `contactSource` TEXT NOT NULL, `keyDeliveryStatus` TEXT NOT NULL, `addedAt` INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_channel_members_channelKeyId` ON `channel_members` (`channelKeyId`)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_channel_members_channelKeyId_phoneNumber` ON `channel_members` (`channelKeyId`, `phoneNumber`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '602596e219d2e07d7ee89fb27fbba586')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `keyring`");
        db.execSQL("DROP TABLE IF EXISTS `channel_members`");
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
        final HashMap<String, TableInfo.Column> _columnsKeyring = new HashMap<String, TableInfo.Column>(8);
        _columnsKeyring.put("keyId", new TableInfo.Column("keyId", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsKeyring.put("channelName", new TableInfo.Column("channelName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsKeyring.put("aesKeyBase64", new TableInfo.Column("aesKeyBase64", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsKeyring.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsKeyring.put("isActive", new TableInfo.Column("isActive", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsKeyring.put("linkedGroupName", new TableInfo.Column("linkedGroupName", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsKeyring.put("coverMessage", new TableInfo.Column("coverMessage", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsKeyring.put("creatorUid", new TableInfo.Column("creatorUid", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysKeyring = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesKeyring = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoKeyring = new TableInfo("keyring", _columnsKeyring, _foreignKeysKeyring, _indicesKeyring);
        final TableInfo _existingKeyring = TableInfo.read(db, "keyring");
        if (!_infoKeyring.equals(_existingKeyring)) {
          return new RoomOpenHelper.ValidationResult(false, "keyring(com.ghostwhisper.data.model.ChannelKey).\n"
                  + " Expected:\n" + _infoKeyring + "\n"
                  + " Found:\n" + _existingKeyring);
        }
        final HashMap<String, TableInfo.Column> _columnsChannelMembers = new HashMap<String, TableInfo.Column>(9);
        _columnsChannelMembers.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsChannelMembers.put("channelKeyId", new TableInfo.Column("channelKeyId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsChannelMembers.put("contactName", new TableInfo.Column("contactName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsChannelMembers.put("phoneNumber", new TableInfo.Column("phoneNumber", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsChannelMembers.put("role", new TableInfo.Column("role", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsChannelMembers.put("hasKey", new TableInfo.Column("hasKey", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsChannelMembers.put("contactSource", new TableInfo.Column("contactSource", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsChannelMembers.put("keyDeliveryStatus", new TableInfo.Column("keyDeliveryStatus", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsChannelMembers.put("addedAt", new TableInfo.Column("addedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysChannelMembers = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesChannelMembers = new HashSet<TableInfo.Index>(2);
        _indicesChannelMembers.add(new TableInfo.Index("index_channel_members_channelKeyId", false, Arrays.asList("channelKeyId"), Arrays.asList("ASC")));
        _indicesChannelMembers.add(new TableInfo.Index("index_channel_members_channelKeyId_phoneNumber", true, Arrays.asList("channelKeyId", "phoneNumber"), Arrays.asList("ASC", "ASC")));
        final TableInfo _infoChannelMembers = new TableInfo("channel_members", _columnsChannelMembers, _foreignKeysChannelMembers, _indicesChannelMembers);
        final TableInfo _existingChannelMembers = TableInfo.read(db, "channel_members");
        if (!_infoChannelMembers.equals(_existingChannelMembers)) {
          return new RoomOpenHelper.ValidationResult(false, "channel_members(com.ghostwhisper.data.model.ChannelMember).\n"
                  + " Expected:\n" + _infoChannelMembers + "\n"
                  + " Found:\n" + _existingChannelMembers);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "602596e219d2e07d7ee89fb27fbba586", "3a23375131d51fb8a6e59eb6998a897d");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "keyring","channel_members");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `keyring`");
      _db.execSQL("DELETE FROM `channel_members`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
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
    _typeConvertersMap.put(KeyringDao.class, KeyringDao_Impl.getRequiredConverters());
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
  public KeyringDao keyringDao() {
    if (_keyringDao != null) {
      return _keyringDao;
    } else {
      synchronized(this) {
        if(_keyringDao == null) {
          _keyringDao = new KeyringDao_Impl(this);
        }
        return _keyringDao;
      }
    }
  }
}
