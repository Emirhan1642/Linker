package com.linker.app.domain.model

data class User(
    val userId: String = "",
    val username: String = "",
    val displayName: String = "",
    val email: String? = null,
    val phoneNumber: String? = null,
    val bio: String? = null,
    val profileImageUrl: String? = null,
    val coverImageUrl: String? = null,
    val isVerified: Boolean = false,
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val likesCount: Int = 0,
    val isFollowing: Boolean = false,
    val isFollowedBy: Boolean = false,
    val isBlocked: Boolean = false,
    val isMuted: Boolean = false,
    val isPrivate: Boolean = false,
    val followRequestSent: Boolean = false,
    /** true → takip/takipçi listeleri herkes için gizli */
    val hideFollowLists: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
) {
    companion object {
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
            email = null,
            phoneNumber = null,
            bio = "This user account has been deleted",
            profileImageUrl = null,
            coverImageUrl = null,
            isVerified = false,
            followersCount = 0,
            followingCount = 0,
            likesCount = 0,
            isFollowing = false,
            isFollowedBy = false,
            isBlocked = false,
            isMuted = false,
            isPrivate = true,
            followRequestSent = false,
            hideFollowLists = true,
            createdAt = 0L,
            updatedAt = 0L
        )
    }
}

enum class FollowState {
    NOT_FOLLOWING,
    NOT_FOLLOWING_PRIVATE,
    REQUEST_SENT,
    FOLLOWING
}

fun User.followState(): FollowState = when {
    isFollowing       -> FollowState.FOLLOWING
    followRequestSent -> FollowState.REQUEST_SENT
    isPrivate         -> FollowState.NOT_FOLLOWING_PRIVATE
    else              -> FollowState.NOT_FOLLOWING
}
