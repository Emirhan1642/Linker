package com.linker.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.linker.app.core.util.Result
import com.linker.app.core.util.safeCall
import com.linker.app.data.local.dao.UserDao
import com.linker.app.data.local.entity.UserEntity
import com.linker.app.data.local.mapper.toDomain
import com.linker.app.domain.model.User
import com.linker.app.domain.repository.UserRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val userDao: UserDao,
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth
) : UserRepository {

    override fun getCurrentUser(): Flow<User?> = callbackFlow {
        val uid = firebaseAuth.currentUser?.uid
        if (uid == null) {
            trySend(null)
            close()
            return@callbackFlow
        }

        // Listen to Firestore for real-time updates of the current user
        val listener = firestore.collection("users").document(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val data = snapshot.data
                if (data != null) {
                    val entity = mapToUserEntity(uid, data)
                    // Launch side-effect to cache locally (in real app, use coroutine scope, omitting here to stay simple or assuming DAO fast)
                    trySend(entity.toDomain())
                }
            } else {
                trySend(null)
            }
        }
        
        awaitClose { listener.remove() }
    }

    override suspend fun getUserById(userId: String): Result<User> = safeCall {
        // First check locally
        val local = userDao.getUserById(userId)
        
        // Then try remote
        val snapshot = firestore.collection("users").document(userId).get().await()
        if (snapshot.exists() && snapshot.data != null) {
            val entity = mapToUserEntity(userId, snapshot.data!!)
            userDao.insertUser(entity)
            return@safeCall entity.toDomain()
        }
        
        if (local != null) return@safeCall local.toDomain()
        
        throw Exception("User not found")
    }

    override suspend fun getUserByUsername(username: String): Result<User> = safeCall {
        val querySnapshot = firestore.collection("users").whereEqualTo("username", username).limit(1).get().await()
        if (querySnapshot.isEmpty) {
            throw Exception("User '$username' not found")
        }
        val doc = querySnapshot.documents[0]
        val entity = mapToUserEntity(doc.id, doc.data!!)
        userDao.insertUser(entity)
        entity.toDomain()
    }

    override suspend fun searchUsers(query: String, limit: Int): Result<List<User>> = safeCall {
        // Firestore doesn't do "contains" easily, but we fallback to local search for now or precise prefix matching
        userDao.searchUsers(query, limit).map { it.toDomain() }
    }

    override suspend fun followUser(targetUserId: String): Result<Unit> = safeCall {
        userDao.updateFollowingStatus(targetUserId, true)
    }

    override suspend fun unfollowUser(targetUserId: String): Result<Unit> = safeCall {
        userDao.updateFollowingStatus(targetUserId, false)
    }

    override suspend fun blockUser(targetUserId: String): Result<Unit> = safeCall {
        val user = userDao.getUserById(targetUserId) ?: throw Exception("User not found")
        userDao.updateUser(user.copy(isBlocked = true, isFollowing = false))
    }

    override suspend fun unblockUser(targetUserId: String): Result<Unit> = safeCall {
        val user = userDao.getUserById(targetUserId) ?: throw Exception("User not found")
        userDao.updateUser(user.copy(isBlocked = false))
    }

    override suspend fun updateProfile(
        displayName: String?,
        bio: String?,
        profileImageUrl: String?,
        coverImageUrl: String?
    ): Result<User> = safeCall {
        val uid = firebaseAuth.currentUser?.uid ?: throw Exception("Not logged in")
        val map = mutableMapOf<String, Any>()
        displayName?.let { map["displayName"] = it }
        bio?.let { map["bio"] = it }
        profileImageUrl?.let { map["profileImageUrl"] = it }
        coverImageUrl?.let { map["coverImageUrl"] = it }
        map["updatedAt"] = System.currentTimeMillis()

        firestore.collection("users").document(uid).set(map, SetOptions.merge()).await()
        
        // Return updated user
        val updatedSnap = firestore.collection("users").document(uid).get().await()
        val entity = mapToUserEntity(uid, updatedSnap.data!!)
        userDao.insertUser(entity)
        entity.toDomain()
    }

    override fun observeFollowing(): Flow<List<User>> =
        userDao.observeFollowing().map { list -> list.map { it.toDomain() } }

    override suspend fun isUsernameAvailable(username: String): Result<Boolean> = safeCall {
        val snapshot = firestore.collection("users").whereEqualTo("username", username).limit(1).get().await()
        snapshot.isEmpty
    }

    // Helper
    private fun mapToUserEntity(id: String, data: Map<String, Any>): UserEntity {
        return UserEntity(
            userId = id,
            username = data["username"] as? String ?: "",
            displayName = data["displayName"] as? String ?: "",
            email = data["email"] as? String,
            phoneNumber = data["phoneNumber"] as? String,
            bio = data["bio"] as? String,
            profileImageUrl = data["profileImageUrl"] as? String,
            coverImageUrl = data["coverImageUrl"] as? String,
            isVerified = data["isVerified"] as? Boolean ?: false,
            followersCount = (data["followersCount"] as? Number)?.toInt() ?: 0,
            followingCount = (data["followingCount"] as? Number)?.toInt() ?: 0,
            likesCount = (data["likesCount"] as? Number)?.toInt() ?: 0,
            isFollowing = false,
            isFollowedBy = false,
            createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            lastSyncedAt = System.currentTimeMillis()
        )
    }
}
