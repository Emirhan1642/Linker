package com.linker.app.domain.repository

import com.linker.app.domain.model.User
import com.linker.app.core.util.Result
import kotlinx.coroutines.flow.Flow

interface UserRepository {

    fun getCurrentUser(): Flow<User?>
    suspend fun getUserById(userId: String): Result<User>
    suspend fun getUserByUsername(username: String): Result<User>
    suspend fun searchUsers(query: String, limit: Int = 20): Result<List<User>>

    suspend fun followUser(targetUserId: String): Result<Unit>
    suspend fun unfollowUser(targetUserId: String): Result<Unit>
    suspend fun cancelFollowRequest(targetUserId: String): Result<Unit>
    suspend fun acceptFollowRequest(fromUserId: String): Result<Unit>
    suspend fun declineFollowRequest(fromUserId: String): Result<Unit>

    /**
     * Takipçi listesini döndürür.
     * Private hesap + takip etmiyorsa null döner (kilitli durum).
     * Public hesap + 0 takipçi ise emptyList döner.
     */
    suspend fun getFollowers(userId: String): Result<List<User>?>
    suspend fun getFollowing(userId: String): Result<List<User>?>
    suspend fun getPendingRequests(): Result<List<User>>
    suspend fun getSentRequests(): Result<List<User>>

    suspend fun blockUser(targetUserId: String): Result<Unit>
    suspend fun unblockUser(targetUserId: String): Result<Unit>

    suspend fun updateProfile(
        displayName: String? = null,
        bio: String? = null,
        profileImageUrl: String? = null,
        coverImageUrl: String? = null
    ): Result<User>

    suspend fun setPrivateAccount(isPrivate: Boolean): Result<Unit>
    /** Takip/takipçi listelerini diğer kullanıcılardan gizle */
    suspend fun setHideFollowLists(hide: Boolean): Result<Unit>

    fun observeFollowing(): Flow<List<User>>
    suspend fun isUsernameAvailable(username: String): Result<Boolean>
}
