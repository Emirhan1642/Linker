package com.linker.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.Transaction
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
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.tasks.await
import kotlin.math.max
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val userDao: UserDao,
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth
) : UserRepository {

    private val currentUid get() = firebaseAuth.currentUser?.uid

    // ── Current user stream ───────────────────────────────────────────────────
    //
    // AuthStateListener kullanılır — hesap değişince (Account Center switch)
    // yeni UID'nin Firestore belgesine otomatik abone olur.
    // Bu sayede ProfileViewModel'deki combine her hesap değişiminde doğru
    // kullanıcıyı yayınlar.

    private val currentUserFlow: Flow<User?> by lazy {
        callbackFlow {
            var firestoreListener: com.google.firebase.firestore.ListenerRegistration? = null

            fun resubscribe() {
                firestoreListener?.remove()
                val uid = firebaseAuth.currentUser?.uid
                if (uid == null) {
                    trySend(null)
                    return
                }
                firestoreListener = firestore.collection("users").document(uid)
                    .addSnapshotListener { snap, _ ->
                        snap?.data?.let { data ->
                            if (snap.exists()) {
                                trySend(mapToEntity(uid, data).toDomain())
                            } else {
                                trySend(null)
                            }
                        } ?: trySend(null)
                    }
            }

            val authListener = FirebaseAuth.AuthStateListener { resubscribe() }
            firebaseAuth.addAuthStateListener(authListener)
            resubscribe()

            awaitClose {
                firebaseAuth.removeAuthStateListener(authListener)
                firestoreListener?.remove()
            }
        }.shareIn(
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob()),
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            replay = 1
        )
    }

    override fun getCurrentUser(): Flow<User?> = currentUserFlow

    override suspend fun getUserById(userId: String): Result<User> = safeCall {
        val snap = firestore.collection("users").document(userId).get().await()
        snap.data?.let { data ->
            if (snap.exists()) {
                val enriched = enrichWithRelationship(mapToEntity(userId, data))
                userDao.insertUser(enriched)
                return@safeCall enriched.toDomain()
            }
        }
        val local = userDao.getUserById(userId)
        if (local != null) return@safeCall local.toDomain()
        throw Exception("User not found")
    }

    override suspend fun getUserByUsername(username: String): Result<User> = safeCall {
        val q = firestore.collection("users").whereEqualTo("username", username).limit(1).get().await()
        if (q.isEmpty) throw Exception("User '$username' not found")
        val doc = q.documents[0]
        val data = doc.data ?: throw Exception("User data is null")
        val entity = enrichWithRelationship(mapToEntity(doc.id, data))
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
            .distinctBy { it.id }.filter { it.id != me }
            .mapNotNull { doc -> doc.data?.let { mapToEntity(doc.id, it) } }
            
        val enrichedRemoteEntities = batchFetchRelationships(remoteEntities)
        enrichedRemoteEntities.forEach { userDao.insertUser(it) }
        val remoteIds = enrichedRemoteEntities.map { it.userId }.toSet()
        val localOnly = userDao.searchUsers(query, limit).filter { it.userId != me && it.userId !in remoteIds }
        (enrichedRemoteEntities + localOnly).take(limit).map { it.toDomain() }
    }

    override suspend fun followUser(targetUserId: String): Result<Unit> = safeCall {
        val me = currentUid ?: throw Exception("Not logged in")
        val docId = "${me}_${targetUserId}"
        val followRef = firestore.collection("follows").document(docId)
        val meRef = firestore.collection("users").document(me)
        val targetRef = firestore.collection("users").document(targetUserId)

        val isPrivate = firestore.runTransaction { tx ->
            // All reads first
            val existing = tx.get(followRef)
            if (existing.exists()) return@runTransaction null
            val targetSnap = tx.get(targetRef)
            val meSnap = tx.get(meRef)

            val privateAccount = targetSnap.getBoolean("isPrivate") ?: false
            val status = if (privateAccount) "pending" else "active"

            // Then writes
            tx.set(
                followRef,
                mapOf(
                    "followerId" to me,
                    "followedId" to targetUserId,
                    "status" to status,
                    "createdAt" to System.currentTimeMillis()
                )
            )
            if (!privateAccount) {
                updateCount(tx, meRef, "followingCount", 1)
                updateCount(tx, targetRef, "followersCount", 1)
            }
            privateAccount
        }.await()

        if (isPrivate == null) return@safeCall
        if (!isPrivate) {
            userDao.updateFollowingStatus(targetUserId, true)
        } else {
            userDao.updateRequestSentStatus(targetUserId, true)
        }
    }

    override suspend fun unfollowUser(targetUserId: String): Result<Unit> = safeCall {
        val me = currentUid ?: throw Exception("Not logged in")
        val docId = "${me}_${targetUserId}"
        val followRef = firestore.collection("follows").document(docId)
        val meRef = firestore.collection("users").document(me)
        val targetRef = firestore.collection("users").document(targetUserId)

        val status = firestore.runTransaction { tx ->
            // All reads first
            val existing = tx.get(followRef)
            if (!existing.exists()) return@runTransaction "none"
            val currentStatus = existing.getString("status") ?: "none"
            val meSnap = tx.get(meRef)
            val targetSnap = tx.get(targetRef)

            // Then writes
            tx.delete(followRef)
            if (currentStatus == "active") {
                updateCount(tx, meRef, "followingCount", -1)
                updateCount(tx, targetRef, "followersCount", -1)
            }
            currentStatus
        }.await()

        if (status == "none") return@safeCall
        userDao.updateFollowingStatus(targetUserId, false)
        userDao.updateRequestSentStatus(targetUserId, false)
    }

    override suspend fun cancelFollowRequest(targetUserId: String): Result<Unit> = safeCall {
        val me = currentUid ?: throw Exception("Not logged in")
        val docId = "${me}_${targetUserId}"
        val existing = firestore.collection("follows").document(docId).get().await()
        if (!existing.exists()) return@safeCall
        if (existing.getString("status") == "active") { unfollowUser(targetUserId); return@safeCall }
        firestore.collection("follows").document(docId).delete().await()
        userDao.updateRequestSentStatus(targetUserId, false)
    }

    override suspend fun acceptFollowRequest(fromUserId: String): Result<Unit> = safeCall {
        val me = currentUid ?: throw Exception("Not logged in")
        val docId = "${fromUserId}_${me}"
        val followRef = firestore.collection("follows").document(docId)
        val meRef = firestore.collection("users").document(me)
        val fromRef = firestore.collection("users").document(fromUserId)

        firestore.runTransaction { tx ->
            // All reads first
            val existing = tx.get(followRef)
            if (!existing.exists()) throw Exception("Follow request not found")
            val status = existing.getString("status") ?: "pending"
            if (status == "active") return@runTransaction null
            val fromSnap = tx.get(fromRef)
            val meSnap = tx.get(meRef)

            // Then writes
            tx.update(followRef, "status", "active")
            updateCount(tx, fromRef, "followingCount", 1)
            updateCount(tx, meRef, "followersCount", 1)
            null
        }.await()
    }

    override suspend fun declineFollowRequest(fromUserId: String): Result<Unit> = safeCall {
        val me = currentUid ?: throw Exception("Not logged in")
        val docId = "${fromUserId}_${me}"
        val followRef = firestore.collection("follows").document(docId)
        val meRef = firestore.collection("users").document(me)
        val fromRef = firestore.collection("users").document(fromUserId)

        firestore.runTransaction { tx ->
            // All reads first
            val existing = tx.get(followRef)
            if (!existing.exists()) return@runTransaction null
            val status = existing.getString("status") ?: "pending"
            val fromSnap = tx.get(fromRef)
            val meSnap = tx.get(meRef)

            // Then writes
            tx.delete(followRef)
            if (status == "active") {
                updateCount(tx, fromRef, "followingCount", -1)
                updateCount(tx, meRef, "followersCount", -1)
            }
            null
        }.await()
    }

    override suspend fun getFollowers(userId: String): Result<List<User>?> = safeCall {
        val me = currentUid
        if (me != null && me != userId) {
            val targetSnap = firestore.collection("users").document(userId).get().await()
            val isPrivate       = targetSnap.getBoolean("isPrivate") ?: false
            val hideFollowLists = targetSnap.getBoolean("hideFollowLists") ?: false
            if (hideFollowLists) return@safeCall null
            if (isPrivate) {
                val followDoc = firestore.collection("follows").document("${me}_${userId}").get().await()
                if (!(followDoc.exists() && followDoc.getString("status") == "active")) return@safeCall null
            }
        }
        val q = firestore.collection("follows")
            .whereEqualTo("followedId", userId).whereEqualTo("status", "active").get().await()
        val entities = q.documents.mapNotNull { doc ->
            val fid = doc.getString("followerId") ?: return@mapNotNull null
            val snap = firestore.collection("users").document(fid).get().await()
            snap.data?.let { data ->
                if (snap.exists()) mapToEntity(fid, data) else null
            }
        }
        batchFetchRelationships(entities).map { it.toDomain() }
    }

    override suspend fun getFollowing(userId: String): Result<List<User>?> = safeCall {
        val me = currentUid
        if (me != null && me != userId) {
            val targetSnap = firestore.collection("users").document(userId).get().await()
            val isPrivate       = targetSnap.getBoolean("isPrivate") ?: false
            val hideFollowLists = targetSnap.getBoolean("hideFollowLists") ?: false
            if (hideFollowLists) return@safeCall null
            if (isPrivate) {
                val followDoc = firestore.collection("follows").document("${me}_${userId}").get().await()
                if (!(followDoc.exists() && followDoc.getString("status") == "active")) return@safeCall null
            }
        }
        val q = firestore.collection("follows")
            .whereEqualTo("followerId", userId).whereEqualTo("status", "active").get().await()
        val entities = q.documents.mapNotNull { doc ->
            val fid = doc.getString("followedId") ?: return@mapNotNull null
            val snap = firestore.collection("users").document(fid).get().await()
            snap.data?.let { data ->
                if (snap.exists()) mapToEntity(fid, data) else null
            }
        }
        batchFetchRelationships(entities).map { it.toDomain() }
    }

    override suspend fun getPendingRequests(): Result<List<User>> = safeCall {
        val me = currentUid ?: throw Exception("Not logged in")
        val q = firestore.collection("follows")
            .whereEqualTo("followedId", me).whereEqualTo("status", "pending").get().await()
        q.documents.mapNotNull { doc ->
            val fid = doc.getString("followerId") ?: return@mapNotNull null
            val snap = firestore.collection("users").document(fid).get().await()
            snap.data?.let { data ->
                if (snap.exists()) mapToEntity(fid, data).toDomain() else null
            }
        }
    }

    override suspend fun getSentRequests(): Result<List<User>> = safeCall {
        val me = currentUid ?: throw Exception("Not logged in")
        val q = firestore.collection("follows")
            .whereEqualTo("followerId", me).whereEqualTo("status", "pending").get().await()
        val entities = q.documents.mapNotNull { doc ->
            val fid = doc.getString("followedId") ?: return@mapNotNull null
            val snap = firestore.collection("users").document(fid).get().await()
            snap.data?.let { data ->
                if (snap.exists()) mapToEntity(fid, data) else null
            }
        }
        batchFetchRelationships(entities).map { it.toDomain() }
    }

    override suspend fun blockUser(targetUserId: String): Result<Unit> = safeCall {
        val user = userDao.getUserById(targetUserId) ?: throw Exception("User not found")
        userDao.updateUser(user.copy(isBlocked = true, isFollowing = false))
        unfollowUser(targetUserId)
    }

    override suspend fun unblockUser(targetUserId: String): Result<Unit> = safeCall {
        val user = userDao.getUserById(targetUserId) ?: throw Exception("User not found")
        userDao.updateUser(user.copy(isBlocked = false))
    }

    override suspend fun updateProfile(displayName: String?, bio: String?, profileImageUrl: String?, coverImageUrl: String?): Result<User> = safeCall {
        val uid = currentUid ?: throw Exception("Not logged in")
        val map = mutableMapOf<String, Any>()
        displayName?.let { map["displayName"] = it }
        bio?.let { map["bio"] = it }
        profileImageUrl?.let { map["profileImageUrl"] = it }
        coverImageUrl?.let { map["coverImageUrl"] = it }
        map["updatedAt"] = System.currentTimeMillis()
        firestore.collection("users").document(uid).set(map, SetOptions.merge()).await()
        val snap = firestore.collection("users").document(uid).get().await()
        val data = snap.data ?: throw Exception("Failed to retrieve updated user data")
        val entity = mapToEntity(uid, data)
        userDao.insertUser(entity)
        entity.toDomain()
    }

    override suspend fun setPrivateAccount(isPrivate: Boolean): Result<Unit> = safeCall {
        val uid = currentUid ?: throw Exception("Not logged in")
        firestore.collection("users").document(uid).update("isPrivate", isPrivate).await()
        val local = userDao.getUserById(uid)
        if (local != null) userDao.updateUser(local.copy(isPrivate = isPrivate))
    }

    override suspend fun setHideFollowLists(hide: Boolean): Result<Unit> = safeCall {
        val uid = currentUid ?: throw Exception("Not logged in")
        firestore.collection("users").document(uid).update("hideFollowLists", hide).await()
        val local = userDao.getUserById(uid)
        if (local != null) userDao.updateUser(local.copy(hideFollowLists = hide))
    }

    override fun observeFollowing(): Flow<List<User>> =
        userDao.observeFollowing().map { it.map { e -> e.toDomain() } }

    override suspend fun isUsernameAvailable(username: String): Result<Boolean> = safeCall {
        val snap = firestore.collection("users").whereEqualTo("username", username).limit(1).get().await()
        snap.isEmpty
    }

    /**
     * Enrich user entity with relationship data (following/follower status)
     * 
     * PERFORMANCE NOTE:
     * This makes an additional Firestore query per user. For list operations
     * (search, followers, following), this can result in N+1 queries.
     * 
     * OPTIMIZATION STRATEGIES:
     * 1. Batch queries: Use Firestore batch reads for multiple users
     * 2. Cache: Store relationship data in local database
     * 3. Denormalize: Include relationship flags in user document
     * 
     * Current implementation is acceptable for single-user operations
     * (profile view, user detail). For lists, consider batch optimization.
     */
    private suspend fun enrichWithRelationship(entity: UserEntity): UserEntity {
        val me = currentUid ?: return entity
        
        // Single Firestore query to check follow relationship
        val followDoc = firestore.collection("follows")
            .document("${me}_${entity.userId}")
            .get()
            .await()
            
        return entity.copy(
            isFollowing       = followDoc.exists() && followDoc.getString("status") == "active",
            followRequestSent = followDoc.exists() && followDoc.getString("status") == "pending"
        )
    }

    private suspend fun batchFetchRelationships(entities: List<UserEntity>): List<UserEntity> {
        val me = currentUid ?: return entities
        if (entities.isEmpty()) return entities

        val followDocsMap = mutableMapOf<String, com.google.firebase.firestore.DocumentSnapshot>()
        val targetIds = entities.map { it.userId }.distinct()

        targetIds.chunked(10).forEach { chunk ->
            val query = firestore.collection("follows")
                .whereEqualTo("followerId", me)
                .whereIn("followedId", chunk)
                .get()
                .await()

            query.documents.forEach { doc ->
                val followedId = doc.getString("followedId")
                if (followedId != null) {
                    followDocsMap[followedId] = doc
                }
            }
        }

        return entities.map { entity ->
            val followDoc = followDocsMap[entity.userId]
            entity.copy(
                isFollowing       = followDoc?.getString("status") == "active",
                followRequestSent = followDoc?.getString("status") == "pending"
            )
        }
    }

    private suspend fun decrementSafe(collection: String, docId: String, field: String) {
        val snap = firestore.collection(collection).document(docId).get().await()
        val current = (snap.get(field) as? Number)?.toLong() ?: 0L
        if (current > 0) firestore.collection(collection).document(docId)
            .update(field, FieldValue.increment(-1)).await()
    }

    private fun updateCount(
        tx: Transaction,
        docRef: DocumentReference,
        field: String,
        delta: Long
    ) {
        tx.update(docRef, field, FieldValue.increment(delta))
    }

    private fun mapToEntity(id: String, data: Map<String, Any>): UserEntity = UserEntity(
        userId            = id,
        username          = data["username"] as? String ?: "",
        displayName       = data["displayName"] as? String ?: "",
        email             = data["email"] as? String,
        phoneNumber       = data["phoneNumber"] as? String,
        bio               = data["bio"] as? String,
        profileImageUrl   = data["profileImageUrl"] as? String,
        coverImageUrl     = data["coverImageUrl"] as? String,
        isVerified        = data["isVerified"] as? Boolean ?: false,
        followersCount    = (data["followersCount"] as? Number)?.toInt() ?: 0,
        followingCount    = (data["followingCount"] as? Number)?.toInt() ?: 0,
        likesCount        = (data["likesCount"] as? Number)?.toInt() ?: 0,
        isPrivate         = data["isPrivate"] as? Boolean ?: false,
        hideFollowLists   = data["hideFollowLists"] as? Boolean ?: false,
        isFollowing       = false, isFollowedBy = false, followRequestSent = false,
        createdAt  = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        updatedAt  = (data["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        lastSyncedAt = System.currentTimeMillis()
    )
}
