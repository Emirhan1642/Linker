package com.linker.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Message Entity - Individual chat messages
 * 
 * Supports text, media, GIFs, and shared links
 */
@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["chatId"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["userId"],
            childColumns = ["senderId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["chatId"]),
        Index(value = ["senderId"]),
        Index(value = ["createdAt"]),
        Index(value = ["messageStatus"])
    ]
)
data class MessageEntity(
    @PrimaryKey
    val messageId: String,
    val chatId: String,
    val senderId: String,
    val messageType: MessageType, // TEXT, IMAGE, VIDEO, GIF, LINK, AUDIO
    val content: String?, // Text content or media URL
    val mediaUrl: String? = null,
    val thumbnailUrl: String? = null,
    val mediaWidth: Int? = null,
    val mediaHeight: Int? = null,
    val mediaDuration: Int? = null, // For video/audio in seconds
    val sharedLinkId: String? = null, // If sharing a Link post
    val replyToMessageId: String? = null,
    val forwardedFromMessageId: String? = null,
    val reactions: Map<String, String> = emptyMap(), // userId to emoji
    val isEdited: Boolean = false,
    val isDeleted: Boolean = false,
    val deletedForEveryone: Boolean = false,
    val messageStatus: MessageStatus, // SENDING, SENT, DELIVERED, READ, FAILED
    val deliveryMethod: DeliveryMethod, // ONLINE, BLE, WIFI_DIRECT
    val encryptedContent: String? = null, // E2E encrypted content
    val createdAt: Long,
    val updatedAt: Long,
    val deliveredAt: Long? = null,
    val readAt: Long? = null,
    val lastSyncedAt: Long = System.currentTimeMillis()
)

enum class MessageType {
    TEXT,
    IMAGE,
    VIDEO,
    GIF,
    LINK,  // Shared Link post
    AUDIO
}

enum class MessageStatus {
    SENDING,
    SENT,
    DELIVERED,
    READ,
    FAILED
}

enum class DeliveryMethod {
    ONLINE,      // Via internet
    BLE,         // Via Bluetooth mesh
    WIFI_DIRECT  // Via Wi-Fi Direct
}
