package com.linker.app.domain.model

/**
 * Domain model for User
 * 
 * Clean, presentation-ready user representation.
 * Independent from both Firebase/Firestore and Room implementations.
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
    val createdAt: Long,
    val updatedAt: Long
)
