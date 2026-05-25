package com.linker.app.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * User relationship state.
 *
 * Groups all follow/block/mute relationship flags.
 *
 * @property isFollowing Whether the current user follows this user.
 * @property isFollowedBy Whether this user follows the current user.
 * @property isBlocked Whether the current user has blocked this user.
 * @property isMuted Whether the current user has muted this user.
 * @property followRequestSent Whether the current user has sent a follow request.
 */
@Serializable
data class UserRelationship(
    val isFollowing: Boolean = false,
    val isFollowedBy: Boolean = false,
    val isBlocked: Boolean = false,
    val isMuted: Boolean = false,
    val followRequestSent: Boolean = false
) {
    init {
        // Logical consistency checks
        if (isBlocked) {
            require(!isFollowing) { "Cannot follow a blocked user" }
            require(!isFollowedBy) { "Blocked user cannot be a follower" }
        }
        if (isFollowing) {
            require(!followRequestSent) { "Cannot have pending follow request while already following" }
        }
    }

    /** Whether there is any active relationship (follow, block, or mute). */
    val hasActiveRelationship: Boolean
        get() = isFollowing || isFollowedBy || isBlocked || isMuted

    /** Whether this is a mutual follow relationship. */
    val isMutualFollow: Boolean
        get() = isFollowing && isFollowedBy
}

/**
 * User privacy settings.
 *
 * Groups all privacy-related flags.
 *
 * @property isPrivate Whether this user's profile is private.
 * @property hideFollowLists Whether follow/follower lists are hidden.
 */
@Serializable
data class UserPrivacy(
    val isPrivate: Boolean = false,
    val hideFollowLists: Boolean = false
) {
    /** Whether the user's content is publicly visible. */
    val isPublic: Boolean
        get() = !isPrivate
}

/**
 * User engagement metrics.
 *
 * Groups all count-based metrics.
 *
 * @property followersCount Number of followers.
 * @property followingCount Number of users this user follows.
 * @property likesCount Total likes received across all posts.
 */
@Serializable
data class UserMetrics(
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val likesCount: Int = 0
) {
    init {
        require(followersCount >= 0) { "followersCount cannot be negative" }
        require(followingCount >= 0) { "followingCount cannot be negative" }
        require(likesCount >= 0) { "likesCount cannot be negative" }
    }

    /** Total engagement count (followers + following + likes). */
    val totalEngagement: Int
        get() = followersCount + followingCount + likesCount

    /** Whether the user has any followers. */
    val hasFollowers: Boolean
        get() = followersCount > 0

    /** Whether the user follows anyone. */
    val hasFollowing: Boolean
        get() = followingCount > 0
}

/**
 * Domain model representing a Linker user profile.
 *
 * This is the central identity model used across the entire application.
 * It holds profile information, social relationship state, and engagement counts.
 *
 * ## Important
 * - `User()` empty constructor is used widely for default/placeholder values.
 *   Do NOT add `isNotBlank()` checks on string fields.
 * - [email] and [phoneNumber] are PII and are:
 *   - Stored as private fields
 *   - Redacted in [toString]
 *   - Excluded from serialization by default
 *
 * @property userId Firebase UID — unique, stable identifier.
 * @property username Unique username handle (e.g., "john_doe").
 * @property displayName User's display name shown in the UI.
 * @property bio User bio/description.
 * @property profileImageUrl Profile picture URL.
 * @property coverImageUrl Cover/banner image URL.
 * @property isVerified Whether the user has a verified badge.
 * @property relationship Social relationship state with the current user.
 * @property privacy Privacy settings.
 * @property metrics Engagement metrics (followers, following, likes).
 * @property createdAt Account creation timestamp (epoch ms).
 * @property updatedAt Last profile update timestamp (epoch ms).
 *
 * @see FollowState
 * @see followState
 */
@Serializable
data class User(
    val userId: String = "",
    val username: String = "",
    val displayName: String = "",
    @Transient private val _email: String? = null,
    @Transient private val _phoneNumber: String? = null,
    val bio: String? = null,
    val profileImageUrl: String? = null,
    val coverImageUrl: String? = null,
    val isVerified: Boolean = false,
    val relationship: UserRelationship = UserRelationship(),
    val privacy: UserPrivacy = UserPrivacy(),
    val metrics: UserMetrics = UserMetrics(),
    val createdAt: Long = 0,
    val updatedAt: Long = 0
) {
    /**
     * Gets the user's email address (PII).
     * Returns null if not available or if the caller doesn't have permission.
     */
    fun getEmail(): String? = _email

    /**
     * Gets the user's phone number (PII).
     * Returns null if not available or if the caller doesn't have permission.
     */
    fun getPhoneNumber(): String? = _phoneNumber

    /**
     * Checks if the user has an email address.
     */
    fun hasEmail(): Boolean = !_email.isNullOrBlank()

    /**
     * Checks if the user has a phone number.
     */
    fun hasPhoneNumber(): Boolean = !_phoneNumber.isNullOrBlank()

    /**
     * Override toString to redact PII fields (email, phoneNumber).
     * Prevents accidental exposure in logs.
     */
    override fun toString(): String {
        return "User(" +
                "userId='$userId', " +
                "username='$username', " +
                "displayName='$displayName', " +
                "email=***REDACTED***, " +
                "phoneNumber=***REDACTED***, " +
                "bio=$bio, " +
                "profileImageUrl=$profileImageUrl, " +
                "isVerified=$isVerified, " +
                "relationship=$relationship, " +
                "privacy=$privacy, " +
                "metrics=$metrics)"
    }

    companion object {
        /** Minimum username length. */
        const val MIN_USERNAME_LENGTH = 3

        /** Maximum username length. */
        const val MAX_USERNAME_LENGTH = 30

        /** Maximum display name length. */
        const val MAX_DISPLAY_NAME_LENGTH = 50

        /** Maximum bio length. */
        const val MAX_BIO_LENGTH = 150

        /**
         * Creates a placeholder User instance for deleted users.
         * Used when a user has been deleted but their content (posts, comments, etc.) still exists.
         *
         * @param userId The ID of the deleted user
         * @return A User instance representing a deleted user
         */
        fun deletedUser(userId: String) = User(
            userId = userId,
            username = "[deleted]",
            displayName = "Deleted User",
            _email = null,
            _phoneNumber = null,
            bio = "This user account has been deleted",
            profileImageUrl = null,
            coverImageUrl = null,
            isVerified = false,
            relationship = UserRelationship(),
            privacy = UserPrivacy(isPrivate = true, hideFollowLists = true),
            metrics = UserMetrics(),
            createdAt = 0L,
            updatedAt = 0L
        )
    }
}

/**
 * Describes the follow relationship between the current user and another user.
 */
enum class FollowState {
    /** Not following, profile is public — show "Follow" button. */
    NOT_FOLLOWING,
    /** Not following, profile is private — show "Follow" button (will send request). */
    NOT_FOLLOWING_PRIVATE,
    /** Follow request has been sent, waiting for approval — show "Requested" button. */
    REQUEST_SENT,
    /** Currently following — show "Following" button. */
    FOLLOWING
}

/**
 * Derives the [FollowState] from the current user's relationship flags.
 *
 * Priority: isFollowing > followRequestSent > isPrivate > default
 */
fun User.followState(): FollowState = when {
    relationship.isFollowing       -> FollowState.FOLLOWING
    relationship.followRequestSent -> FollowState.REQUEST_SENT
    privacy.isPrivate              -> FollowState.NOT_FOLLOWING_PRIVATE
    else                           -> FollowState.NOT_FOLLOWING
}
