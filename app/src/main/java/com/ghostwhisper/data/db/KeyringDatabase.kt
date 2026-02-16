package com.ghostwhisper.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ghostwhisper.data.model.ChannelKey
import com.ghostwhisper.data.model.ChannelMember

/**
 * Room database for Ghost Whisper's keyring and channel members.
 *
 * Version 2: Added ChannelMember entity and new fields on ChannelKey.
 */
@Database(entities = [ChannelKey::class, ChannelMember::class], version = 2, exportSchema = false)
abstract class KeyringDatabase : RoomDatabase() {

    abstract fun keyringDao(): KeyringDao

    companion object {
        @Volatile private var INSTANCE: KeyringDatabase? = null

        /** Migration from v1 to v2: add group channel fields + members table. */
        private val MIGRATION_1_2 =
                object : Migration(1, 2) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        // Add new columns to keyring table
                        db.execSQL(
                                "ALTER TABLE keyring ADD COLUMN linkedGroupName TEXT DEFAULT NULL"
                        )
                        db.execSQL(
                                "ALTER TABLE keyring ADD COLUMN coverMessage TEXT NOT NULL DEFAULT 'Noted 👍'"
                        )
                        db.execSQL("ALTER TABLE keyring ADD COLUMN creatorUid TEXT DEFAULT NULL")

                        // Create channel_members table
                        db.execSQL(
                                """
                            CREATE TABLE IF NOT EXISTS channel_members (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                channelKeyId TEXT NOT NULL,
                                contactName TEXT NOT NULL,
                                phoneNumber TEXT NOT NULL,
                                role TEXT NOT NULL DEFAULT 'MEMBER',
                                hasKey INTEGER NOT NULL DEFAULT 0,
                                contactSource TEXT NOT NULL DEFAULT 'MANUAL',
                                keyDeliveryStatus TEXT NOT NULL DEFAULT 'PENDING',
                                addedAt INTEGER NOT NULL DEFAULT 0
                            )
                        """.trimIndent()
                        )

                        // Create indices for channel_members
                        db.execSQL(
                                "CREATE INDEX IF NOT EXISTS index_channel_members_channelKeyId ON channel_members (channelKeyId)"
                        )
                        db.execSQL(
                                "CREATE UNIQUE INDEX IF NOT EXISTS index_channel_members_channelKeyId_phoneNumber ON channel_members (channelKeyId, phoneNumber)"
                        )
                    }
                }

        /** Get the singleton database instance. Thread-safe via double-checked locking. */
        fun getInstance(context: Context): KeyringDatabase {
            return INSTANCE
                    ?: synchronized(this) {
                        INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
                    }
        }

        private fun buildDatabase(context: Context): KeyringDatabase {
            return Room.databaseBuilder(
                            context.applicationContext,
                            KeyringDatabase::class.java,
                            "ghost_whisper_keyring.db"
                    )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
        }
    }
}
