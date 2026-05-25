package com.linker.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.linker.app.data.local.dao.*
import com.linker.app.data.local.entity.*
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException

@Database(
    entities = [
        UserEntity::class, LinkEntity::class, StoryEntity::class,
        NoteEntity::class, ChatEntity::class, MessageEntity::class,
        MessageQueueEntity::class, CommentEntity::class,
        MediaCacheEntity::class, NotificationEntity::class,
        BleNodeEntity::class, MessageIdCacheEntity::class,
        SignalIdentityEntity::class, SignalSessionEntity::class,
        SignalPreKeyEntity::class, SignalSignedPreKeyEntity::class,
        SignalKyberPreKeyEntity::class, SignalSenderKeyEntity::class
    ],
    version = 12,
    exportSchema = true
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
    abstract fun signalKyberPreKeyDao(): SignalKyberPreKeyDao
    abstract fun signalSenderKeyDao(): SignalSenderKeyDao
    
    /**
     * Atomically update queue status and message delivery method.
     * 
     * This transaction ensures both updates succeed or both fail,
     * preventing data inconsistency.
     * 
     * NOTE: Room doesn't support @Transaction on RoomDatabase methods directly.
     * This is a workaround using runInTransaction.
     * 
     * @param timeout Transaction timeout in milliseconds (default: 5000ms)
     * @return Result indicating success or failure with error details
     * @throws IllegalArgumentException if queueId or messageId is blank
     */
    suspend fun updateQueueAndMessageAtomic(
        queueId: String,
        queueStatus: QueueStatus,
        sentAt: Long?,
        messageId: String,
        deliveryMethod: DeliveryMethod,
        timeout: Long = 5000L
    ): Result<Unit> {
        require(queueId.isNotBlank()) { "queueId cannot be blank" }
        require(messageId.isNotBlank()) { "messageId cannot be blank" }
        
        return try {
            withTimeout(timeout) {
                withTransaction {
                    val queueUpdated = messageQueueDao().updateQueueStatus(queueId, queueStatus, sentAt)
                    if (queueUpdated == 0) {
                        android.util.Log.w("LinkerDatabase", "Queue not found: $queueId")
                    }
                    
                    val messageUpdated = messageDao().updateDeliveryMethod(messageId, deliveryMethod)
                    if (messageUpdated == 0) {
                        android.util.Log.w("LinkerDatabase", "Message not found: $messageId")
                    }
                    
                    android.util.Log.d("LinkerDatabase", "Atomic update successful - Queue: $queueId, Message: $messageId")
                }
            }
            Result.success(Unit)
        } catch (e: TimeoutCancellationException) {
            android.util.Log.e("LinkerDatabase", "Transaction timeout: $queueId", e)
            Result.failure(e)
        } catch (e: Exception) {
            android.util.Log.e("LinkerDatabase", "Transaction failed - Queue: $queueId, Message: $messageId", e)
            Result.failure(e)
        }
    }

    companion object {
        const val DATABASE_NAME = "linker_database"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Version 1 to 2: No schema changes
                // This migration exists to maintain complete migration path
                // All base tables were already present in version 1
            }
        }

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
                
                // Add indices for new columns
                db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_replyToMessageId ON messages(replyToMessageId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_messageStatus ON messages(messageStatus)")
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
                // 1. Create new table without deletedMessage
                db.execSQL("""
                    CREATE TABLE messages_new (
                        id TEXT PRIMARY KEY NOT NULL,
                        chatId TEXT NOT NULL,
                        senderId TEXT NOT NULL,
                        content TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        messageType TEXT NOT NULL,
                        deliveryMethod TEXT NOT NULL,
                        replyToMessageId TEXT,
                        reactions TEXT,
                        messageStatus TEXT NOT NULL DEFAULT 'SENT',
                        readReceipts TEXT,
                        FOREIGN KEY(chatId) REFERENCES chats(id) ON DELETE CASCADE
                    )
                """)
                
                // 2. Copy data from old table (excluding deletedMessage)
                db.execSQL("""
                    INSERT INTO messages_new 
                    SELECT id, chatId, senderId, content, timestamp, messageType, 
                           deliveryMethod, replyToMessageId, reactions, messageStatus, readReceipts
                    FROM messages
                """)
                
                // 3. Drop old table
                db.execSQL("DROP TABLE messages")
                
                // 4. Rename new table
                db.execSQL("ALTER TABLE messages_new RENAME TO messages")
                
                // 5. Recreate indices
                db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_chatId ON messages(chatId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_timestamp ON messages(timestamp)")
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
                
                // Add index for filtering pending key exchanges
                db.execSQL("CREATE INDEX IF NOT EXISTS index_message_queue_pendingKeyExchange ON message_queue(pendingKeyExchange)")
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
                
                // Add indices for timestamp-based queries (key rotation, cleanup)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_signal_identities_updatedAt ON signal_identities(updatedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_signal_sessions_updatedAt ON signal_sessions(updatedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_signal_prekeys_createdAt ON signal_prekeys(createdAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_signal_signed_prekeys_createdAt ON signal_signed_prekeys(createdAt)")
            }
        }
        
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add messageType field to message_queue table for proper message type handling
                db.execSQL("ALTER TABLE message_queue ADD COLUMN messageType TEXT NOT NULL DEFAULT 'TEXT'")
                
                // Add index for filtering by message type
                db.execSQL("CREATE INDEX IF NOT EXISTS index_message_queue_messageType ON message_queue(messageType)")
            }
        }
        
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add Kyber pre-keys table for PQXDH support
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS signal_kyber_prekeys (
                        kyberPreKeyId INTEGER PRIMARY KEY NOT NULL,
                        kyberPreKeyRecord BLOB NOT NULL,
                        createdAt INTEGER NOT NULL,
                        isUsed INTEGER NOT NULL DEFAULT 0
                    )
                """)
                
                // Add sender keys table for group messaging support
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS signal_sender_keys (
                        senderAddress TEXT NOT NULL,
                        distributionId TEXT NOT NULL,
                        senderKeyRecord BLOB NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY (senderAddress, distributionId)
                    )
                """)
                
                // Create indices for better query performance
                db.execSQL("CREATE INDEX IF NOT EXISTS index_signal_kyber_prekeys_isUsed ON signal_kyber_prekeys(isUsed)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_signal_sender_keys_senderAddress ON signal_sender_keys(senderAddress)")
            }
        }
    }
}
