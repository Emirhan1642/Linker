package com.linker.app.core.di

import com.linker.app.core.util.Result
import com.linker.app.data.repository.AccountRepositoryImpl
import com.linker.app.data.repository.AuthRepositoryImpl
import com.linker.app.data.repository.ChatRepositoryImpl
import com.linker.app.data.repository.ChatSettingsRepositoryImpl
import com.linker.app.data.repository.CommentRepositoryImpl
import com.linker.app.data.repository.LinkRepositoryImpl
import com.linker.app.data.repository.MessageReactionRepositoryImpl
import com.linker.app.data.repository.MessageRepositoryImpl
import com.linker.app.data.repository.NoteRepositoryImpl
import com.linker.app.data.repository.NotificationRepositoryImpl
import com.linker.app.data.repository.ReadReceiptRepositoryImpl
import com.linker.app.data.repository.StoryRepositoryImpl
import com.linker.app.data.repository.UserPreferencesRepositoryImpl
import com.linker.app.data.repository.UserRepositoryImpl
import com.linker.app.domain.repository.AccountRepository
import com.linker.app.domain.repository.AuthRepository
import com.linker.app.domain.repository.ChatRepository
import com.linker.app.domain.repository.ChatSettingsRepository
import com.linker.app.domain.repository.CommentRepository
import com.linker.app.domain.repository.GifRepository
import com.linker.app.domain.repository.LinkRepository
import com.linker.app.domain.repository.MessageReactionRepository
import com.linker.app.domain.repository.MessageRepository
import com.linker.app.domain.repository.NoteRepository
import com.linker.app.domain.repository.NotificationRepository
import com.linker.app.domain.repository.ReadReceiptRepository
import com.linker.app.domain.repository.StoryRepository
import com.linker.app.domain.repository.UserPreferencesRepository
import com.linker.app.data.repository.LocationRepositoryImpl
import com.linker.app.domain.repository.LocationRepository
import com.linker.app.data.repository.LiveLocationRepositoryImpl
import com.linker.app.domain.repository.LiveLocationRepository
import com.linker.app.data.repository.SpotifyRepositoryImpl
import com.linker.app.domain.repository.SpotifyRepository
import com.linker.app.data.repository.LyricsRepositoryImpl
import com.linker.app.domain.repository.LyricsRepository
import com.linker.app.domain.repository.UserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Repository Module
 *
 * Binds repository implementations to their interfaces following the
 * Repository pattern from Clean Architecture.
 * 
 * **Architecture:**
 * - Interfaces defined in domain layer (domain/repository/)
 * - Implementations in data layer (data/repository/)
 * - All repositories are application-scoped singletons
 * - Repositories coordinate between data sources (local DB, remote API, cache)
 * 
 * **Repositories:**
 * 
 * **Authentication & User Management:**
 * - AuthRepository: User authentication and session management
 * - UserRepository: User profile CRUD operations
 * - AccountRepository: Account settings and preferences
 * 
 * **Social Features:**
 * - LinkRepository: Social links management
 * - StoryRepository: User stories (temporary posts)
 * - NoteRepository: Personal notes
 * - CommentRepository: Comments on posts/stories
 * 
 * **Messaging:**
 * - ChatRepository: Chat conversations and participants
 * - MessageRepository: Message CRUD operations
 * - MessageReactionRepository: Message reactions (emoji, likes)
 * - ReadReceiptRepository: Message read status tracking
 * - ChatSettingsRepository: Per-chat settings (mute, notifications)
 * 
 * **Notifications:**
 * - NotificationRepository: Push notification management
 * 
 * **Data Flow:**
 * ```
 * ViewModel -> UseCase -> Repository -> DataSource (DAO/API)
 * ```
 * 
 * **Dependency Injection:**
 * Repositories are injected via constructor injection:
 * ```kotlin
 * class MyViewModel @Inject constructor(
 *     private val userRepository: UserRepository
 * ) : ViewModel()
 * ```
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    // ══════════════════════════════════════════════════════════════════════
    // Authentication & User Management
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Handles user authentication, login, logout, and session management
     */
    @Binds @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    /**
     * Manages user profiles, avatars, and public information
     */
    @Binds @Singleton
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository

    /**
     * Manages account settings, privacy, and preferences
     */
    @Binds @Singleton
    abstract fun bindAccountRepository(impl: AccountRepositoryImpl): AccountRepository

    // ══════════════════════════════════════════════════════════════════════
    // Social Features
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Manages social links (Instagram, Twitter, etc.)
     */
    @Binds @Singleton
    abstract fun bindLinkRepository(impl: LinkRepositoryImpl): LinkRepository

    /**
     * Manages user stories (temporary posts, 24-hour expiry)
     */
    @Binds @Singleton
    abstract fun bindStoryRepository(impl: StoryRepositoryImpl): StoryRepository

    /**
     * Manages personal notes
     */
    @Binds @Singleton
    abstract fun bindNoteRepository(impl: NoteRepositoryImpl): NoteRepository

    @Binds @Singleton
    abstract fun bindGifRepository(impl: com.linker.app.data.repository.GiphyGifRepositoryImpl): com.linker.app.domain.repository.GifRepository

    @Binds @Singleton
    abstract fun bindLocationRepository(impl: LocationRepositoryImpl): LocationRepository

    @Binds @Singleton
    abstract fun bindLiveLocationRepository(impl: LiveLocationRepositoryImpl): LiveLocationRepository

    @Binds
    @Singleton
    abstract fun bindSpotifyRepository(impl: SpotifyRepositoryImpl): SpotifyRepository

    @Binds
    @Singleton
    abstract fun bindLyricsRepository(impl: LyricsRepositoryImpl): LyricsRepository

    /**
     * Manages comments on posts, stories, and other content
     */
    @Binds @Singleton
    abstract fun bindCommentRepository(impl: CommentRepositoryImpl): CommentRepository

    // ══════════════════════════════════════════════════════════════════════
    // Messaging System
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Manages chat conversations, participants, and metadata
     */
    @Binds @Singleton
    abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository

    /**
     * Manages individual messages (send, receive, edit, delete)
     */
    @Binds @Singleton
    abstract fun bindMessageRepository(impl: MessageRepositoryImpl): MessageRepository

    /**
     * Manages message reactions (emoji reactions, likes)
     */
    @Binds @Singleton
    abstract fun bindMessageReactionRepository(
        impl: MessageReactionRepositoryImpl
    ): MessageReactionRepository

    /**
     * Manages read receipts (message seen status)
     */
    @Binds @Singleton
    abstract fun bindReadReceiptRepository(
        impl: ReadReceiptRepositoryImpl
    ): ReadReceiptRepository

    /**
     * Manages per-chat settings (mute, notifications, wallpaper)
     */
    @Binds @Singleton
    abstract fun bindChatSettingsRepository(
        impl: ChatSettingsRepositoryImpl
    ): ChatSettingsRepository

    // ══════════════════════════════════════════════════════════════════════
    // Notifications
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Manages push notifications and notification preferences
     */
    @Binds @Singleton
    abstract fun bindNotificationRepository(
        impl: NotificationRepositoryImpl
    ): NotificationRepository

    // ══════════════════════════════════════════════════════
    // User Preferences & Moderation
    // ══════════════════════════════════════════════════════

    /**
     * Manages user blocking, muting, content interest signals and reporting
     */
    @Binds @Singleton
    abstract fun bindUserPreferencesRepository(
        impl: UserPreferencesRepositoryImpl
    ): UserPreferencesRepository
}
