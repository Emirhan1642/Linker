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

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideLinkerDatabase(@ApplicationContext context: Context): LinkerDatabase {
        return Room.databaseBuilder(context, LinkerDatabase::class.java, LinkerDatabase.DATABASE_NAME)
            .addMigrations(
                LinkerDatabase.MIGRATION_2_3,
                LinkerDatabase.MIGRATION_3_4,
                LinkerDatabase.MIGRATION_4_5,
                LinkerDatabase.MIGRATION_5_6,
                LinkerDatabase.MIGRATION_6_7,
                LinkerDatabase.MIGRATION_7_8,
                LinkerDatabase.MIGRATION_8_9,
                LinkerDatabase.MIGRATION_9_10,
                LinkerDatabase.MIGRATION_10_11
            )
            // ✅ REMOVED: fallbackToDestructiveMigration() - Data loss risk
            .build()
    }

    @Provides @Singleton fun provideUserDao(db: LinkerDatabase)         = db.userDao()
    @Provides @Singleton fun provideLinkDao(db: LinkerDatabase)         = db.linkDao()
    @Provides @Singleton fun provideStoryDao(db: LinkerDatabase)        = db.storyDao()
    @Provides @Singleton fun provideNoteDao(db: LinkerDatabase)         = db.noteDao()
    @Provides @Singleton fun provideChatDao(db: LinkerDatabase)         = db.chatDao()
    @Provides @Singleton fun provideMessageDao(db: LinkerDatabase)      = db.messageDao()
    @Provides @Singleton fun provideMessageQueueDao(db: LinkerDatabase) = db.messageQueueDao()
    @Provides @Singleton fun provideCommentDao(db: LinkerDatabase)      = db.commentDao()
    @Provides @Singleton fun provideMediaCacheDao(db: LinkerDatabase)   = db.mediaCacheDao()
    @Provides @Singleton fun provideNotificationDao(db: LinkerDatabase) = db.notificationDao()
    @Provides @Singleton fun provideBleNodeDao(db: LinkerDatabase)      = db.bleNodeDao()
    @Provides @Singleton fun provideMessageIdCacheDao(db: LinkerDatabase) = db.messageIdCacheDao()

    @Provides @Singleton fun provideSignalIdentityDao(db: LinkerDatabase) = db.signalIdentityDao()
    @Provides @Singleton fun provideSignalSessionDao(db: LinkerDatabase) = db.signalSessionDao()
    @Provides @Singleton fun provideSignalPreKeyDao(db: LinkerDatabase) = db.signalPreKeyDao()
    @Provides @Singleton fun provideSignalSignedPreKeyDao(db: LinkerDatabase) = db.signalSignedPreKeyDao()
}
