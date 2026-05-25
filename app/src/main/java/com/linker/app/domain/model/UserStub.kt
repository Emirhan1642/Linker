package com.linker.app.domain.model

/**
 * Creates a minimal [User] stub for cases where full user data is not available.
 *
 * ## When to Use
 * - Repository layers when user data hasn't been fetched yet
 * - Message/Chat entities that only have a userId
 * - Notification handlers before user sync completes
 * - UI layers that need a non-null User before the real data loads
 *
 * ## When NOT to Use
 * - When full user data is available (use the real [User] instance instead)
 * - For long-lived display in the UI (resolve to a real user first)
 * - For comparisons or equality checks (stub data will match incorrectly)
 *
 * ## Stub Lifecycle
 * Stubs are temporary placeholders. They should be replaced with real user data
 * as soon as it becomes available (e.g., after a Firestore fetch). Use [User.isStub]
 * to detect stubs in the UI layer.
 *
 * ## Example
 * ```kotlin
 * // Create a stub for a user we only know by ID
 * val placeholder = createUserStub("user_abc123")
 *
 * // Later, check if we still have a stub
 * if (user.isStub()) {
 *     // Fetch real user data
 * }
 * ```
 *
 * @param userId The user's unique identifier (must not be blank).
 * @param username Optional username (null → generated from userId).
 * @param displayName Optional display name (null → generated from userId).
 * @return A minimal [User] object with safe default values.
 * @throws IllegalArgumentException if [userId] is blank.
 */
fun createUserStub(
    userId: String,
    username: String? = null,
    displayName: String? = null
): User {
    require(userId.isNotBlank()) { "userId cannot be blank when creating a stub" }
    return User(
        userId = userId,
        username = username?.takeIf { it.isNotBlank() } ?: "user_${userId.take(8)}",
        displayName = displayName?.takeIf { it.isNotBlank() } ?: "User ${userId.take(8)}",
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
}

/**
 * Checks whether this [User] is a stub (placeholder with minimal data).
 *
 * A user is considered a stub if:
 * - [User.createdAt] is 0 (never persisted)
 * - [User.profileImageUrl] is null (no profile data loaded)
 *
 * @return true if this user appears to be a stub/placeholder.
 */
fun User.isStub(): Boolean {
    return createdAt == 0L && profileImageUrl == null
}
