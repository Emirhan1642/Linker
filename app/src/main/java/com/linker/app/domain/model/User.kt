package com.linker.app.domain.model

/**
 * Domain model for User
 */
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
    /** Hesap gizliyse true — yabancılar içerikleri göremez */
    val isPrivate: Boolean = false,
    /** Aktif kullanıcı bu hesaba follow isteği gönderdiyse true */
    val followRequestSent: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)

/** Takip durumu — UserProfile butonu bu state'e göre render edilir */
enum class FollowState {
    /** Takip edilmiyor, private değil → direkt follow */
    NOT_FOLLOWING,
    /** Takip edilmiyor, private → istek gönder */
    NOT_FOLLOWING_PRIVATE,
    /** İstek gönderildi, onay bekleniyor */
    REQUEST_SENT,
    /** Takip ediliyor */
    FOLLOWING
}

fun User.followState(): FollowState = when {
    isFollowing              -> FollowState.FOLLOWING
    followRequestSent        -> FollowState.REQUEST_SENT
    isPrivate                -> FollowState.NOT_FOLLOWING_PRIVATE
    else                     -> FollowState.NOT_FOLLOWING
}
