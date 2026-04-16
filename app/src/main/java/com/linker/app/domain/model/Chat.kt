package com.linker.app.domain.model

/**
 * Domain model for a Chat conversation
 */
data class Chat(
    val chatId: String,
    val chatType: ChatType,
    val chatName: String?,
    val chatImageUrl: String?,
    val participants: List<User>,
    val lastMessage: Message?,
    val unreadCount: Int,
    val isPinned: Boolean,
    val isMuted: Boolean,
    val isArchived: Boolean,
    val isBlocked: Boolean,
    val isFavorited: Boolean,
    val theme: String?,
    val createdAt: Long,
    val updatedAt: Long,
    /** Grup sohbetlerinde yönetici uid listesi (Firestore `adminIds`). */
    val groupAdminIds: List<String> = emptyList(),
    /** Grubu oluşturan kullanıcı (Firestore `createdBy`). */
    val groupCreatedBy: String? = null
)

enum class ChatType { PRIVATE, GROUP }

/**
 * Domain model for a single Message
 */
data class Message(
    val messageId: String,
    val chatId: String,
    val sender: User,
    val messageType: MessageType,
    val content: String?,
    val mediaUrl: String?,
    val thumbnailUrl: String?,
    val mediaWidth: Int?,
    val mediaHeight: Int?,
    val mediaDuration: Int?,
    val sharedLink: Link?,
    val replyToMessage: Message?,
    val reactions: Map<String, String>, // userId → emoji
    val isEdited: Boolean,
    val isDeleted: Boolean,
    val deletedForEveryone: Boolean,
    val messageStatus: MessageStatus,
    val deliveryMethod: DeliveryMethod,
    val createdAt: Long,
    val updatedAt: Long,
    val deliveredAt: Long?,
    val readAt: Long?
)

enum class MessageType  { TEXT, IMAGE, VIDEO, GIF, LINK, AUDIO, FILE, LOCATION, CONTACT, STICKER }
enum class MessageStatus { SENDING, SENT, DELIVERED, READ, FAILED }
enum class DeliveryMethod { ONLINE, BLE, WIFI_DIRECT, MESH, P2P_WIFI }
