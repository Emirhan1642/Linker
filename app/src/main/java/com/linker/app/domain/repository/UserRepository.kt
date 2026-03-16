package com.linker.app.domain.repository

import com.linker.app.domain.model.User
import com.linker.app.core.util.Result
import kotlinx.coroutines.flow.Flow

interface UserRepository {

    /** Returns the current signed-in user, or null if not authenticated. */
    fun getCurrentUser(): Flow<User?>

    /** Fetches a user by ID — checks cache first, then remote. */
    suspend fun getUserById(userId: String): Result<User>

    /** Fetches a user by username. */
    suspend fun getUserByUsername(username: String): Result<User>

    /** Returns users whose username or display name contains [query]. */
    suspend fun searchUsers(query: String, limit: Int = 20): Result<List<User>>

    /** Follows the user with [targetUserId]. */
    suspend fun followUser(targetUserId: String): Result<Unit>

    /** Unfollows the user with [targetUserId]. */
    suspend fun unfollowUser(targetUserId: String): Result<Unit>

    /** Blocks the user with [targetUserId]. */
    suspend fun blockUser(targetUserId: String): Result<Unit>

    /** Unblocks the user with [targetUserId]. */
    suspend fun unblockUser(targetUserId: String): Result<Unit>

    /** Updates the current user's profile. */
    suspend fun updateProfile(
        displayName: String? = null,
        bio: String? = null,
        profileImageUrl: String? = null,
        coverImageUrl: String? = null
    ): Result<User>

    /** Observes the list of users the current user is following (cached). */
    fun observeFollowing(): Flow<List<User>>

    /** Returns whether a given username is already taken. */
    suspend fun isUsernameAvailable(username: String): Result<Boolean>
}
