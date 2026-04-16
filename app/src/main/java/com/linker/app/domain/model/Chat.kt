package com.linker.app.domain.model

/**
 * Domain model for a Chat conversation
 */
data class Chat(
    val chatId: String = "",
    val chatType: ChatType = ChatType.PRIVATE,
    val chatName: String? = null,
    val chatImageUrl: String? = null,
    val participants: List<User> = emptyList(),
    val lastMessage: Message? = null,
    val unreadCount: Int = 0,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val isArchived: Boolean = false,
    val isBlocked: Boolean = false,
    val isFavorited: Boolean = false,
    val theme: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
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
    val messageId: String = "",
    val chatId: String = "",
    val sender: User = User(),
    val messageType: MessageType = MessageType.TEXT,
    val content: String? = null,
    val mediaUrl: String? = null,
    val thumbnailUrl: String? = null,
    val mediaWidth: Int? = null,
    val mediaHeight: Int? = null,
    val mediaDuration: Int? = null,
    val sharedLink: Link? = null,
    val replyToMessage: Message? = null,
    val reactions: Map<String, String> = emptyMap(), // userId → emoji
    val isEdited: Boolean = false,
    val isDeleted: Boolean = false,
    val deletedForEveryone: Boolean = false,
    val messageStatus: MessageStatus = MessageStatus.SENT,
    val deliveryMethod: DeliveryMethod = DeliveryMethod.ONLINE,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val deliveredAt: Long? = null,
    val readAt: Long? = null
)

enum class MessageType  { TEXT, IMAGE, VIDEO, GIF, LINK, AUDIO, FILE, LOCATION, CONTACT, STICKER }
enum class MessageStatus { SENDING, SENT, DELIVERED, READ, FAILED }
enum class DeliveryMethod { ONLINE, BLE, WIFI_DIRECT, MESH, P2P_WIFI }
