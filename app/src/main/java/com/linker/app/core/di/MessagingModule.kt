package com.linker.app.core.di

import com.linker.app.data.cache.UserCache
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
 * Binds all new UseCases and implementations from the messaging analysis
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MessagingModule {

    @Binds
    @Singleton
    abstract fun bindCurrentUserProvider(
        impl: CurrentUserProviderImpl
    ): CurrentUserProvider

    companion object {

        @Provides
        @Singleton
        fun provideUserCache(): UserCache = UserCache()

        @Provides
        @Singleton
        fun provideLoadMessageInfoUseCase(
            chatRepository: com.linker.app.domain.repository.ChatRepository
        ): LoadMessageInfoUseCase {
            return LoadMessageInfoUseCase(chatRepository)
        }

        @Provides
        @Singleton
        fun provideLoadMessagesPagedUseCase(
            chatRepository: com.linker.app.domain.repository.ChatRepository
        ): LoadMessagesPagedUseCase {
            return LoadMessagesPagedUseCase(chatRepository)
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
