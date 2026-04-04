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

fun UserEntity.toDomain(): User = User(
    userId            = userId,
    username          = username,
    displayName       = displayName,
    email             = email,
    phoneNumber       = phoneNumber,
    bio               = bio,
    profileImageUrl   = profileImageUrl,
    coverImageUrl     = coverImageUrl,
    isVerified        = isVerified,
    followersCount    = followersCount,
    followingCount    = followingCount,
    likesCount        = likesCount,
    isFollowing       = isFollowing,
    isFollowedBy      = isFollowedBy,
    isBlocked         = isBlocked,
    isMuted           = isMuted,
    isPrivate         = isPrivate,
    followRequestSent = followRequestSent,
    hideFollowLists   = hideFollowLists,
    createdAt         = createdAt,
    updatedAt         = updatedAt
)

fun User.toEntity(): UserEntity = UserEntity(
    userId            = userId,
    username          = username,
    displayName       = displayName,
    email             = email,
    phoneNumber       = phoneNumber,
    bio               = bio,
    profileImageUrl   = profileImageUrl,
    coverImageUrl     = coverImageUrl,
    isVerified        = isVerified,
    followersCount    = followersCount,
    followingCount    = followingCount,
    likesCount        = likesCount,
    isFollowing       = isFollowing,
    isFollowedBy      = isFollowedBy,
    isBlocked         = isBlocked,
    isMuted           = isMuted,
    isPrivate         = isPrivate,
    followRequestSent = followRequestSent,
    hideFollowLists   = hideFollowLists,
    createdAt         = createdAt,
    updatedAt         = updatedAt,
    lastSyncedAt      = System.currentTimeMillis()
)

// ── LinkEntity ↔ Link ─────────────────────────────────────────────────────────

fun LinkEntity.toDomain(author: User): Link = Link(
    linkId        = linkId, author = author, linkType = linkType.toDomain(),
    description   = description, mediaUrls = mediaUrls, thumbnailUrl = thumbnailUrl,
    videoDuration = videoDuration, aspectRatio = aspectRatio,
    likesCount    = likesCount, commentsCount = commentsCount,
    sharesCount   = sharesCount, relinksCount = relinksCount,
    savesCount    = savesCount, viewsCount = viewsCount,
    isLiked = isLiked, isSaved = isSaved, isRelinked = isRelinked,
    location = location, hashtags = hashtags, mentions = mentions,
    createdAt = createdAt, updatedAt = updatedAt
)

fun com.linker.app.data.local.entity.LinkType.toDomain(): LinkType = when (this) {
    com.linker.app.data.local.entity.LinkType.FEED  -> LinkType.FEED
    com.linker.app.data.local.entity.LinkType.VIDEO -> LinkType.VIDEO
    com.linker.app.data.local.entity.LinkType.REEL  -> LinkType.REEL
}

fun LinkType.toEntity(): com.linker.app.data.local.entity.LinkType = when (this) {
    LinkType.FEED  -> com.linker.app.data.local.entity.LinkType.FEED
    LinkType.VIDEO -> com.linker.app.data.local.entity.LinkType.VIDEO
    LinkType.REEL  -> com.linker.app.data.local.entity.LinkType.REEL
}

fun Link.toEntity(): LinkEntity = LinkEntity(
    linkId = linkId, authorId = author.userId, linkType = linkType.toEntity(),
    description = description, mediaUrls = mediaUrls, thumbnailUrl = thumbnailUrl,
    videoDuration = videoDuration, aspectRatio = aspectRatio,
    likesCount = likesCount, commentsCount = commentsCount,
    sharesCount = sharesCount, relinksCount = relinksCount,
    savesCount = savesCount, viewsCount = viewsCount,
    isLiked = isLiked, isSaved = isSaved, isRelinked = isRelinked,
    location = location, hashtags = hashtags, mentions = mentions,
    createdAt = createdAt, updatedAt = updatedAt,
    lastSyncedAt = System.currentTimeMillis()
)

// ── StoryEntity ↔ Story ───────────────────────────────────────────────────────

fun StoryEntity.toDomain(author: User): Story = Story(
    storyId = storyId, author = author, mediaUrl = mediaUrl,
    mediaType = mediaType.toDomain(), thumbnailUrl = thumbnailUrl,
    duration = duration, caption = caption, viewsCount = viewsCount,
    isViewed = isViewed, createdAt = createdAt, expiresAt = expiresAt
)

fun com.linker.app.data.local.entity.StoryMediaType.toDomain(): StoryMediaType = when (this) {
    com.linker.app.data.local.entity.StoryMediaType.IMAGE -> StoryMediaType.IMAGE
    com.linker.app.data.local.entity.StoryMediaType.VIDEO -> StoryMediaType.VIDEO
}

// ── NoteEntity ↔ Note ─────────────────────────────────────────────────────────

fun NoteEntity.toDomain(author: User): Note = Note(
    noteId = noteId, author = author, noteType = noteType.toDomain(),
    content = content, musicTrackId = musicTrackId, musicTrackName = musicTrackName,
    musicArtistName = musicArtistName, musicAlbumArt = musicAlbumArt,
    countdownTargetTime = countdownTargetTime, countdownTitle = countdownTitle,
    backgroundColor = backgroundColor, textColor = textColor,
    createdAt = createdAt, expiresAt = expiresAt
)

fun com.linker.app.data.local.entity.NoteType.toDomain(): NoteType = when (this) {
    com.linker.app.data.local.entity.NoteType.TEXT      -> NoteType.TEXT
    com.linker.app.data.local.entity.NoteType.MUSIC     -> NoteType.MUSIC
    com.linker.app.data.local.entity.NoteType.COUNTDOWN -> NoteType.COUNTDOWN
}

// ── ChatEntity ↔ Chat / MessageEntity ↔ Message ───────────────────────────────

fun ChatEntity.toDomain(participants: List<User>, lastMessage: Message?): Chat = Chat(
    chatId = chatId, chatType = chatType.toDomain(), chatName = chatName,
    chatImageUrl = chatImageUrl, participants = participants, lastMessage = lastMessage,
    unreadCount = unreadCount, isPinned = isPinned, isMuted = isMuted,
    isArchived = isArchived, isBlocked = isBlocked, isFavorited = isFavorited, theme = theme, createdAt = createdAt, updatedAt = updatedAt
)

fun com.linker.app.data.local.entity.ChatType.toDomain(): ChatType = when (this) {
    com.linker.app.data.local.entity.ChatType.PRIVATE -> ChatType.PRIVATE
    com.linker.app.data.local.entity.ChatType.GROUP   -> ChatType.GROUP
}

fun MessageEntity.toDomain(sender: User, sharedLink: Link? = null, replyToMessage: Message? = null): Message = Message(
    messageId = messageId, chatId = chatId, sender = sender,
    messageType = messageType.toDomain(), content = content,
    mediaUrl = mediaUrl, thumbnailUrl = thumbnailUrl,
    mediaWidth = mediaWidth, mediaHeight = mediaHeight, mediaDuration = mediaDuration,
    sharedLink = sharedLink, replyToMessage = replyToMessage, reactions = reactions,
    isEdited = isEdited, isDeleted = isDeleted, deletedForEveryone = deletedForEveryone,
    messageStatus = messageStatus.toDomain(), deliveryMethod = deliveryMethod.toDomain(),
    createdAt = createdAt, updatedAt = updatedAt, deliveredAt = deliveredAt, readAt = readAt
)

fun com.linker.app.data.local.entity.MessageType.toDomain(): MessageType = when (this) {
    com.linker.app.data.local.entity.MessageType.TEXT  -> MessageType.TEXT
    com.linker.app.data.local.entity.MessageType.IMAGE -> MessageType.IMAGE
    com.linker.app.data.local.entity.MessageType.VIDEO -> MessageType.VIDEO
    com.linker.app.data.local.entity.MessageType.GIF   -> MessageType.GIF
    com.linker.app.data.local.entity.MessageType.LINK  -> MessageType.LINK
    com.linker.app.data.local.entity.MessageType.AUDIO -> MessageType.AUDIO
}

fun com.linker.app.data.local.entity.MessageStatus.toDomain(): MessageStatus = when (this) {
    com.linker.app.data.local.entity.MessageStatus.SENDING   -> MessageStatus.SENDING
    com.linker.app.data.local.entity.MessageStatus.SENT      -> MessageStatus.SENT
    com.linker.app.data.local.entity.MessageStatus.DELIVERED -> MessageStatus.DELIVERED
    com.linker.app.data.local.entity.MessageStatus.READ      -> MessageStatus.READ
    com.linker.app.data.local.entity.MessageStatus.FAILED    -> MessageStatus.FAILED
}

fun com.linker.app.data.local.entity.DeliveryMethod.toDomain(): DeliveryMethod = when (this) {
    com.linker.app.data.local.entity.DeliveryMethod.ONLINE      -> DeliveryMethod.ONLINE
    com.linker.app.data.local.entity.DeliveryMethod.BLE         -> DeliveryMethod.BLE
    com.linker.app.data.local.entity.DeliveryMethod.WIFI_DIRECT -> DeliveryMethod.WIFI_DIRECT
}

fun MessageStatus.toEntity(): com.linker.app.data.local.entity.MessageStatus = when (this) {
    MessageStatus.SENDING   -> com.linker.app.data.local.entity.MessageStatus.SENDING
    MessageStatus.SENT      -> com.linker.app.data.local.entity.MessageStatus.SENT
    MessageStatus.DELIVERED -> com.linker.app.data.local.entity.MessageStatus.DELIVERED
    MessageStatus.READ      -> com.linker.app.data.local.entity.MessageStatus.READ
    MessageStatus.FAILED    -> com.linker.app.data.local.entity.MessageStatus.FAILED
}

// ── Comment / Notification ────────────────────────────────────────────────────

fun CommentEntity.toDomain(author: User): Comment = Comment(
    commentId = commentId, linkId = linkId, author = author, content = content,
    gifUrl = gifUrl, parentCommentId = parentCommentId, likesCount = likesCount,
    repliesCount = repliesCount, isLiked = isLiked, isPinned = isPinned,
    isEdited = isEdited, createdAt = createdAt, updatedAt = updatedAt
)

fun NotificationEntity.toDomain(actor: User): Notification = Notification(
    notificationId = notificationId, notificationType = notificationType.toDomain(),
    actor = actor, targetEntityId = targetEntityId, targetEntityType = targetEntityType,
    title = title, message = message, imageUrl = imageUrl, actionUrl = actionUrl,
    isRead = isRead, createdAt = createdAt
)

fun com.linker.app.data.local.entity.NotificationType.toDomain(): NotificationType = when (this) {
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
