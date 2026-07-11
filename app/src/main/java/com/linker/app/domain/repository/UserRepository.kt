package com.linker.app.domain.repository

import com.linker.app.domain.model.User
import com.linker.app.core.util.Result
import kotlinx.coroutines.flow.Flow

/**
 * Errors specific to User repository operations.
 */
sealed class UserRepositoryError : Exception() {
    object PrivateAccountLocked : UserRepositoryError()
    object UserNotFound : UserRepositoryError()
    object RateLimitExceeded : UserRepositoryError()
    data class Unknown(override val message: String) : UserRepositoryError()
}

/**
 * Typed result for user operations.
 */
typealias UserResult<T> = Result<T>

/**
 * Represents a paginated list of users.
 */
data class PaginatedUsers(
    val users: List<User>,
    val nextCursor: String?,
    val hasMore: Boolean
)

interface UserRepository {

    // ── Current User ───────────────────────────────────────────────────────

    /** Observes the current authenticated user's profile. */
    fun observeCurrentUser(): Flow<UserResult<User?>>
    
    /** Gets the current authenticated user's profile synchronously. */
    suspend fun getCurrentUser(): UserResult<User?>

    // ── Queries ────────────────────────────────────────────────────────────

    /** Gets a user profile by ID. */
    suspend fun getUserById(userId: String): UserResult<User>
    
    /** Gets a user profile by username. */
    suspend fun getUserByUsername(username: String): UserResult<User>
    
    /** 
     * Searches for users matching the query.
     * 
     * Behavior:
     * - Searches by username, display name, and bio.
     * - Prioritizes verified users and users with more followers.
     * - Minimum query length is 2 characters.
     */
    suspend fun searchUsers(query: String, limit: Int = 20, cursor: String? = null): UserResult<PaginatedUsers>

    /** Checks if a username is available for registration or change. */
    suspend fun isUsernameAvailable(username: String): UserResult<Boolean>

    // ── Social (Follow/Unfollow) ───────────────────────────────────────────

    /** 
     * Follows a user.
     * 
     * Rate Limits:
     * - Maximum 100 follows per hour.
     * - Maximum 1000 follows per day.
     * Will return RateLimitExceeded error if exceeded.
     */
    suspend fun followUser(targetUserId: String): UserResult<Unit>
    
    /** 
     * Unfollows a user.
     * 
     * Rate Limits:
     * - Maximum 200 unfollows per hour.
     */
    suspend fun unfollowUser(targetUserId: String): UserResult<Unit>
    
    suspend fun cancelFollowRequest(targetUserId: String): UserResult<Unit>
    suspend fun acceptFollowRequest(fromUserId: String): UserResult<Unit>
    suspend fun declineFollowRequest(fromUserId: String): UserResult<Unit>
    
    /** 
     * Removes a user from the current user's followers list.
     */
    suspend fun removeFollower(userId: String): UserResult<Unit>

    /**
     * Gets the followers list.
     * 
     * @return Result containing PaginatedUsers, or PrivateAccountLocked error if the account is private and not followed.
     */
    suspend fun getFollowers(userId: String, limit: Int = 20, cursor: String? = null): UserResult<PaginatedUsers>
    
    /**
     * Gets the following list.
     * 
     * @return Result containing PaginatedUsers, or PrivateAccountLocked error if the account is private and not followed.
     */
    suspend fun getFollowing(userId: String, limit: Int = 20, cursor: String? = null): UserResult<PaginatedUsers>
    
    /** Gets users that both the current user and target user follow. */
    suspend fun getMutualFollowing(userId: String, limit: Int = 20, cursor: String? = null): UserResult<PaginatedUsers>
    
    /** Gets users that follow both the current user and target user. */
    suspend fun getMutualFollowers(userId: String, limit: Int = 20, cursor: String? = null): UserResult<PaginatedUsers>

    suspend fun getPendingRequests(limit: Int = 20, cursor: String? = null): UserResult<PaginatedUsers>
    suspend fun getSentRequests(limit: Int = 20, cursor: String? = null): UserResult<PaginatedUsers>

    /** Observes the current user's following list in real-time. */
    fun observeFollowing(): Flow<UserResult<List<User>>>

    // ── Blocking ───────────────────────────────────────────────────────────

    /** 
     * Blocks a user.
     * 
     * Consequences:
     * - The blocked user will be forced to unfollow.
     * - The current user will be forced to unfollow.
     * - Any existing DMs between the two users will be hidden.
     * - The blocked user will not be able to view the current user's profile, posts, or stories.
     */
    suspend fun blockUser(targetUserId: String): UserResult<Unit>
    
    /** 
     * Unblocks a user.
     * 
     * Consequences:
     * - Previous follow relationships are NOT restored.
     * - DMs will become visible again.
     */
    suspend fun unblockUser(targetUserId: String): UserResult<Unit>

    // ── Profile Updates ────────────────────────────────────────────────────

    /** Updates text-based profile information. */
    suspend fun updateProfile(
        displayName: String? = null,
        bio: String? = null
    ): UserResult<User>
    
    /** Updates the profile image. Returns the new User object with updated URL. */
    suspend fun updateProfileImage(localImagePath: String): UserResult<User>
    
    /** Updates the cover image. Returns the new User object with updated URL. */
    suspend fun updateCoverImage(localImagePath: String): UserResult<User>

    // ── Settings ───────────────────────────────────────────────────────────

    /** Toggles the account's privacy status. */
    suspend fun setPrivateAccount(isPrivate: Boolean): UserResult<Unit>
    
    /** Toggles whether to hide follow/following lists from other users. */
    suspend fun setHideFollowLists(hide: Boolean): UserResult<Unit>
    
    /** Updates the user's online presence (lastSeen timestamp). */
    suspend fun updatePresence(): UserResult<Unit>
}
