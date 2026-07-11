package com.linker.app.data.local.mapper

import com.linker.app.data.local.entity.*
import com.linker.app.domain.model.*
import com.linker.app.domain.model.ChatType
import com.linker.app.domain.model.DeliveryMethod
import com.linker.app.domain.model.LinkType
import com.linker.app.domain.model.MessageStatus
import com.linker.app.domain.model.MessageType
import com.linker.app.domain.model.NotificationType
import com.linker.app.domain.model.NoteType
import com.linker.app.domain.model.StoryMediaType

// ── UserEntity ↔ User ─────────────────────────────────────────────────────────

/**
 * Convert UserEntity to domain User model
 * @return User domain model
 */
fun UserEntity.toDomain(): User = User(
    userId       = userId,
    username     = username,
    displayName  = displayName,
    _email       = email,
    _phoneNumber = phoneNumber,
    bio          = bio,
    profileImageUrl = profileImageUrl,
    coverImageUrl   = coverImageUrl,
    isVerified   = isVerified,
    relationship = UserRelationship(
        isFollowing       = isFollowing,
        isFollowedBy      = isFollowedBy,
        isBlocked         = isBlocked,
        isMuted           = isMuted,
        followRequestSent = followRequestSent
    ),
    privacy = UserPrivacy(
        isPrivate       = isPrivate,
        hideFollowLists = hideFollowLists
    ),
    metrics = UserMetrics(
        followersCount = followersCount,
        followingCount = followingCount,
        likesCount     = likesCount
    ),
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastSeen = lastSeen
)

/**
 * Convert domain User to UserEntity for database storage
 * @return UserEntity with current timestamp as lastSyncedAt
 */
fun User.toEntity(): UserEntity = UserEntity(
    userId            = userId,
    username          = username,
    displayName       = displayName,
    email             = getEmail(),
    phoneNumber       = getPhoneNumber(),
    bio               = bio,
    profileImageUrl   = profileImageUrl,
    coverImageUrl     = coverImageUrl,
    isVerified        = isVerified,
    followersCount    = metrics.followersCount,
    followingCount    = metrics.followingCount,
    likesCount        = metrics.likesCount,
    isFollowing       = relationship.isFollowing,
    isFollowedBy      = relationship.isFollowedBy,
    isBlocked         = relationship.isBlocked,
    isMuted           = relationship.isMuted,
    isPrivate         = privacy.isPrivate,
    followRequestSent = relationship.followRequestSent,
    hideFollowLists   = privacy.hideFollowLists,
    createdAt         = createdAt,
    updatedAt         = updatedAt,
    lastSeen          = lastSeen,
    lastSyncedAt      = System.currentTimeMillis()
)

// ── LinkEntity ↔ Link ─────────────────────────────────────────────────────────

/**
 * Convert LinkEntity to domain Link model.
 * If author is null, creates a placeholder deleted user.
 * 
 * @param author Author user (nullable)
 * @return Link domain model
 */
fun LinkEntity.toDomain(author: User?): Link {
    val safeAuthor = author ?: User.deletedUser(authorId)
    
    // Convert legacy flat media structure to MediaItem sealed class
    val mediaItems = if (mediaUrls.isNotEmpty()) {
        mediaUrls.mapIndexed { index, url ->
            if (videoDuration != null && index == 0) {
                // First item is a video
                MediaItem.Video(
                    url = url,
                    aspectRatio = aspectRatio,
                    thumbnailUrl = thumbnailUrl,
                    duration = videoDuration,
                    width = null,
                    height = null
                )
            } else {
                // Image item
                MediaItem.Image(
                    url = url,
                    aspectRatio = aspectRatio,
                    width = null,
                    height = null
                )
            }
        }
    } else {
        emptyList()
    }
    
    return Link(
        linkId      = linkId,
        author      = LinkAuthor.from(safeAuthor),
        linkType    = linkType.toDomain(),
        description = description,
        mediaItems  = mediaItems,
        engagement  = EngagementMetrics(
            likesCount    = likesCount,
            commentsCount = commentsCount,
            sharesCount   = sharesCount,
            relinksCount  = relinksCount,
            savesCount    = savesCount,
            viewsCount    = viewsCount,
            isLiked       = isLiked,
            isSaved       = isSaved,
            isRelinked    = isRelinked
        ),
        location  = location,
        hashtags  = hashtags,
        mentions  = mentions,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

inline fun com.linker.app.data.local.entity.LinkType.toDomain(): LinkType = when (this) {
    com.linker.app.data.local.entity.LinkType.FEED  -> LinkType.FEED
    com.linker.app.data.local.entity.LinkType.VIDEO -> LinkType.VIDEO
    com.linker.app.data.local.entity.LinkType.REEL  -> LinkType.REEL
}

inline fun LinkType.toEntity(): com.linker.app.data.local.entity.LinkType = when (this) {
    LinkType.FEED  -> com.linker.app.data.local.entity.LinkType.FEED
    LinkType.VIDEO -> com.linker.app.data.local.entity.LinkType.VIDEO
    LinkType.REEL  -> com.linker.app.data.local.entity.LinkType.REEL
}

fun Link.toEntity(): LinkEntity {
    // Extract legacy flat structure from MediaItem sealed class
    val mediaUrls = mediaItems.map { it.url }
    val thumbnailUrl = mediaItems.filterIsInstance<MediaItem.Video>().firstOrNull()?.thumbnailUrl
    val videoDuration = mediaItems.filterIsInstance<MediaItem.Video>().firstOrNull()?.duration
    val aspectRatio = mediaItems.firstOrNull()?.aspectRatio
    
    return LinkEntity(
        linkId = linkId,
        authorId = author.userId,
        linkType = linkType.toEntity(),
        description = description,
        mediaUrls = mediaUrls,
        thumbnailUrl = thumbnailUrl,
        videoDuration = videoDuration,
        aspectRatio = aspectRatio,
        likesCount = engagement.likesCount,
        commentsCount = engagement.commentsCount,
        sharesCount = engagement.sharesCount,
        relinksCount = engagement.relinksCount,
        savesCount = engagement.savesCount,
        viewsCount = engagement.viewsCount,
        isLiked = engagement.isLiked,
        isSaved = engagement.isSaved,
        isRelinked = engagement.isRelinked,
        location = location,
        hashtags = hashtags,
        mentions = mentions,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastSyncedAt = System.currentTimeMillis()
    )
}

// ── StoryEntity ↔ Story ───────────────────────────────────────────────────────

/**
 * Convert StoryEntity to domain Story model.
 * If author is null, creates a placeholder deleted user.
 * 
 * @param author Author user (nullable)
 * @return Story domain model
 */
fun StoryEntity.toDomain(author: User?): Story {
    val safeAuthor = author ?: User.deletedUser(authorId)
    
    return Story(
        storyId      = storyId,
        author       = StoryAuthor.from(safeAuthor),
        mediaUrl     = mediaUrl,
        mediaType    = mediaType.toDomain(),
        thumbnailUrl = thumbnailUrl,
        duration     = duration,
        caption      = caption,
        viewsCount   = viewsCount,
        isViewed     = isViewed,
        createdAt    = createdAt,
        expiresAt    = expiresAt
    )
}

inline fun com.linker.app.data.local.entity.StoryMediaType.toDomain(): StoryMediaType = when (this) {
    com.linker.app.data.local.entity.StoryMediaType.IMAGE -> StoryMediaType.IMAGE
    com.linker.app.data.local.entity.StoryMediaType.VIDEO -> StoryMediaType.VIDEO
}

fun Story.toEntity(): StoryEntity = StoryEntity(
    storyId = storyId,
    authorId = author.userId,
    mediaUrl = mediaUrl,
    mediaType = when (mediaType) {
        StoryMediaType.IMAGE -> com.linker.app.data.local.entity.StoryMediaType.IMAGE
        StoryMediaType.VIDEO -> com.linker.app.data.local.entity.StoryMediaType.VIDEO
    },
    thumbnailUrl = thumbnailUrl,
    duration = duration,
    caption = caption,
    viewsCount = viewsCount,
    isViewed = isViewed,
    createdAt = createdAt,
    expiresAt = expiresAt,
    lastSyncedAt = System.currentTimeMillis()
)

// ── NoteEntity ↔ Note ─────────────────────────────────────────────────────────

/**
 * Convert NoteEntity to domain Note model (sealed class).
 * If author is null, creates a placeholder deleted user.
 * 
 * @param author Author user (nullable)
 * @return Note domain model (Text, Music, or Countdown)
 */
fun NoteEntity.toDomain(author: User?): Note {
    val safeAuthor = author ?: User.deletedUser(authorId)
    val noteAuthor = NoteAuthor.from(safeAuthor)
    
    return when (noteType.toDomain()) {
        NoteType.TEXT -> Note.Text(
            noteId = noteId,
            author = noteAuthor,
            content = content,
            backgroundColor = backgroundColor,
            textColor = textColor,
            createdAt = createdAt,
            expiresAt = expiresAt
        )
        NoteType.MUSIC -> Note.Music(
            noteId = noteId,
            author = noteAuthor,
            content = content,
            musicTrackId = musicTrackId ?: "",
            musicTrackName = musicTrackName ?: "",
            musicArtistName = musicArtistName ?: "",
            musicAlbumArt = musicAlbumArt,
            backgroundColor = backgroundColor,
            textColor = textColor,
            createdAt = createdAt,
            expiresAt = expiresAt
        )
        NoteType.COUNTDOWN -> Note.Countdown(
            noteId = noteId,
            author = noteAuthor,
            content = content,
            countdownTargetTime = countdownTargetTime ?: 0L,
            countdownTitle = countdownTitle ?: "",
            backgroundColor = backgroundColor,
            textColor = textColor,
            createdAt = createdAt,
            expiresAt = expiresAt
        )
        NoteType.LOCATION -> {
            val parts = content.split("|", limit = 4)
            val latitude = parts.getOrNull(0)?.toDoubleOrNull() ?: 0.0
            val longitude = parts.getOrNull(1)?.toDoubleOrNull() ?: 0.0
            val placeName = parts.getOrNull(2) ?: ""
            val mapPreviewUrl = parts.getOrNull(3)?.takeIf { it.isNotBlank() }
            Note.Location(
                noteId = noteId,
                author = noteAuthor,
                latitude = latitude,
                longitude = longitude,
                placeName = placeName,
                mapPreviewUrl = mapPreviewUrl,
                backgroundColor = backgroundColor,
                textColor = textColor,
                createdAt = createdAt,
                expiresAt = expiresAt
            )
        }
        NoteType.GIF -> {
            val parts = content.split("|", limit = 3)
            val gifUrl = parts.getOrNull(0) ?: ""
            val aspectRatio = parts.getOrNull(1)?.toFloatOrNull()
            val textContent = parts.getOrNull(2) ?: ""
            Note.Gif(
                noteId = noteId,
                author = noteAuthor,
                content = textContent,
                gifUrl = gifUrl,
                aspectRatio = aspectRatio,
                backgroundColor = backgroundColor,
                textColor = textColor,
                createdAt = createdAt,
                expiresAt = expiresAt
            )
        }
    }
}

inline fun com.linker.app.data.local.entity.NoteType.toDomain(): NoteType = when (this) {
    com.linker.app.data.local.entity.NoteType.TEXT      -> NoteType.TEXT
    com.linker.app.data.local.entity.NoteType.MUSIC     -> NoteType.MUSIC
    com.linker.app.data.local.entity.NoteType.COUNTDOWN -> NoteType.COUNTDOWN
    com.linker.app.data.local.entity.NoteType.LOCATION  -> NoteType.LOCATION
    com.linker.app.data.local.entity.NoteType.GIF       -> NoteType.GIF
}

fun Note.toEntity(): NoteEntity = when (this) {
    is Note.Text -> NoteEntity(
        noteId = noteId,
        authorId = author.userId,
        noteType = com.linker.app.data.local.entity.NoteType.TEXT,
        content = content,
        musicTrackId = null,
        musicTrackName = null,
        musicArtistName = null,
        musicAlbumArt = null,
        countdownTargetTime = null,
        countdownTitle = null,
        backgroundColor = backgroundColor,
        textColor = textColor,
        createdAt = createdAt,
        expiresAt = expiresAt,
        lastSyncedAt = System.currentTimeMillis()
    )
    is Note.Music -> NoteEntity(
        noteId = noteId,
        authorId = author.userId,
        noteType = com.linker.app.data.local.entity.NoteType.MUSIC,
        content = content,
        musicTrackId = musicTrackId,
        musicTrackName = musicTrackName,
        musicArtistName = musicArtistName,
        musicAlbumArt = musicAlbumArt,
        countdownTargetTime = null,
        countdownTitle = null,
        backgroundColor = backgroundColor,
        textColor = textColor,
        createdAt = createdAt,
        expiresAt = expiresAt,
        lastSyncedAt = System.currentTimeMillis()
    )
    is Note.Countdown -> NoteEntity(
        noteId = noteId,
        authorId = author.userId,
        noteType = com.linker.app.data.local.entity.NoteType.COUNTDOWN,
        content = content,
        musicTrackId = null,
        musicTrackName = null,
        musicArtistName = null,
        musicAlbumArt = null,
        countdownTargetTime = countdownTargetTime,
        countdownTitle = countdownTitle,
        backgroundColor = backgroundColor,
        textColor = textColor,
        createdAt = createdAt,
        expiresAt = expiresAt,
        lastSyncedAt = System.currentTimeMillis()
    )
    is Note.Location -> NoteEntity(
        noteId = noteId,
        authorId = author.userId,
        noteType = com.linker.app.data.local.entity.NoteType.LOCATION,
        content = "$latitude|$longitude|$placeName|${mapPreviewUrl ?: ""}",
        musicTrackId = null,
        musicTrackName = null,
        musicArtistName = null,
        musicAlbumArt = null,
        countdownTargetTime = null,
        countdownTitle = null,
        backgroundColor = backgroundColor,
        textColor = textColor,
        createdAt = createdAt,
        expiresAt = expiresAt,
        lastSyncedAt = System.currentTimeMillis()
    )
    is Note.Gif -> NoteEntity(
        noteId = noteId,
        authorId = author.userId,
        noteType = com.linker.app.data.local.entity.NoteType.GIF,
        content = "$gifUrl|${aspectRatio ?: 1f}|$content",
        musicTrackId = null,
        musicTrackName = null,
        musicArtistName = null,
        musicAlbumArt = null,
        countdownTargetTime = null,
        countdownTitle = null,
        backgroundColor = backgroundColor,
        textColor = textColor,
        createdAt = createdAt,
        expiresAt = expiresAt,
        lastSyncedAt = System.currentTimeMillis()
    )
}

// ── ChatEntity ↔ Chat / MessageEntity ↔ Message ───────────────────────────────

fun ChatEntity.toDomain(participants: List<User>, lastMessage: Message?): Chat {
    val adminIds = if (chatType == com.linker.app.data.local.entity.ChatType.GROUP) {
        participantIds.take(1)
    } else {
        emptyList()
    }
    
    return Chat(
        chatId           = chatId,
        chatType         = chatType.toDomain(),
        chatName         = chatName,
        chatImageUrl     = chatImageUrl,
        participants     = participants,
        lastMessage      = lastMessage,
        unreadCount      = unreadCount,
        isPinned         = isPinned,
        isMuted          = isMuted,
        isArchived       = isArchived,
        isBlocked        = isBlocked,
        isFavorited      = isFavorited,
        theme            = theme,
        createdAt        = createdAt,
        updatedAt        = updatedAt,
        groupAdminIds    = adminIds,
        groupCreatedBy   = adminIds.firstOrNull()
    )
}

inline fun com.linker.app.data.local.entity.ChatType.toDomain(): ChatType = when (this) {
    com.linker.app.data.local.entity.ChatType.PRIVATE -> ChatType.PRIVATE
    com.linker.app.data.local.entity.ChatType.GROUP   -> ChatType.GROUP
}

fun Chat.toEntity(): ChatEntity = ChatEntity(
    chatId = chatId,
    chatType = when (chatType) {
        ChatType.PRIVATE -> com.linker.app.data.local.entity.ChatType.PRIVATE
        ChatType.GROUP   -> com.linker.app.data.local.entity.ChatType.GROUP
    },
    chatName = chatName,
    chatImageUrl = chatImageUrl,
    participantIds = participants.map { it.userId },
    lastMessageId = lastMessage?.messageId,
    lastMessageText = lastMessage?.content ?: lastMessage?.let { 
        when (it.messageType) {
            MessageType.IMAGE -> "📷 Image"
            MessageType.VIDEO -> "🎥 Video"
            MessageType.GIF -> "GIF"
            MessageType.LINK -> "🔗 Link"
            MessageType.AUDIO -> "🎵 Audio"
            MessageType.FILE -> "📄 File"
            MessageType.LOCATION -> "📍 Location"
            MessageType.CONTACT -> "👤 Contact"
            MessageType.STICKER -> "Sticker"
            else -> "Message"
        }
    },
    lastMessageAt = lastMessage?.createdAt,
    unreadCount = unreadCount,
    isPinned = isPinned,
    isMuted = isMuted,
    isArchived = isArchived,
    isBlocked = isBlocked,
    isFavorited = isFavorited,
    theme = theme,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastSyncedAt = System.currentTimeMillis()
)

/**
 * Convert MessageEntity to domain Message model.
 * If sender is null, creates a placeholder deleted user.
 * 
 * @param sender Message sender (nullable)
 * @param sharedLink Shared link if message type is LINK
 * @param replyToMessage Lightweight reference to the original message if this is a reply
 * @return Message domain model
 */
fun MessageEntity.toDomain(sender: User?, sharedLink: Link? = null, replyToMessage: MessageReference? = null): Message {
    val safeSender = sender ?: User.deletedUser(senderId)
    
    return Message(
        messageId          = messageId,
        chatId             = chatId,
        sender             = UserReference.from(safeSender),
        messageType        = messageType.toDomain(),
        content            = content,
        mediaUrl           = mediaUrl,
        thumbnailUrl       = thumbnailUrl,
        mediaWidth         = mediaWidth,
        mediaHeight        = mediaHeight,
        mediaDuration      = mediaDuration,
        sharedLink         = sharedLink,
        replyToMessage     = replyToMessage,
        reactions          = reactions,
        isEdited           = isEdited,
        isDeleted          = isDeleted,
        deletedForEveryone = deletedForEveryone,
        messageStatus      = messageStatus.toDomain(),
        deliveryMethod     = deliveryMethod.toDomain(),
        createdAt          = createdAt,
        updatedAt          = updatedAt,
        deliveredAt        = deliveredAt,
        readAt             = readAt
    )
}

inline fun com.linker.app.data.local.entity.MessageType.toDomain(): MessageType = when (this) {
    com.linker.app.data.local.entity.MessageType.TEXT  -> MessageType.TEXT
    com.linker.app.data.local.entity.MessageType.IMAGE -> MessageType.IMAGE
    com.linker.app.data.local.entity.MessageType.VIDEO -> MessageType.VIDEO
    com.linker.app.data.local.entity.MessageType.GIF   -> MessageType.GIF
    com.linker.app.data.local.entity.MessageType.LINK  -> MessageType.LINK
    com.linker.app.data.local.entity.MessageType.AUDIO -> MessageType.AUDIO
    com.linker.app.data.local.entity.MessageType.FILE  -> MessageType.FILE
    com.linker.app.data.local.entity.MessageType.LOCATION -> MessageType.LOCATION
    com.linker.app.data.local.entity.MessageType.CONTACT -> MessageType.CONTACT
    com.linker.app.data.local.entity.MessageType.STICKER -> MessageType.STICKER
}

inline fun com.linker.app.data.local.entity.MessageStatus.toDomain(): MessageStatus = when (this) {
    com.linker.app.data.local.entity.MessageStatus.SENDING   -> MessageStatus.SENDING
    com.linker.app.data.local.entity.MessageStatus.SENT      -> MessageStatus.SENT
    com.linker.app.data.local.entity.MessageStatus.DELIVERED -> MessageStatus.DELIVERED
    com.linker.app.data.local.entity.MessageStatus.READ      -> MessageStatus.READ
    com.linker.app.data.local.entity.MessageStatus.FAILED    -> MessageStatus.FAILED
}

inline fun com.linker.app.data.local.entity.DeliveryMethod.toDomain(): DeliveryMethod = when (this) {
    com.linker.app.data.local.entity.DeliveryMethod.ONLINE      -> DeliveryMethod.ONLINE
    com.linker.app.data.local.entity.DeliveryMethod.BLE         -> DeliveryMethod.BLE
    com.linker.app.data.local.entity.DeliveryMethod.WIFI_DIRECT -> DeliveryMethod.WIFI_DIRECT
}

inline fun MessageStatus.toEntity(): com.linker.app.data.local.entity.MessageStatus = when (this) {
    MessageStatus.SENDING   -> com.linker.app.data.local.entity.MessageStatus.SENDING
    MessageStatus.SENT      -> com.linker.app.data.local.entity.MessageStatus.SENT
    MessageStatus.DELIVERED -> com.linker.app.data.local.entity.MessageStatus.DELIVERED
    MessageStatus.READ      -> com.linker.app.data.local.entity.MessageStatus.READ
    MessageStatus.FAILED    -> com.linker.app.data.local.entity.MessageStatus.FAILED
}

fun Message.toEntity(): MessageEntity = MessageEntity(
    messageId = messageId,
    chatId = chatId,
    senderId = sender.userId,
    messageType = when (messageType) {
        MessageType.TEXT -> com.linker.app.data.local.entity.MessageType.TEXT
        MessageType.IMAGE -> com.linker.app.data.local.entity.MessageType.IMAGE
        MessageType.VIDEO -> com.linker.app.data.local.entity.MessageType.VIDEO
        MessageType.GIF -> com.linker.app.data.local.entity.MessageType.GIF
        MessageType.LINK -> com.linker.app.data.local.entity.MessageType.LINK
        MessageType.AUDIO -> com.linker.app.data.local.entity.MessageType.AUDIO
        MessageType.FILE -> com.linker.app.data.local.entity.MessageType.FILE
        MessageType.LOCATION -> com.linker.app.data.local.entity.MessageType.LOCATION
        MessageType.CONTACT -> com.linker.app.data.local.entity.MessageType.CONTACT
        MessageType.STICKER -> com.linker.app.data.local.entity.MessageType.STICKER
    },
    content = content,
    mediaUrl = mediaUrl,
    thumbnailUrl = thumbnailUrl,
    mediaWidth = mediaWidth,
    mediaHeight = mediaHeight,
    mediaDuration = mediaDuration,
    sharedLinkId = sharedLink?.linkId,
    replyToMessageId = replyToMessage?.messageId,
    forwardedFromMessageId = null,
    reactions = reactions,
    isEdited = isEdited,
    isDeleted = isDeleted,
    deletedForEveryone = deletedForEveryone,
    messageStatus = messageStatus.toEntity(),
    deliveryMethod = when (deliveryMethod) {
        DeliveryMethod.ONLINE -> com.linker.app.data.local.entity.DeliveryMethod.ONLINE
        DeliveryMethod.BLE -> com.linker.app.data.local.entity.DeliveryMethod.BLE
        DeliveryMethod.WIFI_DIRECT -> com.linker.app.data.local.entity.DeliveryMethod.WIFI_DIRECT
    },
    encryptedContent = null,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deliveredAt = deliveredAt,
    readAt = readAt,
    lastSyncedAt = System.currentTimeMillis()
)

// ── Comment / Notification ────────────────────────────────────────────────────

/**
 * Convert CommentEntity to domain Comment model.
 * If author is null, creates a placeholder deleted user.
 * 
 * @param author Comment author (nullable)
 * @return Comment domain model
 */
fun CommentEntity.toDomain(author: User?): Comment {
    val safeAuthor = author ?: User.deletedUser(authorId ?: "unknown")
    
    return Comment(
        commentId       = commentId,
        linkId          = linkId,
        author          = CommentAuthor.from(safeAuthor),
        content         = content,
        gifUrl          = gifUrl,
        parentCommentId = parentCommentId,
        likesCount      = likesCount,
        repliesCount    = repliesCount,
        isLiked         = isLiked,
        isPinned        = isPinned,
        isEdited        = isEdited,
        createdAt       = createdAt,
        updatedAt       = updatedAt
    )
}

/**
 * Convert NotificationEntity to domain Notification model.
 * If actor is null, creates a placeholder deleted user.
 * 
 * @param actor Notification actor (nullable)
 * @return Notification domain model
 */
fun NotificationEntity.toDomain(actor: User?): Notification {
    val safeActor = actor ?: User.deletedUser(actorId)
    
    // Convert legacy string-based target to sealed class
    val target = NotificationTarget.fromLegacy(
        entityType = targetEntityType,
        entityId = targetEntityId,
        secondaryId = null // Could be extracted from actionUrl if needed
    )
    
    return Notification(
        notificationId   = notificationId,
        notificationType = notificationType.toDomain(),
        actor            = NotificationActor.from(safeActor),
        target           = target,
        title            = title,
        message          = message,
        imageUrl         = imageUrl,
        actionUrl        = actionUrl,
        isRead           = isRead,
        createdAt        = createdAt
    )
}

inline fun com.linker.app.data.local.entity.NotificationType.toDomain(): NotificationType = when (this) {
    com.linker.app.data.local.entity.NotificationType.LIKE       -> NotificationType.LIKE
    com.linker.app.data.local.entity.NotificationType.COMMENT    -> NotificationType.COMMENT
    com.linker.app.data.local.entity.NotificationType.REPLY      -> NotificationType.REPLY
    com.linker.app.data.local.entity.NotificationType.FOLLOW     -> NotificationType.FOLLOW
    com.linker.app.data.local.entity.NotificationType.MENTION    -> NotificationType.MENTION
    com.linker.app.data.local.entity.NotificationType.RELINK     -> NotificationType.RELINK
    com.linker.app.data.local.entity.NotificationType.MESSAGE    -> NotificationType.MESSAGE
    com.linker.app.data.local.entity.NotificationType.STORY_VIEW -> NotificationType.STORY_VIEW
    com.linker.app.data.local.entity.NotificationType.LIVE       -> NotificationType.LIVE
}

fun Comment.toEntity(): CommentEntity = CommentEntity(
    commentId = commentId,
    linkId = linkId,
    authorId = author.userId,
    content = content,
    gifUrl = gifUrl,
    parentCommentId = parentCommentId,
    likesCount = likesCount,
    repliesCount = repliesCount,
    isLiked = isLiked,
    isPinned = isPinned,
    isEdited = isEdited,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastSyncedAt = System.currentTimeMillis()
)

fun Notification.toEntity(): NotificationEntity {
    // Convert sealed class target back to legacy string-based format
    val (entityType, entityId) = when (target) {
        is NotificationTarget.LinkTarget -> "link" to target.linkId
        is NotificationTarget.CommentTarget -> "comment" to target.commentId
        is NotificationTarget.UserTarget -> "user" to target.userId
        is NotificationTarget.StoryTarget -> "story" to target.storyId
        is NotificationTarget.MessageTarget -> "message" to target.messageId
        is NotificationTarget.NoTarget -> null to null
    }
    
    return NotificationEntity(
        notificationId = notificationId,
        notificationType = when (notificationType) {
            NotificationType.LIKE -> com.linker.app.data.local.entity.NotificationType.LIKE
            NotificationType.COMMENT -> com.linker.app.data.local.entity.NotificationType.COMMENT
            NotificationType.REPLY -> com.linker.app.data.local.entity.NotificationType.REPLY
            NotificationType.FOLLOW -> com.linker.app.data.local.entity.NotificationType.FOLLOW
            NotificationType.MENTION -> com.linker.app.data.local.entity.NotificationType.MENTION
            NotificationType.RELINK -> com.linker.app.data.local.entity.NotificationType.RELINK
            NotificationType.MESSAGE -> com.linker.app.data.local.entity.NotificationType.MESSAGE
            NotificationType.STORY_VIEW -> com.linker.app.data.local.entity.NotificationType.STORY_VIEW
            NotificationType.LIVE -> com.linker.app.data.local.entity.NotificationType.LIVE
        },
        actorId = actor.userId,
        targetEntityId = entityId,
        targetEntityType = entityType,
        title = title,
        message = message,
        imageUrl = imageUrl,
        actionUrl = actionUrl,
        isRead = isRead,
        createdAt = createdAt,
        lastSyncedAt = System.currentTimeMillis()
    )
}
