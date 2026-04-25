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
    }
}
