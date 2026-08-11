package com.linker.app.domain.model

/**
 * Lightweight message reference for replies.
 *
 * Avoids the circular reference risk that arises when [Message] directly
 * contains another [Message] via `replyToMessage`.
 * This class holds only the data needed to render a reply preview.
 *
 * @property messageId Unique identifier of the referenced message.
 * @property senderId User ID of the original sender.
 * @property senderName Display name of the original sender.
 * @property content Text content of the original message (null for media-only).
 * @property messageType Type of the original message (TEXT, IMAGE, etc.).
 * @property createdAt Timestamp of the original message (epoch ms).
 */
data class MessageReference(
    val messageId: String,
    val senderId: String,
    val senderName: String,
    val content: String?,
    val messageType: MessageType,
    val createdAt: Long
)

/**
 * Lightweight note reference for note replies.
 * 
 * Used to render a preview of the note being replied to.
 */
data class NoteReference(
    val noteId: String,
    val authorId: String,
    val authorName: String,
    val noteType: String, // TEXT, MUSIC, COUNTDOWN, etc.
    val content: String?,
    val musicTrackName: String? = null,
    val musicArtistName: String? = null,
    val musicAlbumArt: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val backgroundColor: String? = null,
    val textColor: String? = null,
    val expiresAt: Long // To check if the note is still accessible without fetching it
)

/**
 * Type-safe group chat permissions.
 *
 * Replaces raw `Map<String, Any>` to ensure compile-time safety
 * and provide convenient presets for common permission profiles.
 *
 * @property canSendMessages Whether members can send text messages.
 * @property canSendMedia Whether members can send photos/videos/files.
 * @property canAddMembers Whether members can add new participants.
 * @property canRemoveMembers Whether members can remove participants.
 * @property canEditGroupInfo Whether members can change group name/image/description.
 * @property canPinMessages Whether members can pin messages.
 * @property canDeleteMessages Whether members can delete others' messages.
 * @property canChangePermissions Whether members can modify these permissions.
 */
data class GroupPermissions(
    val canSendMessages: Boolean = true,
    val canSendMedia: Boolean = true,
    val canAddMembers: Boolean = false,
    val canRemoveMembers: Boolean = false,
    val canEditGroupInfo: Boolean = false,
    val canPinMessages: Boolean = false,
    val canDeleteMessages: Boolean = false,
    val canChangePermissions: Boolean = false
) {
    /**
     * Converts to a Firestore-compatible map for persistence.
     */
    fun toMap(): Map<String, Any> = mapOf(
        "canSendMessages" to canSendMessages,
        "canSendMedia" to canSendMedia,
        "canAddMembers" to canAddMembers,
        "canRemoveMembers" to canRemoveMembers,
        "canEditGroupInfo" to canEditGroupInfo,
        "canPinMessages" to canPinMessages,
        "canDeleteMessages" to canDeleteMessages,
        "canChangePermissions" to canChangePermissions
    )

    companion object {
        /** Default permissions for regular group members. */
        val DEFAULT = GroupPermissions()

        /** Full permissions for group admins. */
        val ADMIN = GroupPermissions(
            canAddMembers = true,
            canRemoveMembers = true,
            canEditGroupInfo = true,
            canPinMessages = true,
            canDeleteMessages = true,
            canChangePermissions = true
        )

        /** Read-only: members cannot send messages or media. */
        val READ_ONLY = GroupPermissions(
            canSendMessages = false,
            canSendMedia = false
        )

        /**
         * Creates [GroupPermissions] from a Firestore map.
         * Unknown keys are ignored; missing keys fall back to [DEFAULT] values.
         */
        fun fromMap(map: Map<String, Any>?): GroupPermissions {
            if (map.isNullOrEmpty()) return DEFAULT
            return GroupPermissions(
                canSendMessages = map["canSendMessages"] as? Boolean ?: true,
                canSendMedia = map["canSendMedia"] as? Boolean ?: true,
                canAddMembers = map["canAddMembers"] as? Boolean ?: false,
                canRemoveMembers = map["canRemoveMembers"] as? Boolean ?: false,
                canEditGroupInfo = map["canEditGroupInfo"] as? Boolean ?: false,
                canPinMessages = map["canPinMessages"] as? Boolean ?: false,
                canDeleteMessages = map["canDeleteMessages"] as? Boolean ?: false,
                canChangePermissions = map["canChangePermissions"] as? Boolean ?: false
            )
        }
    }
}

/**
 * Domain model for a Chat conversation.
 *
 * Represents both private (1-on-1) and group chats.
 * Group-specific fields ([groupAdminIds], [groupCreatedBy], [groupPermissions])
 * are only meaningful when [chatType] is [ChatType.GROUP].
 *
 * @property chatId Unique chat identifier (Firestore document ID).
 * @property chatType Whether this is a PRIVATE or GROUP chat.
 * @property chatName Display name (null for private chats — derived from participants).
 * @property chatImageUrl Group image URL (null for private chats).
 * @property participants List of users in this chat.
 * @property lastMessage The most recent message in this chat (for list previews).
 * @property unreadCount Number of unread messages for the current user.
 * @property isPinned Whether the chat is pinned to the top.
 * @property isMuted Whether notifications are silenced.
 * @property isArchived Whether the chat is archived.
 * @property isBlocked Whether the other user (private) or the group is blocked.
 * @property isFavorited Whether the chat is marked as favorite.
 * @property theme Optional visual theme key for the chat background.
 * @property createdAt Chat creation timestamp (epoch ms).
 * @property updatedAt Last update timestamp (epoch ms).
 * @property groupAdminIds Admin user IDs for group chats (Firestore `adminIds`).
 * @property groupCreatedBy UID of the group creator (Firestore `createdBy`).
 * @property groupPermissions Type-safe group permissions.
 */
data class Chat(
    val chatId: String,
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
    val createdAt: Long,
    val updatedAt: Long,
    /** Grup sohbetlerinde yönetici uid listesi (Firestore `adminIds`). */
    val groupAdminIds: List<String> = emptyList(),
    /** Grubu oluşturan kullanıcı (Firestore `createdBy`). */
    val groupCreatedBy: String? = null,
    /** Grup izinleri — type-safe wrapper (Firestore `groupPermissions`). */
    val groupPermissions: GroupPermissions = GroupPermissions.DEFAULT
) {
    init {
        require(chatId.isNotBlank()) { "chatId cannot be blank" }
        require(createdAt > 0) { "createdAt must be positive" }
        require(updatedAt >= createdAt) { "updatedAt cannot be before createdAt" }
        require(unreadCount >= 0) { "unreadCount cannot be negative" }
    }
}

/**
 * Chat type discriminator.
 */
enum class ChatType {
    /** One-on-one conversation between two users. */
    PRIVATE,
    /** Multi-user group conversation. */
    GROUP
}

/**
 * Domain model for a single Message.
 *
 * @property messageId Unique message identifier.
 * @property chatId ID of the chat this message belongs to.
 * @property sender Lightweight sender reference (use [UserReference.from] to create from [User]).
 * @property messageType Content type (TEXT, IMAGE, VIDEO, etc.).
 * @property content Text content (null for media-only messages).
 * @property mediaUrl URL to the media file (null for text-only).
 * @property thumbnailUrl Preview thumbnail for media messages.
 * @property mediaWidth Width of the media in pixels.
 * @property mediaHeight Height of the media in pixels.
 * @property mediaDuration Duration of audio/video in seconds.
 * @property sharedLink Embedded link preview data.
 * @property replyToMessage Lightweight reference to the message being replied to.
 * @property reactions User reactions map (userId → emoji).
 * @property isEdited Whether the message has been edited.
 * @property isDeleted Whether the message has been soft-deleted.
 * @property deletedForEveryone Whether the deletion is visible to all participants.
 * @property messageStatus Current delivery status.
 * @property deliveryMethod How the message was delivered (ONLINE, BLE, WIFI_DIRECT).
 * @property createdAt Message creation timestamp (epoch ms).
 * @property updatedAt Last update timestamp (epoch ms).
 * @property deliveredAt When the message was delivered (epoch ms).
 * @property readAt When the message was read by the recipient (epoch ms).
 * @property readReceipts Per-user read timestamps (userId → epoch ms).
 */
data class Message(
    val messageId: String,
    val chatId: String,
    val sender: UserReference,
    val messageType: MessageType = MessageType.TEXT,
    val content: String? = null,
    val mediaUrl: String? = null,
    val thumbnailUrl: String? = null,
    val mediaWidth: Int? = null,
    val mediaHeight: Int? = null,
    val mediaDuration: Int? = null,
    val sharedLink: Link? = null,
    val replyToMessage: MessageReference? = null,
    val replyToNote: NoteReference? = null,
    val reactions: Map<String, String> = emptyMap(), // userId → emoji
    val isEdited: Boolean = false,
    val isDeleted: Boolean = false,
    val deletedForEveryone: Boolean = false,
    val messageStatus: MessageStatus = MessageStatus.SENT,
    val deliveryMethod: DeliveryMethod = DeliveryMethod.ONLINE,
    val createdAt: Long,
    val updatedAt: Long,
    val deliveredAt: Long? = null,
    val readAt: Long? = null,
    val readReceipts: Map<String, Long> = emptyMap()
) {
    init {
        require(messageId.isNotBlank()) { "messageId cannot be blank" }
        require(chatId.isNotBlank()) { "chatId cannot be blank" }
        require(createdAt > 0) { "createdAt must be positive" }
        require(updatedAt >= createdAt) { "updatedAt cannot be before createdAt" }
        deliveredAt?.let {
            require(it >= createdAt) { "deliveredAt cannot be before createdAt" }
        }
        readAt?.let {
            require(it >= createdAt) { "readAt cannot be before createdAt" }
        }
    }
}

/**
 * Supported message content types.
 */
enum class MessageType {
    /** Plain text message. */
    TEXT,
    /** Image attachment. */
    IMAGE,
    /** Video attachment. */
    VIDEO,
    /** Animated GIF. */
    GIF,
    /** URL link with preview. */
    LINK,
    /** Voice/audio message. */
    AUDIO,
    /** File attachment. */
    FILE,
    /** Location share. */
    LOCATION,
    /** Contact card. */
    CONTACT,
    /** Sticker. */
    STICKER
}

/**
 * Message delivery status.
 *
 * State machine:
 * ```
 * SENDING → SENT → DELIVERED → READ
 *    ↓        ↓        ↓
 *  FAILED   FAILED   FAILED
 *    ↓
 *  SENDING (retry)
 * ```
 */
enum class MessageStatus {
    /** Message is being sent. */
    SENDING,
    /** Message reached the server. */
    SENT,
    /** Message was delivered to the recipient's device. */
    DELIVERED,
    /** Message was read by the recipient. */
    READ,
    /** Message delivery failed. */
    FAILED;

    /**
     * Checks whether a transition from this status to [newStatus] is valid.
     */
    fun canTransitionTo(newStatus: MessageStatus): Boolean = when (this) {
        SENDING -> newStatus in setOf(SENT, FAILED)
        SENT -> newStatus in setOf(DELIVERED, FAILED)
        DELIVERED -> newStatus in setOf(READ, FAILED)
        READ -> false
        FAILED -> newStatus == SENDING
    }

    /** Whether this status is terminal (won't change further). */
    fun isTerminal(): Boolean = this == READ

    /** Whether delivery has failed. */
    fun isFailed(): Boolean = this == FAILED

    /** Whether the message can be retried (only from FAILED). */
    fun canRetry(): Boolean = this == FAILED
}

/**
 * Message delivery method.
 */
enum class DeliveryMethod {
    /** Standard internet delivery via Firebase. */
    ONLINE,
    /** Bluetooth Low Energy peer-to-peer delivery. */
    BLE,
    /** Wi-Fi Direct peer-to-peer delivery. */
    WIFI_DIRECT
}
