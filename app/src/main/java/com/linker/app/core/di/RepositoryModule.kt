package com.linker.app.core.di

import com.linker.app.data.repository.AccountRepositoryImpl
import com.linker.app.data.repository.AuthRepositoryImpl
import com.linker.app.data.repository.ChatRepositoryImpl
import com.linker.app.data.repository.CommentRepositoryImpl
import com.linker.app.data.repository.LinkRepositoryImpl
import com.linker.app.data.repository.NoteRepositoryImpl
import com.linker.app.data.repository.NotificationRepositoryImpl
import com.linker.app.data.repository.StoryRepositoryImpl
import com.linker.app.data.repository.UserRepositoryImpl
import com.linker.app.domain.repository.AccountRepository
import com.linker.app.domain.repository.AuthRepository
import com.linker.app.domain.repository.ChatRepository
import com.linker.app.domain.repository.CommentRepository
import com.linker.app.domain.repository.LinkRepository
import com.linker.app.domain.repository.NoteRepository
import com.linker.app.domain.repository.NotificationRepository
import com.linker.app.domain.repository.StoryRepository
import com.linker.app.domain.repository.UserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Repository Module
 *
 * Binds repository implementations to their interfaces
 *
 * ✅ FIXED: Added all missing repository bindings
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds @Singleton
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository

    @Binds @Singleton
    abstract fun bindLinkRepository(impl: LinkRepositoryImpl): LinkRepository

    @Binds @Singleton
    abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository

    @Binds @Singleton
    abstract fun bindAccountRepository(impl: AccountRepositoryImpl): AccountRepository

    @Binds @Singleton
    abstract fun bindNotificationRepository(impl: NotificationRepositoryImpl): NotificationRepository

    // ✅ NEW: Note Repository binding
    @Binds @Singleton
    abstract fun bindNoteRepository(impl: NoteRepositoryImpl): NoteRepository

    // ✅ NEW: Story Repository binding
    @Binds @Singleton
    abstract fun bindStoryRepository(impl: StoryRepositoryImpl): StoryRepository

    // ✅ NEW: Comment Repository binding
    @Binds @Singleton
    abstract fun bindCommentRepository(impl: CommentRepositoryImpl): CommentRepository
}
