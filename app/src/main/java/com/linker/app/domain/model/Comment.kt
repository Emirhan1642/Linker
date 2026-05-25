package com.linker.app.domain.model

/**
 * Domain model for a Comment (supports nested replies).
 *
 * Comments are attached to [Link] posts and support a nesting hierarchy
 * up to [MAX_NESTING_LEVEL] levels deep. A root comment has
 * [parentCommentId] = null and [nestingLevel] = 0.
 *
 * @property commentId Unique comment identifier.
 * @property linkId ID of the [Link] this comment belongs to.
 * @property author Lightweight author reference (use [CommentAuthor.from] to create from [User]).
 * @property content Text content (may be blank if [gifUrl] is present).
 * @property gifUrl URL to a GIF attachment (null for text-only comments).
 * @property parentCommentId ID of the parent comment (null for root comments).
 * @property nestingLevel Depth of nesting (0 = root, 1 = reply, etc.).
 * @property likesCount Number of likes.
 * @property repliesCount Number of direct replies.
 * @property isLiked Whether the current user has liked this comment.
 * @property isPinned Whether this comment is pinned by the post author.
 * @property isEdited Whether this comment has been edited.
 * @property createdAt Creation timestamp (epoch ms).
 * @property updatedAt Last update timestamp (epoch ms).
 */
data class Comment(
    val commentId: String,
    val linkId: String,
    val author: CommentAuthor,
    val content: String,
    val gifUrl: String? = null,
    val parentCommentId: String? = null,
    val nestingLevel: Int = 0,
    val likesCount: Int = 0,
    val repliesCount: Int = 0,
    val isLiked: Boolean = false,
    val isPinned: Boolean = false,
    val isEdited: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
) {
    init {
        require(commentId.isNotBlank()) { "commentId cannot be blank" }
        require(linkId.isNotBlank()) { "linkId cannot be blank" }
        require(content.isNotBlank() || gifUrl != null) { "Comment must have either content or gifUrl" }
        require(content.length <= MAX_CONTENT_LENGTH) { "Content exceeds maximum length of $MAX_CONTENT_LENGTH" }
        require(likesCount >= 0) { "likesCount cannot be negative" }
        require(repliesCount >= 0) { "repliesCount cannot be negative" }
        require(createdAt > 0) { "createdAt must be positive" }
        require(updatedAt >= createdAt) { "updatedAt cannot be before createdAt" }
        require(commentId != parentCommentId) { "Comment cannot be its own parent" }
        require(nestingLevel >= 0) { "nestingLevel cannot be negative" }
        require(nestingLevel <= MAX_NESTING_LEVEL) { "Comment nesting exceeds maximum level of $MAX_NESTING_LEVEL" }
        if (parentCommentId == null) {
            require(nestingLevel == 0) { "Root comment must have nestingLevel = 0" }
        } else {
            require(nestingLevel > 0) { "Reply must have nestingLevel > 0" }
        }
    }

    /** Whether this comment can accept replies (hasn't reached max nesting). */
    fun canHaveReplies(): Boolean = nestingLevel < MAX_NESTING_LEVEL

    /** Whether this is a root-level comment (not a reply). */
    fun isRoot(): Boolean = parentCommentId == null

    companion object {
        /** Maximum allowed content length in characters. */
        const val MAX_CONTENT_LENGTH = 2000

        /** Maximum nesting depth for replies. */
        const val MAX_NESTING_LEVEL = 3

        /**
         * Creates a root comment.
         */
        fun createRoot(
            commentId: String,
            linkId: String,
            author: CommentAuthor,
            content: String,
            gifUrl: String? = null,
            createdAt: Long
        ) = Comment(
            commentId = commentId,
            linkId = linkId,
            author = author,
            content = content,
            gifUrl = gifUrl,
            parentCommentId = null,
            nestingLevel = 0,
            createdAt = createdAt,
            updatedAt = createdAt
        )

        /**
         * Creates a reply to an existing comment.
         */
        fun createReply(
            commentId: String,
            linkId: String,
            author: CommentAuthor,
            content: String,
            parentCommentId: String,
            parentNestingLevel: Int,
            gifUrl: String? = null,
            createdAt: Long
        ) = Comment(
            commentId = commentId,
            linkId = linkId,
            author = author,
            content = content,
            gifUrl = gifUrl,
            parentCommentId = parentCommentId,
            nestingLevel = parentNestingLevel + 1,
            createdAt = createdAt,
            updatedAt = createdAt
        )
    }
}

/**
 * Represents the target entity of a notification.
 *
 * Type-safe discriminated union for notification targets.
 * Replaces raw string-based [targetEntityType] + [targetEntityId] pairs.
 */
sealed class NotificationTarget {
    /**
     * Notification about a Link post.
     *
     * @property linkId ID of the target link.
     */
    data class LinkTarget(val linkId: String) : NotificationTarget() {
        init {
            require(linkId.isNotBlank()) { "linkId cannot be blank" }
        }
    }

    /**
     * Notification about a Comment.
     *
     * @property commentId ID of the target comment.
     * @property linkId ID of the link the comment belongs to.
     */
    data class CommentTarget(
        val commentId: String,
        val linkId: String
    ) : NotificationTarget() {
        init {
            require(commentId.isNotBlank()) { "commentId cannot be blank" }
            require(linkId.isNotBlank()) { "linkId cannot be blank" }
        }
    }

    /**
     * Notification about a User (e.g., new follower).
     *
     * @property userId ID of the target user.
     */
    data class UserTarget(val userId: String) : NotificationTarget() {
        init {
            require(userId.isNotBlank()) { "userId cannot be blank" }
        }
    }

    /**
     * Notification about a Story.
     *
     * @property storyId ID of the target story.
     * @property authorId ID of the story author.
     */
    data class StoryTarget(
        val storyId: String,
        val authorId: String
    ) : NotificationTarget() {
        init {
            require(storyId.isNotBlank()) { "storyId cannot be blank" }
            require(authorId.isNotBlank()) { "authorId cannot be blank" }
        }
    }

    /**
     * Notification about a Message.
     *
     * @property messageId ID of the target message.
     * @property chatId ID of the chat the message belongs to.
     */
    data class MessageTarget(
        val messageId: String,
        val chatId: String
    ) : NotificationTarget() {
        init {
            require(messageId.isNotBlank()) { "messageId cannot be blank" }
            require(chatId.isNotBlank()) { "chatId cannot be blank" }
        }
    }

    /**
     * Notification with no specific target (e.g., system announcements).
     */
    object NoTarget : NotificationTarget()

    companion object {
        /**
         * Creates a [NotificationTarget] from legacy string-based fields.
         *
         * @param entityType Type string ("link", "comment", "user", "story", "message").
         * @param entityId Primary entity ID.
         * @param secondaryId Secondary ID (e.g., linkId for comments).
         * @return Corresponding [NotificationTarget] or [NoTarget] if unknown.
         */
        fun fromLegacy(
            entityType: String?,
            entityId: String?,
            secondaryId: String? = null
        ): NotificationTarget {
            if (entityType == null || entityId == null) return NoTarget

            return when (entityType.lowercase()) {
                "link" -> LinkTarget(entityId)
                "comment" -> CommentTarget(
                    commentId = entityId,
                    linkId = secondaryId ?: ""
                )
                "user" -> UserTarget(entityId)
                "story" -> StoryTarget(
                    storyId = entityId,
                    authorId = secondaryId ?: ""
                )
                "message" -> MessageTarget(
                    messageId = entityId,
                    chatId = secondaryId ?: ""
                )
                else -> NoTarget
            }
        }
    }
}

/**
 * Domain model for a Notification.
 *
 * Represents a push/in-app notification triggered by user activity.
 *
 * @property notificationId Unique notification identifier.
 * @property notificationType Type of notification (determines UI and grouping behavior).
 * @property actor Lightweight actor reference (use [NotificationActor.from] to create from [User]).
 * @property target Type-safe target entity (link, comment, user, etc.).
 * @property title Notification title (e.g., "John liked your post").
 * @property message Notification body text.
 * @property imageUrl Optional image URL for rich notifications.
 * @property actionUrl Deep link URL for navigation on tap.
 * @property isRead Whether the user has read this notification.
 * @property createdAt Creation timestamp (epoch ms).
 */
data class Notification(
    val notificationId: String,
    val notificationType: NotificationType,
    val actor: NotificationActor,
    val target: NotificationTarget,
    val title: String,
    val message: String,
    val imageUrl: String?,
    val actionUrl: String?,
    val isRead: Boolean,
    val createdAt: Long
) {
    init {
        require(notificationId.isNotBlank()) { "notificationId cannot be blank" }
        require(title.isNotBlank()) { "title cannot be blank" }
        require(title.length <= MAX_TITLE_LENGTH) { "title exceeds maximum length of $MAX_TITLE_LENGTH" }
        require(message.isNotBlank()) { "message cannot be blank" }
        require(message.length <= MAX_MESSAGE_LENGTH) { "message exceeds maximum length of $MAX_MESSAGE_LENGTH" }
        require(createdAt > 0) { "createdAt must be positive" }
    }

    /**
     * Whether this notification is older than 30 days and should be cleaned up.
     */
    fun isExpired(): Boolean {
        val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000
        return System.currentTimeMillis() - createdAt > thirtyDaysMs
    }

    companion object {
        /** Maximum notification title length. */
        const val MAX_TITLE_LENGTH = 100

        /** Maximum notification message length. */
        const val MAX_MESSAGE_LENGTH = 500
    }
}

/**
 * Notification priority levels.
 */
enum class NotificationPriority {
    /** Low priority — batched and delivered silently (e.g., likes, story views). */
    LOW,
    /** Medium priority — standard notification (e.g., comments, follows). */
    MEDIUM,
    /** High priority — immediate delivery with sound (e.g., messages, mentions). */
    HIGH
}

/**
 * Notification type discriminator with display metadata.
 *
 * Each type carries its own display properties and grouping behavior
 * for the notification list UI.
 *
 * @property displayName Human-readable name for this notification type.
 * @property iconName Name of the icon resource to display.
 * @property priority Default delivery priority.
 * @property isGroupable Whether notifications of this type can be collapsed (e.g., "3 people liked your post").
 */
enum class NotificationType(
    val displayName: String,
    val iconName: String,
    val priority: NotificationPriority,
    val isGroupable: Boolean
) {
    /** Someone liked a post or comment. */
    LIKE("Like", "ic_heart", NotificationPriority.LOW, true),
    /** Someone commented on a post. */
    COMMENT("Comment", "ic_comment", NotificationPriority.MEDIUM, true),
    /** Someone replied to a comment. */
    REPLY("Reply", "ic_reply", NotificationPriority.MEDIUM, true),
    /** Someone followed the user. */
    FOLLOW("Follow", "ic_person_add", NotificationPriority.MEDIUM, true),
    /** Someone mentioned the user in a post or comment. */
    MENTION("Mention", "ic_at", NotificationPriority.HIGH, false),
    /** Someone shared (relinked) a post. */
    RELINK("Relink", "ic_share", NotificationPriority.LOW, true),
    /** New direct message received. */
    MESSAGE("Message", "ic_message", NotificationPriority.HIGH, false),
    /** Someone viewed the user's story. */
    STORY_VIEW("Story View", "ic_eye", NotificationPriority.LOW, true),
    /** Someone started a live stream. */
    LIVE("Live", "ic_live", NotificationPriority.HIGH, false);

    /**
     * Returns the Android notification channel ID for this type.
     */
    fun getChannelId(): String = "linker_${name.lowercase()}"
}
