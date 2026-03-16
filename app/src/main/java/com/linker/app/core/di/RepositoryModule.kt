package com.linker.app.core.di

import com.linker.app.data.repository.AuthRepositoryImpl
import com.linker.app.data.repository.ChatRepositoryImpl
import com.linker.app.data.repository.LinkRepositoryImpl
import com.linker.app.data.repository.UserRepositoryImpl
import com.linker.app.domain.repository.AuthRepository
import com.linker.app.domain.repository.ChatRepository
import com.linker.app.domain.repository.LinkRepository
import com.linker.app.domain.repository.UserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Repository Module
 *
 * Binds concrete implementations to their domain interfaces.
 * Using @Binds is more efficient than @Provides — no extra wrapper function.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository

    @Binds
    @Singleton
    abstract fun bindLinkRepository(impl: LinkRepositoryImpl): LinkRepository

    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository
}
