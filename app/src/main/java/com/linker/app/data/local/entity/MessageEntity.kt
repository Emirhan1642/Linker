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
        )
    ],
    indices = [
        Index(value = ["chatId", "createdAt"], name = "idx_chat_messages"),
        Index(value = ["chatId", "messageStatus"], name = "idx_message_status"),
        Index(value = ["replyToMessageId"], name = "idx_message_replies"),
        Index(value = ["senderId", "createdAt"], name = "idx_sender")
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
    val replyToNoteJson: String? = null, // JSON serialized NoteReference
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
    val lastSyncedAt: Long = System.currentTimeMillis(),
    val isEncrypted: Boolean = false
) {
    companion object {
        const val MAX_TEXT_LENGTH = 10000
        const val MAX_REACTIONS = 50
    }
}

enum class MessageType {
    TEXT,
    IMAGE,
    VIDEO,
    GIF,
    LINK,  // Shared Link post
    AUDIO,
    FILE,
    LOCATION,
    CONTACT,
    STICKER
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
