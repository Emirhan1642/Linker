package com.linker.app.domain.model

/**
 * Lightweight user reference for use in other domain models.
 *
 * Avoids circular dependencies and reduces memory footprint by holding
 * only the essential user data needed for display purposes.
 * Use this instead of full [User] objects in nested contexts (posts, comments, etc.).
 *
 * @property userId Unique user identifier (Firebase UID).
 * @property username Unique username handle.
 * @property displayName User's display name.
 * @property profileImageUrl Profile picture URL.
 * @property isVerified Whether the user has a verified badge.
 */
data class UserReference(
    val userId: String,
    val username: String,
    val displayName: String,
    val profileImageUrl: String?,
    val isVerified: Boolean = false
) {
    init {
        require(userId.isNotBlank()) { "userId cannot be blank" }
        require(username.isNotBlank()) { "username cannot be blank" }
        require(displayName.isNotBlank()) { "displayName cannot be blank" }
    }

    companion object {
        /**
         * Creates a [UserReference] from a full [User] object.
         */
        fun from(user: User) = UserReference(
            userId = user.userId,
            username = user.username,
            displayName = user.displayName,
            profileImageUrl = user.profileImageUrl,
            isVerified = user.isVerified
        )

        /**
         * Creates a placeholder reference for deleted users.
         */
        fun deletedUser(userId: String) = UserReference(
            userId = userId,
            username = "[deleted]",
            displayName = "Deleted User",
            profileImageUrl = null,
            isVerified = false
        )
    }
}

/**
 * Lightweight author reference for Link posts.
 *
 * Extends [UserReference] with follower count for display in post headers.
 *
 * @property userId Unique user identifier.
 * @property username Unique username handle.
 * @property displayName User's display name.
 * @property profileImageUrl Profile picture URL.
 * @property isVerified Whether the user has a verified badge.
 * @property followersCount Number of followers (for display in post header).
 */
data class LinkAuthor(
    val userId: String,
    val username: String,
    val displayName: String,
    val profileImageUrl: String?,
    val isVerified: Boolean = false,
    val followersCount: Int = 0
) {
    init {
        require(userId.isNotBlank()) { "userId cannot be blank" }
        require(username.isNotBlank()) { "username cannot be blank" }
        require(displayName.isNotBlank()) { "displayName cannot be blank" }
        require(followersCount >= 0) { "followersCount cannot be negative" }
    }

    /**
     * Converts to a basic [UserReference].
     */
    fun toUserReference() = UserReference(
        userId = userId,
        username = username,
        displayName = displayName,
        profileImageUrl = profileImageUrl,
        isVerified = isVerified
    )

    companion object {
        /**
         * Creates a [LinkAuthor] from a full [User] object.
         */
        fun from(user: User) = LinkAuthor(
            userId = user.userId,
            username = user.username,
            displayName = user.displayName,
            profileImageUrl = user.profileImageUrl,
            isVerified = user.isVerified,
            followersCount = user.followersCount
        )
    }
}

/**
 * Lightweight author reference for Comment posts.
 *
 * Minimal data needed for comment display.
 *
 * @property userId Unique user identifier.
 * @property username Unique username handle.
 * @property displayName User's display name.
 * @property profileImageUrl Profile picture URL.
 * @property isVerified Whether the user has a verified badge.
 */
data class CommentAuthor(
    val userId: String,
    val username: String,
    val displayName: String,
    val profileImageUrl: String?,
    val isVerified: Boolean = false
) {
    init {
        require(userId.isNotBlank()) { "userId cannot be blank" }
        require(username.isNotBlank()) { "username cannot be blank" }
        require(displayName.isNotBlank()) { "displayName cannot be blank" }
    }

    /**
     * Converts to a basic [UserReference].
     */
    fun toUserReference() = UserReference(
        userId = userId,
        username = username,
        displayName = displayName,
        profileImageUrl = profileImageUrl,
        isVerified = isVerified
    )

    companion object {
        /**
         * Creates a [CommentAuthor] from a full [User] object.
         */
        fun from(user: User) = CommentAuthor(
            userId = user.userId,
            username = user.username,
            displayName = user.displayName,
            profileImageUrl = user.profileImageUrl,
            isVerified = user.isVerified
        )
    }
}

/**
 * Lightweight author reference for Note posts.
 *
 * Minimal data needed for note display in chat list header.
 *
 * @property userId Unique user identifier.
 * @property username Unique username handle.
 * @property displayName User's display name.
 * @property profileImageUrl Profile picture URL.
 */
data class NoteAuthor(
    val userId: String,
    val username: String,
    val displayName: String,
    val profileImageUrl: String?
) {
    init {
        require(userId.isNotBlank()) { "userId cannot be blank" }
        require(username.isNotBlank()) { "username cannot be blank" }
        require(displayName.isNotBlank()) { "displayName cannot be blank" }
    }

    /**
     * Converts to a basic [UserReference].
     */
    fun toUserReference() = UserReference(
        userId = userId,
        username = username,
        displayName = displayName,
        profileImageUrl = profileImageUrl,
        isVerified = false
    )

    companion object {
        /**
         * Creates a [NoteAuthor] from a full [User] object.
         */
        fun from(user: User) = NoteAuthor(
            userId = user.userId,
            username = user.username,
            displayName = user.displayName,
            profileImageUrl = user.profileImageUrl
        )
    }
}

/**
 * Lightweight author reference for Story posts.
 *
 * Minimal data needed for story display in story bar.
 *
 * @property userId Unique user identifier.
 * @property username Unique username handle.
 * @property displayName User's display name.
 * @property profileImageUrl Profile picture URL.
 */
data class StoryAuthor(
    val userId: String,
    val username: String,
    val displayName: String,
    val profileImageUrl: String?
) {
    init {
        require(userId.isNotBlank()) { "userId cannot be blank" }
        require(username.isNotBlank()) { "username cannot be blank" }
        require(displayName.isNotBlank()) { "displayName cannot be blank" }
    }

    /**
     * Converts to a basic [UserReference].
     */
    fun toUserReference() = UserReference(
        userId = userId,
        username = username,
        displayName = displayName,
        profileImageUrl = profileImageUrl,
        isVerified = false
    )

    companion object {
        /**
         * Creates a [StoryAuthor] from a full [User] object.
         */
        fun from(user: User) = StoryAuthor(
            userId = user.userId,
            username = user.username,
            displayName = user.displayName,
            profileImageUrl = user.profileImageUrl
        )
    }
}

/**
 * Lightweight actor reference for Notification events.
 *
 * Represents the user who triggered a notification.
 *
 * @property userId Unique user identifier.
 * @property username Unique username handle.
 * @property displayName User's display name.
 * @property profileImageUrl Profile picture URL.
 * @property isVerified Whether the user has a verified badge.
 */
data class NotificationActor(
    val userId: String,
    val username: String,
    val displayName: String,
    val profileImageUrl: String?,
    val isVerified: Boolean = false
) {
    init {
        require(userId.isNotBlank()) { "userId cannot be blank" }
        require(username.isNotBlank()) { "username cannot be blank" }
        require(displayName.isNotBlank()) { "displayName cannot be blank" }
    }

    /**
     * Converts to a basic [UserReference].
     */
    fun toUserReference() = UserReference(
        userId = userId,
        username = username,
        displayName = displayName,
        profileImageUrl = profileImageUrl,
        isVerified = isVerified
    )

    companion object {
        /**
         * Creates a [NotificationActor] from a full [User] object.
         */
        fun from(user: User) = NotificationActor(
            userId = user.userId,
            username = user.username,
            displayName = user.displayName,
            profileImageUrl = user.profileImageUrl,
            isVerified = user.isVerified
        )
    }
}
