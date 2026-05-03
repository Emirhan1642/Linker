package com.linker.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.linker.app.data.local.dao.*
import com.linker.app.data.local.entity.*

@Database(
    entities = [
        UserEntity::class, LinkEntity::class, StoryEntity::class,
        NoteEntity::class, ChatEntity::class, MessageEntity::class,
        MessageQueueEntity::class, CommentEntity::class,
        MediaCacheEntity::class, NotificationEntity::class,
        BleNodeEntity::class, MessageIdCacheEntity::class,
        SignalIdentityEntity::class, SignalSessionEntity::class,
        SignalPreKeyEntity::class, SignalSignedPreKeyEntity::class
    ],
    version = 11,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class LinkerDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun linkDao(): LinkDao
    abstract fun storyDao(): StoryDao
    abstract fun noteDao(): NoteDao
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun messageQueueDao(): MessageQueueDao
    abstract fun commentDao(): CommentDao
    abstract fun mediaCacheDao(): MediaCacheDao
    abstract fun notificationDao(): NotificationDao
    abstract fun bleNodeDao(): BleNodeDao
    abstract fun messageIdCacheDao(): MessageIdCacheDao
    abstract fun signalIdentityDao(): SignalIdentityDao
    abstract fun signalSessionDao(): SignalSessionDao
    abstract fun signalPreKeyDao(): SignalPreKeyDao
    abstract fun signalSignedPreKeyDao(): SignalSignedPreKeyDao

    companion object {
        const val DATABASE_NAME = "linker_database"

        // Migration 1 to 2: Initial schema (implicit, no migration needed)
        // Database version 1 was the initial schema with all base tables

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Added privacy features
                db.execSQL("ALTER TABLE users ADD COLUMN isPrivate INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE users ADD COLUMN followRequestSent INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Added follow list visibility control
                db.execSQL("ALTER TABLE users ADD COLUMN hideFollowLists INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Added message reply and reaction support
                db.execSQL("ALTER TABLE messages ADD COLUMN replyToMessageId TEXT")
                db.execSQL("ALTER TABLE messages ADD COLUMN reactions TEXT")
                db.execSQL("ALTER TABLE messages ADD COLUMN messageStatus TEXT NOT NULL DEFAULT 'SENT'")
                db.execSQL("ALTER TABLE messages ADD COLUMN readReceipts TEXT")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Added deletedMessage field for showing deletion info
                db.execSQL("ALTER TABLE messages ADD COLUMN deletedMessage TEXT")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Removed deletedMessage field - now generated dynamically in UI
                // SQLite doesn't support DROP COLUMN directly, so we'll leave it
                // The field will be ignored by the entity
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add offline messaging tables for BLE mesh networking
                
                // Create ble_nodes table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS ble_nodes (
                        nodeId TEXT PRIMARY KEY NOT NULL,
                        deviceAddress TEXT NOT NULL,
                        deviceName TEXT,
                        rssi INTEGER NOT NULL,
                        lastSeen INTEGER NOT NULL,
                        isConnected INTEGER NOT NULL,
                        hopCount INTEGER NOT NULL DEFAULT 1,
                        routeQuality REAL NOT NULL DEFAULT 0.0,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """)
                
                // Create indices for ble_nodes
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ble_nodes_lastSeen ON ble_nodes(lastSeen)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ble_nodes_isConnected ON ble_nodes(isConnected)")
                
                // Create message_id_cache table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS message_id_cache (
                        messageId TEXT PRIMARY KEY NOT NULL,
                        receivedAt INTEGER NOT NULL,
                        sourceNodeId TEXT NOT NULL
                    )
                """)
                
                // Create index for message_id_cache
                db.execSQL("CREATE INDEX IF NOT EXISTS index_message_id_cache_receivedAt ON message_id_cache(receivedAt)")
            }
        }
        
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add pendingKeyExchange field to message_queue table
                db.execSQL("ALTER TABLE message_queue ADD COLUMN pendingKeyExchange INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create Signal Protocol tables
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS signal_identities (
                        address TEXT PRIMARY KEY NOT NULL,
                        identityKey BLOB NOT NULL,
                        trustLevel INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """)
                
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS signal_sessions (
                        address TEXT PRIMARY KEY NOT NULL,
                        sessionRecord BLOB NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """)
                
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS signal_prekeys (
                        preKeyId INTEGER PRIMARY KEY NOT NULL,
                        preKeyRecord BLOB NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """)
                
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS signal_signed_prekeys (
                        signedPreKeyId INTEGER PRIMARY KEY NOT NULL,
                        signedPreKeyRecord BLOB NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """)
            }
        }
        
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add messageType field to message_queue table for proper message type handling
                db.execSQL("ALTER TABLE message_queue ADD COLUMN messageType TEXT NOT NULL DEFAULT 'TEXT'")
            }
        }
    }
}
