package com.linker.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Chat Entity - Conversation metadata
 * 
 * Represents a chat conversation (private or group)
 */
@Entity(
    tableName = "chats",
    indices = [
        Index(value = ["lastMessageAt"]),
        Index(value = ["isPinned"])
    ]
)
data class ChatEntity(
    @PrimaryKey
    val chatId: String,
    val chatType: ChatType, // PRIVATE, GROUP
    val chatName: String?, // For group chats
    val chatImageUrl: String?, // For group chats
    val participantIds: List<String>, // User IDs
    val lastMessageId: String?,
    val lastMessageText: String?,
    val lastMessageAt: Long?,
    val unreadCount: Int = 0,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val isArchived: Boolean = false,
    val isBlocked: Boolean = false,
    val isFavorited: Boolean = false,
    val theme: String? = null, // Chat theme color/pattern
    val createdAt: Long,
    val updatedAt: Long,
    val lastSyncedAt: Long = System.currentTimeMillis()
)

enum class ChatType {
    PRIVATE,
    GROUP
}
