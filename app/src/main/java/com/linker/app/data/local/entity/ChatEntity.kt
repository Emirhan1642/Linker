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
        Index(value = ["isArchived", "isPinned", "lastMessageAt"], name = "idx_chat_list"),
        Index(value = ["isArchived", "isMuted"], name = "idx_unread_filter"),
        Index(value = ["isPinned"], name = "idx_pinned"),
        Index(value = ["lastMessageAt"], name = "idx_last_message")
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
) {
    init {
        require(chatId.isNotBlank()) { "Chat ID cannot be blank" }
        require(participantIds.isNotEmpty()) { "Chat must have at least one participant" }
        require(updatedAt >= createdAt) { "Updated timestamp cannot be before created timestamp" }
        
        if (chatType == ChatType.GROUP) {
            require(!chatName.isNullOrBlank()) { "Group chat must have a name" }
            require(participantIds.size >= 2) { "Group chat must have at least 2 participants" }
        }
        
        if (lastMessageAt != null) {
            require(lastMessageAt >= createdAt) { "Last message timestamp cannot be before chat creation" }
        }
        
        lastMessageText?.let {
            require(it.length <= MAX_PREVIEW_LENGTH) {
                "Last message preview cannot exceed $MAX_PREVIEW_LENGTH characters"
            }
        }
    }

    val hasUnreadMessages: Boolean
        get() = unreadCount > 0

    val isActive: Boolean
        get() = !isArchived && !isBlocked

    fun shouldShowNotification(): Boolean {
        return !isMuted && hasUnreadMessages && !isArchived
    }

    fun getDisplayName(currentUserId: String): String {
        return when (chatType) {
            ChatType.GROUP -> chatName ?: "Group Chat"
            ChatType.PRIVATE -> {
                participantIds.firstOrNull { it != currentUserId } ?: "Unknown"
            }
        }
    }

    companion object {
        const val MAX_PREVIEW_LENGTH = 200
    }
}

enum class ChatType {
    PRIVATE,
    GROUP
}
