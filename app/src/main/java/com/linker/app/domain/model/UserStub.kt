package com.linker.app.domain.model

/**
 * Creates a minimal User stub for cases where full user data is not available
 * 
 * Used in:
 * - Repository layers when user data hasn't been fetched yet
 * - Message/Chat entities that only have userId
 * - Notification handlers before user sync
 * 
 * @param userId The user's unique identifier
 * @param username Optional username (defaults to empty)
 * @param displayName Optional display name (defaults to empty)
 * @return A minimal User object with default values
 */
fun createUserStub(
    userId: String,
    username: String = "",
    displayName: String = ""
): User = User(
    userId = userId,
    username = username,
    displayName = displayName,
    email = null,
    phoneNumber = null,
    bio = null,
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
    isPrivate = false,
    followRequestSent = false,
    hideFollowLists = false,
    createdAt = 0L,
    updatedAt = 0L
)
