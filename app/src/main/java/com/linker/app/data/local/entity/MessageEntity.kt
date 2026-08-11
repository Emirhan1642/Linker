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
    init {
        require(messageId.isNotBlank()) { "Message ID cannot be blank" }
        require(chatId.isNotBlank()) { "Chat ID cannot be blank" }
        require(senderId.isNotBlank()) { "Sender ID cannot be blank" }
        
        // Content validation
        if (isEncrypted) {
            require(encryptedContent != null) { "Encrypted message must have encryptedContent" }
            require(content == null) { "Encrypted message should not have plain content" }
        } else {
            require(content != null || mediaUrl != null) { "Message must have content or media" }
        }
        
        // Type-specific validations
        when (messageType) {
            MessageType.TEXT -> {
                if (!isEncrypted) require(content != null) { "Text message must have content" }
                content?.let { require(it.length <= MAX_TEXT_LENGTH) { "Message too long" } }
            }
            MessageType.IMAGE, MessageType.VIDEO -> {
                require(mediaUrl != null) { "Media message must have URL" }
                require(thumbnailUrl != null) { "Media message must have thumbnail" }
            }
            MessageType.AUDIO -> {
                require(mediaUrl != null) { "Audio message must have URL" }
                require(mediaDuration != null) { "Audio must have duration" }
            }
            MessageType.LINK -> {
                require(sharedLinkId != null) { "Link message must have linkId" }
            }
            else -> {}
        }
        
        // Dimension validations
        mediaWidth?.let { require(it > 0) { "Media width must be positive" } }
        mediaHeight?.let { require(it > 0) { "Media height must be positive" } }
        mediaDuration?.let { require(it > 0) { "Media duration must be positive" } }
        
        // Timestamp validations
        require(updatedAt >= createdAt) { "Updated cannot be before created" }
        deliveredAt?.let { require(it >= createdAt) { "Delivered cannot be before created" } }
        readAt?.let { 
            require(it >= createdAt) { "Read cannot be before created" }
            deliveredAt?.let { delivered -> 
                require(it >= delivered) { "Read cannot be before delivered" }
            }
        }
        
        // Status consistency
        if (messageStatus == MessageStatus.READ) {
            require(readAt != null) { "READ status must have readAt timestamp" }
        }
        if (messageStatus == MessageStatus.DELIVERED) {
            require(deliveredAt != null) { "DELIVERED status must have deliveredAt timestamp" }
        }
        
        // Delete consistency
        if (deletedForEveryone) {
            require(isDeleted) { "deletedForEveryone requires isDeleted" }
        }
        
        // Reply/forward validation
        if (replyToMessageId != null) {
            require(replyToMessageId != messageId) { "Cannot reply to self" }
        }
        if (forwardedFromMessageId != null) {
            require(forwardedFromMessageId != messageId) { "Cannot forward to self" }
        }
    }

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
