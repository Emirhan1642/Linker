package com.linker.app.domain.repository

import com.linker.app.domain.model.User
import com.linker.app.core.util.Result
import kotlinx.coroutines.flow.Flow

interface UserRepository {

    fun getCurrentUser(): Flow<User?>
    suspend fun getUserById(userId: String): Result<User>
    suspend fun getUserByUsername(username: String): Result<User>
    suspend fun searchUsers(query: String, limit: Int = 20): Result<List<User>>

    // ── Follow / Unfollow ──────────────────────────────────────────────────
    /** Public hesap: direkt takip. Private hesap: istek gönder. */
    suspend fun followUser(targetUserId: String): Result<Unit>
    suspend fun unfollowUser(targetUserId: String): Result<Unit>

    // ── Follow Requests ────────────────────────────────────────────────────
    /** Bekleyen follow isteğini iptal et (istek gönderen taraf) */
    suspend fun cancelFollowRequest(targetUserId: String): Result<Unit>
    /** Gelen isteği kabul et (alıcı taraf) */
    suspend fun acceptFollowRequest(fromUserId: String): Result<Unit>
    /** Gelen isteği reddet (alıcı taraf) */
    suspend fun declineFollowRequest(fromUserId: String): Result<Unit>

    // ── Lists ──────────────────────────────────────────────────────────────
    suspend fun getFollowers(userId: String): Result<List<User>>
    suspend fun getFollowing(userId: String): Result<List<User>>
    /** Aktif kullanıcıya gelen bekleyen follow istekleri */
    suspend fun getPendingRequests(): Result<List<User>>
    /** Aktif kullanıcının gönderdiği bekleyen istekler */
    suspend fun getSentRequests(): Result<List<User>>

    // ── Block ──────────────────────────────────────────────────────────────
    suspend fun blockUser(targetUserId: String): Result<Unit>
    suspend fun unblockUser(targetUserId: String): Result<Unit>

    // ── Profile ────────────────────────────────────────────────────────────
    suspend fun updateProfile(
        displayName: String? = null,
        bio: String? = null,
        profileImageUrl: String? = null,
        coverImageUrl: String? = null
    ): Result<User>

    /** Private hesap ayarını aç/kapat — Firestore'a yazar */
    suspend fun setPrivateAccount(isPrivate: Boolean): Result<Unit>

    fun observeFollowing(): Flow<List<User>>
    suspend fun isUsernameAvailable(username: String): Result<Boolean>
}
