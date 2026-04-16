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
)

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
