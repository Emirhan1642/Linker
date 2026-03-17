package com.linker.app.domain.model

data class User(
    val userId: String,
    val username: String,
    val displayName: String,
    val email: String?,
    val phoneNumber: String?,
    val bio: String?,
    val profileImageUrl: String?,
    val coverImageUrl: String?,
    val isVerified: Boolean,
    val followersCount: Int,
    val followingCount: Int,
    val likesCount: Int,
    val isFollowing: Boolean,
    val isFollowedBy: Boolean,
    val isBlocked: Boolean,
    val isMuted: Boolean,
    val isPrivate: Boolean = false,
    val followRequestSent: Boolean = false,
    /** true → takip/takipçi listeleri herkes için gizli */
    val hideFollowLists: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
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
