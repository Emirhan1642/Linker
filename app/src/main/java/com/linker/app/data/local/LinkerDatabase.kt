package com.linker.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.linker.app.data.local.dao.*
import com.linker.app.data.local.entity.*

/**
 * Linker Room Database
 * 
 * Main database for offline-first architecture
 * Contains all entities for users, posts, stories, messages, and cache
 */
@Database(
    entities = [
        UserEntity::class,
        LinkEntity::class,
        StoryEntity::class,
        NoteEntity::class,
        ChatEntity::class,
        MessageEntity::class,
        MessageQueueEntity::class,
        CommentEntity::class,
        MediaCacheEntity::class,
        NotificationEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class LinkerDatabase : RoomDatabase() {
    
    // DAOs
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
    }
}
