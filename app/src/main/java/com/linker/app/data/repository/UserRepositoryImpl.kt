package com.linker.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
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

/**
 * Firestore koleksiyon yapısı:
 *
 * users/{uid}
 *   isPrivate: Boolean
 *   followersCount: Int
 *   followingCount: Int
 *
 * follows/{followerId}_{followedId}
 *   followerId: String
 *   followedId: String
 *   status: "active" | "pending"
 *   createdAt: Long
 *
 * Bu yapı sayesinde:
 *  - "Beni takip eden var mı?" → follows collectionGroup where followedId == uid && status == active
 *  - "Benim istek attığım var mı?" → follows/{myUid}_{targetUid}.status == pending
 *  - "Private hesaba istek" → status = pending oluşturulur
 *  - "Kabul etme" → status = active yapılır
 */
@Singleton
class UserRepositoryImpl @Inject constructor(
    private val userDao: UserDao,
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth
) : UserRepository {

    private val currentUid get() = firebaseAuth.currentUser?.uid

    // ── Current user stream ───────────────────────────────────────────────────

    override fun getCurrentUser(): Flow<User?> = callbackFlow {
        val uid = currentUid
        if (uid == null) { trySend(null); close(); return@callbackFlow }

        val listener = firestore.collection("users").document(uid)
            .addSnapshotListener { snap, _ ->
                if (snap != null && snap.exists() && snap.data != null)
                    trySend(mapToEntity(uid, snap.data!!).toDomain())
                else
                    trySend(null)
            }
        awaitClose { listener.remove() }
    }

    // ── Get user ──────────────────────────────────────────────────────────────

    override suspend fun getUserById(userId: String): Result<User> = safeCall {
        val snap = firestore.collection("users").document(userId).get().await()
        if (snap.exists() && snap.data != null) {
            val entity = mapToEntity(userId, snap.data!!)
            // Aktif kullanıcıyla ilişki durumunu çek
            val enriched = enrichWithRelationship(entity)
            userDao.insertUser(enriched)
            return@safeCall enriched.toDomain()
        }
        val local = userDao.getUserById(userId)
        if (local != null) return@safeCall local.toDomain()
        throw Exception("User not found")
    }

    override suspend fun getUserByUsername(username: String): Result<User> = safeCall {
        val q = firestore.collection("users").whereEqualTo("username", username).limit(1).get().await()
        if (q.isEmpty) throw Exception("User '$username' not found")
        val doc = q.documents[0]
        val entity = enrichWithRelationship(mapToEntity(doc.id, doc.data!!))
        userDao.insertUser(entity)
        entity.toDomain()
    }

    override suspend fun searchUsers(query: String, limit: Int): Result<List<User>> = safeCall {
        val me = currentUid

        val byUsername = firestore.collection("users")
            .whereGreaterThanOrEqualTo("username", query.lowercase())
            .whereLessThanOrEqualTo("username", query.lowercase() + "\uf8ff")
            .limit(limit.toLong()).get().await()

        val byName = firestore.collection("users")
            .whereGreaterThanOrEqualTo("displayName", query)
            .whereLessThanOrEqualTo("displayName", query + "\uf8ff")
            .limit(limit.toLong()).get().await()

        val remoteEntities = (byUsername.documents + byName.documents)
            .distinctBy { it.id }
            .filter { it.id != me }
            .mapNotNull { doc -> doc.data?.let { enrichWithRelationship(mapToEntity(doc.id, it)) } }

        remoteEntities.forEach { userDao.insertUser(it) }

        val remoteIds = remoteEntities.map { it.userId }.toSet()
        val localOnly = userDao.searchUsers(query, limit).filter { it.userId != me && it.userId !in remoteIds }

        (remoteEntities + localOnly).take(limit).map { it.toDomain() }
    }

    // ── Follow / Unfollow / Request ───────────────────────────────────────────

    override suspend fun followUser(targetUserId: String): Result<Unit> = safeCall {
        val me = currentUid ?: throw Exception("Not logged in")

        // Hedefin private olup olmadığını kontrol et
        val targetSnap = firestore.collection("users").document(targetUserId).get().await()
        val isPrivate = targetSnap.getBoolean("isPrivate") ?: false

        val docId = "${me}_${targetUserId}"
        val status = if (isPrivate) "pending" else "active"

        firestore.collection("follows").document(docId).set(
            mapOf(
                "followerId" to me,
                "followedId" to targetUserId,
                "status"     to status,
                "createdAt"  to System.currentTimeMillis()
            )
        ).await()

        if (!isPrivate) {
            // Sayaçları güncelle
            firestore.collection("users").document(me)
                .update("followingCount", FieldValue.increment(1)).await()
            firestore.collection("users").document(targetUserId)
                .update("followersCount", FieldValue.increment(1)).await()
            userDao.updateFollowingStatus(targetUserId, true)
        } else {
            userDao.updateRequestSentStatus(targetUserId, true)
        }
    }

    override suspend fun unfollowUser(targetUserId: String): Result<Unit> = safeCall {
        val me = currentUid ?: throw Exception("Not logged in")
        val docId = "${me}_${targetUserId}"

        firestore.collection("follows").document(docId).delete().await()

        firestore.collection("users").document(me)
            .update("followingCount", FieldValue.increment(-1)).await()
        firestore.collection("users").document(targetUserId)
            .update("followersCount", FieldValue.increment(-1)).await()

        userDao.updateFollowingStatus(targetUserId, false)
    }

    override suspend fun cancelFollowRequest(targetUserId: String): Result<Unit> = safeCall {
        val me = currentUid ?: throw Exception("Not logged in")
        firestore.collection("follows").document("${me}_${targetUserId}").delete().await()
        userDao.updateRequestSentStatus(targetUserId, false)
    }

    override suspend fun acceptFollowRequest(fromUserId: String): Result<Unit> = safeCall {
        val me = currentUid ?: throw Exception("Not logged in")
        val docId = "${fromUserId}_${me}"

        firestore.collection("follows").document(docId)
            .update("status", "active").await()

        firestore.collection("users").document(fromUserId)
            .update("followingCount", FieldValue.increment(1)).await()
        firestore.collection("users").document(me)
            .update("followersCount", FieldValue.increment(1)).await()
    }

    override suspend fun declineFollowRequest(fromUserId: String): Result<Unit> = safeCall {
        val me = currentUid ?: throw Exception("Not logged in")
        firestore.collection("follows").document("${fromUserId}_${me}").delete().await()
    }

    // ── Lists ─────────────────────────────────────────────────────────────────

    override suspend fun getFollowers(userId: String): Result<List<User>> = safeCall {
        val q = firestore.collection("follows")
            .whereEqualTo("followedId", userId)
            .whereEqualTo("status", "active")
            .get().await()

        q.documents.mapNotNull { doc ->
            val followerId = doc.getString("followerId") ?: return@mapNotNull null
            val userSnap = firestore.collection("users").document(followerId).get().await()
            if (userSnap.exists() && userSnap.data != null)
                enrichWithRelationship(mapToEntity(followerId, userSnap.data!!)).toDomain()
            else null
        }
    }

    override suspend fun getFollowing(userId: String): Result<List<User>> = safeCall {
        val q = firestore.collection("follows")
            .whereEqualTo("followerId", userId)
            .whereEqualTo("status", "active")
            .get().await()

        q.documents.mapNotNull { doc ->
            val followedId = doc.getString("followedId") ?: return@mapNotNull null
            val userSnap = firestore.collection("users").document(followedId).get().await()
            if (userSnap.exists() && userSnap.data != null)
                enrichWithRelationship(mapToEntity(followedId, userSnap.data!!)).toDomain()
            else null
        }
    }

    override suspend fun getPendingRequests(): Result<List<User>> = safeCall {
        val me = currentUid ?: throw Exception("Not logged in")
        val q = firestore.collection("follows")
            .whereEqualTo("followedId", me)
            .whereEqualTo("status", "pending")
            .get().await()

        q.documents.mapNotNull { doc ->
            val followerId = doc.getString("followerId") ?: return@mapNotNull null
            val userSnap = firestore.collection("users").document(followerId).get().await()
            if (userSnap.exists() && userSnap.data != null)
                mapToEntity(followerId, userSnap.data!!).toDomain()
            else null
        }
    }

    override suspend fun getSentRequests(): Result<List<User>> = safeCall {
        val me = currentUid ?: throw Exception("Not logged in")
        val q = firestore.collection("follows")
            .whereEqualTo("followerId", me)
            .whereEqualTo("status", "pending")
            .get().await()

        q.documents.mapNotNull { doc ->
            val followedId = doc.getString("followedId") ?: return@mapNotNull null
            val userSnap = firestore.collection("users").document(followedId).get().await()
            if (userSnap.exists() && userSnap.data != null)
                mapToEntity(followedId, userSnap.data!!).toDomain()
            else null
        }
    }

    // ── Block ─────────────────────────────────────────────────────────────────

    override suspend fun blockUser(targetUserId: String): Result<Unit> = safeCall {
        val user = userDao.getUserById(targetUserId) ?: throw Exception("User not found")
        userDao.updateUser(user.copy(isBlocked = true, isFollowing = false))
        // Takipten çıkar
        unfollowUser(targetUserId)
    }

    override suspend fun unblockUser(targetUserId: String): Result<Unit> = safeCall {
        val user = userDao.getUserById(targetUserId) ?: throw Exception("User not found")
        userDao.updateUser(user.copy(isBlocked = false))
    }

    // ── Profile ───────────────────────────────────────────────────────────────

    override suspend fun updateProfile(
        displayName: String?, bio: String?,
        profileImageUrl: String?, coverImageUrl: String?
    ): Result<User> = safeCall {
        val uid = currentUid ?: throw Exception("Not logged in")
        val map = mutableMapOf<String, Any>()
        displayName?.let { map["displayName"] = it }
        bio?.let { map["bio"] = it }
        profileImageUrl?.let { map["profileImageUrl"] = it }
        coverImageUrl?.let { map["coverImageUrl"] = it }
        map["updatedAt"] = System.currentTimeMillis()
        firestore.collection("users").document(uid).set(map, SetOptions.merge()).await()
        val snap = firestore.collection("users").document(uid).get().await()
        val entity = mapToEntity(uid, snap.data!!)
        userDao.insertUser(entity)
        entity.toDomain()
    }

    override suspend fun setPrivateAccount(isPrivate: Boolean): Result<Unit> = safeCall {
        val uid = currentUid ?: throw Exception("Not logged in")
        firestore.collection("users").document(uid)
            .update("isPrivate", isPrivate).await()
        // Local cache'i güncelle
        val local = userDao.getUserById(uid)
        if (local != null) userDao.updateUser(local.copy(isPrivate = isPrivate))
    }

    override fun observeFollowing(): Flow<List<User>> =
        userDao.observeFollowing().map { it.map { e -> e.toDomain() } }

    override suspend fun isUsernameAvailable(username: String): Result<Boolean> = safeCall {
        val snap = firestore.collection("users").whereEqualTo("username", username).limit(1).get().await()
        snap.isEmpty
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Aktif kullanıcının hedef kullanıcıyla ilişki durumunu Firestore'dan okur
     * ve entity'ye yansıtır.
     */
    private suspend fun enrichWithRelationship(entity: UserEntity): UserEntity {
        val me = currentUid ?: return entity
        val docId = "${me}_${entity.userId}"
        val followDoc = firestore.collection("follows").document(docId).get().await()

        val isFollowing = followDoc.exists() && followDoc.getString("status") == "active"
        val requestSent = followDoc.exists() && followDoc.getString("status") == "pending"

        return entity.copy(
            isFollowing       = isFollowing,
            followRequestSent = requestSent
        )
    }

    private fun mapToEntity(id: String, data: Map<String, Any>): UserEntity = UserEntity(
        userId           = id,
        username         = data["username"] as? String ?: "",
        displayName      = data["displayName"] as? String ?: "",
        email            = data["email"] as? String,
        phoneNumber      = data["phoneNumber"] as? String,
        bio              = data["bio"] as? String,
        profileImageUrl  = data["profileImageUrl"] as? String,
        coverImageUrl    = data["coverImageUrl"] as? String,
        isVerified       = data["isVerified"] as? Boolean ?: false,
        followersCount   = (data["followersCount"] as? Number)?.toInt() ?: 0,
        followingCount   = (data["followingCount"] as? Number)?.toInt() ?: 0,
        likesCount       = (data["likesCount"] as? Number)?.toInt() ?: 0,
        isPrivate        = data["isPrivate"] as? Boolean ?: false,
        isFollowing      = false,
        isFollowedBy     = false,
        followRequestSent = false,
        createdAt        = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        updatedAt        = (data["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        lastSyncedAt     = System.currentTimeMillis()
    )
}
