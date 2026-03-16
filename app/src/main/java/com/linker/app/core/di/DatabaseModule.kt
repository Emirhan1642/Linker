package com.linker.app.core.di

import android.content.Context
import androidx.room.Room
import com.linker.app.data.local.LinkerDatabase
import com.linker.app.data.local.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Database Module
 * 
 * Provides Room database and all DAOs
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideLinkerDatabase(
        @ApplicationContext context: Context
    ): LinkerDatabase {
        return Room.databaseBuilder(
            context,
            LinkerDatabase::class.java,
            LinkerDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration() // TODO: Add proper migrations in production
            .build()
    }
    
    // Provide all DAOs
    @Provides
    @Singleton
    fun provideUserDao(database: LinkerDatabase) = database.userDao()
    
    @Provides
    @Singleton
    fun provideLinkDao(database: LinkerDatabase) = database.linkDao()
    
    @Provides
    @Singleton
    fun provideStoryDao(database: LinkerDatabase) = database.storyDao()
    
    @Provides
    @Singleton
    fun provideNoteDao(database: LinkerDatabase) = database.noteDao()
    
    @Provides
    @Singleton
    fun provideChatDao(database: LinkerDatabase) = database.chatDao()
    
    @Provides
    @Singleton
    fun provideMessageDao(database: LinkerDatabase) = database.messageDao()
    
    @Provides
    @Singleton
    fun provideMessageQueueDao(database: LinkerDatabase) = database.messageQueueDao()
    
    @Provides
    @Singleton
    fun provideCommentDao(database: LinkerDatabase) = database.commentDao()
    
    @Provides
    @Singleton
    fun provideMediaCacheDao(database: LinkerDatabase) = database.mediaCacheDao()
    
    @Provides
    @Singleton
    fun provideNotificationDao(database: LinkerDatabase) = database.notificationDao()
}
