package com.linker.app.core.di

import com.linker.app.data.cache.UserCache
import com.linker.app.data.queue.MessageQueueProcessor
import com.linker.app.data.queue.MessageQueueProcessorImpl
import com.linker.app.data.queue.SyncManager
import com.linker.app.data.queue.SyncManagerImpl
import com.linker.app.data.repository.CurrentUserProviderImpl
import com.linker.app.domain.usecase.chat.LoadMessageInfoUseCase
import com.linker.app.domain.usecase.chat.LoadMessagesPagedUseCase
import com.linker.app.domain.usecase.chat.TypingIndicatorUseCase
import com.linker.app.domain.usecase.user.CurrentUserProvider
import com.linker.app.domain.usecase.user.GetUserDisplayNameUseCase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * DI Module for messaging system improvements
 * 
 * Binds all UseCases and implementations from the messaging analysis.
 * 
 * **Components:**
 * - CurrentUserProvider: Current user information access
 * - MessageQueueProcessor: Offline message queue management
 * - SyncManager: Message synchronization with server
 * - UserCache: In-memory user data cache
 * - Message UseCases: Message loading and display
 * - TypingIndicator: Real-time typing status
 * 
 * **Configuration:**
 * MessageQueueProcessor and SyncManager use OfflineMessagingConfig
 * for queue size limits, sync intervals, and retry policies.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MessagingModule {

    /**
     * Binds CurrentUserProvider for accessing current user info
     */
    @Binds
    @Singleton
    abstract fun bindCurrentUserProvider(
        impl: CurrentUserProviderImpl
    ): CurrentUserProvider

    /**
     * Binds TypingIndicatorRepository for typing indicators in chat
     */
    @Binds
    @Singleton
    abstract fun bindTypingIndicatorRepository(
        impl: com.linker.app.data.repository.TypingIndicatorRepositoryImpl
    ): com.linker.app.domain.usecase.chat.TypingIndicatorRepository

    @Binds
    @Singleton
    abstract fun bindMessageQueueProcessor(
        impl: MessageQueueProcessorImpl
    ): MessageQueueProcessor

    /**
     * Binds SyncManager for message synchronization
     * 
     * CONFIGURATION:
     * Uses OfflineMessagingConfig for:
     * - SYNC_INTERVAL_MS: Sync frequency
     * - BATCH_SIZE: Messages per sync batch
     * - SYNC_TIMEOUT_MS: Timeout for sync operations
     */
    @Binds
    @Singleton
    abstract fun bindSyncManager(
        impl: SyncManagerImpl
    ): SyncManager

    companion object {

        @Provides
        @Singleton
        fun provideLoadMessageInfoUseCase(
            messageRepository: com.linker.app.domain.repository.MessageRepository
        ): LoadMessageInfoUseCase {
            return LoadMessageInfoUseCase(messageRepository)
        }

        @Provides
        @Singleton
        fun provideLoadMessagesPagedUseCase(
            messageRepository: com.linker.app.domain.repository.MessageRepository
        ): LoadMessagesPagedUseCase {
            return LoadMessagesPagedUseCase(messageRepository)
        }

        @Provides
        @Singleton
        fun provideGetUserDisplayNameUseCase(
            userRepository: com.linker.app.domain.repository.UserRepository,
            currentUserProvider: CurrentUserProvider
        ): GetUserDisplayNameUseCase {
            return GetUserDisplayNameUseCase(userRepository, currentUserProvider)
        }
    }
}
