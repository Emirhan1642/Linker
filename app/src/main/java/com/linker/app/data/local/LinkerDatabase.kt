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
        MediaCacheEntity::class, NotificationEntity::class
    ],
    version = 5,
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

    companion object {
        const val DATABASE_NAME = "linker_database"

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE users ADD COLUMN isPrivate INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE users ADD COLUMN followRequestSent INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE users ADD COLUMN hideFollowLists INTEGER NOT NULL DEFAULT 0")
            }
        }

        // ✅ NEW: Migration 4 to 5 - Added message reply and reaction support
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add reply support to messages
                db.execSQL("ALTER TABLE messages ADD COLUMN replyToMessageId TEXT")
                
                // Add reaction support (stored as JSON in new column)
                db.execSQL("ALTER TABLE messages ADD COLUMN reactions TEXT")
                
                // Add message status tracking
                db.execSQL("ALTER TABLE messages ADD COLUMN messageStatus TEXT NOT NULL DEFAULT 'SENT'")
                
                // Add read receipts tracking
                db.execSQL("ALTER TABLE messages ADD COLUMN readReceipts TEXT")
            }
        }
    }
}
