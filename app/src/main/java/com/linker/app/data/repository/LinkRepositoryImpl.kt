package com.linker.app.data.repository

import com.linker.app.core.util.Result
import com.linker.app.core.util.RetryUtil
import com.linker.app.core.util.safeCall
import com.linker.app.data.local.dao.ChatDao
import com.linker.app.data.local.dao.LinkDao
import com.linker.app.data.local.dao.UserDao
import com.linker.app.data.local.mapper.toDomain
import com.linker.app.data.local.mapper.toEntity
import com.linker.app.domain.model.DescriptionVersion
import com.linker.app.domain.model.Link
import com.linker.app.domain.model.LinkType
import com.linker.app.domain.model.ReportReason
import com.linker.app.domain.repository.LinkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LinkRepositoryImpl @Inject constructor(
    private val linkDao: LinkDao,
    private val userDao: UserDao,
    private val firestore: com.google.firebase.firestore.FirebaseFirestore,
    private val auth: com.google.firebase.auth.FirebaseAuth,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context
) : LinkRepository {

    // Helper to resolve a single UserEntity into a domain User
    private suspend fun resolveAuthor(authorId: String): com.linker.app.domain.model.User {
        val local = userDao.getUserById(authorId)?.toDomain()
        if (local != null) return local
        
        try {
            val doc = firestore.collection("users").document(authorId).get().await()
            if (doc.exists()) {
                val user = com.linker.app.domain.model.User(
                    userId = authorId,
                    username = doc.getString("username") ?: "user",
                    displayName = doc.getString("displayName") ?: "User",
                    profileImageUrl = doc.getString("profileImageUrl"),
                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                    updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                )
                userDao.insertUser(user.toEntity())
                return user
            }
        } catch (_: Exception) {}

        return com.linker.app.domain.model.User(
            userId = authorId,
            username = "user_${authorId.take(6)}",
            displayName = "User",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun com.linker.app.domain.model.User.toEntity() = com.linker.app.data.local.entity.UserEntity(
        userId = userId,
        username = username,
        displayName = displayName,
        profileImageUrl = profileImageUrl,
        email = getEmail(),
        phoneNumber = getPhoneNumber(),
        bio = bio,
        coverImageUrl = coverImageUrl,
        isVerified = isVerified,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    override fun observeFeed(): Flow<Result<List<Link>>> =
        linkDao.observeAllLinks().map { entities ->
            val links = entities.map { entity ->
                entity.toDomain(resolveAuthor(entity.authorId))
            }
            Result.Success(links)
        }

    override suspend fun refreshFeed(limit: Int): Result<List<Link>> = safeCall {
        try {
            val snapshot = firestore.collection("links")
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()
            
            val entities = snapshot.documents.mapNotNull { doc ->
                val typeStr = doc.getString("linkType") ?: "FEED"
                val mappedType = when (typeStr) {
                    "VIDEO" -> com.linker.app.data.local.entity.LinkType.VIDEO
                    "REEL" -> com.linker.app.data.local.entity.LinkType.REEL
                    else -> com.linker.app.data.local.entity.LinkType.FEED
                }
                @Suppress("UNCHECKED_CAST")
                val mediaList = (doc.get("mediaUrls") as? List<String>) ?: emptyList()
                com.linker.app.data.local.entity.LinkEntity(
                    linkId = doc.id,
                    authorId = doc.getString("authorId") ?: "",
                    linkType = mappedType,
                    description = doc.getString("description"),
                    mediaUrls = mediaList,
                    thumbnailUrl = doc.getString("thumbnailUrl"),
                    location = doc.getString("location"),
                    likesCount = (doc.getLong("likesCount") ?: 0L).toInt(),
                    commentsCount = (doc.getLong("commentsCount") ?: 0L).toInt(),
                    sharesCount = (doc.getLong("sharesCount") ?: 0L).toInt(),
                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                    updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                )
            }
            if (entities.isNotEmpty()) {
                linkDao.insertLinks(entities)
            }
        } catch (e: Exception) {
            android.util.Log.w("LinkRepositoryImpl", "Remote feed refresh failed: ${e.message}")
        }
        
        linkDao.getAllLinks(limit, 0).mapNotNull { entity ->
            runCatching { entity.toDomain(resolveAuthor(entity.authorId)) }.getOrNull()
        }
    }

    override suspend fun loadMoreFeed(beforeTimestamp: Long, limit: Int): Result<List<Link>> = safeCall {
        try {
            val snapshot = firestore.collection("links")
                .whereLessThan("createdAt", beforeTimestamp)
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()
            
            val entities = snapshot.documents.mapNotNull { doc ->
                val typeStr = doc.getString("linkType") ?: "FEED"
                val mappedType = when (typeStr) {
                    "VIDEO" -> com.linker.app.data.local.entity.LinkType.VIDEO
                    "REEL" -> com.linker.app.data.local.entity.LinkType.REEL
                    else -> com.linker.app.data.local.entity.LinkType.FEED
                }
                @Suppress("UNCHECKED_CAST")
                val mediaList = (doc.get("mediaUrls") as? List<String>) ?: emptyList()
                com.linker.app.data.local.entity.LinkEntity(
                    linkId = doc.id,
                    authorId = doc.getString("authorId") ?: "",
                    linkType = mappedType,
                    description = doc.getString("description"),
                    mediaUrls = mediaList,
                    thumbnailUrl = doc.getString("thumbnailUrl"),
                    location = doc.getString("location"),
                    likesCount = (doc.getLong("likesCount") ?: 0L).toInt(),
                    commentsCount = (doc.getLong("commentsCount") ?: 0L).toInt(),
                    sharesCount = (doc.getLong("sharesCount") ?: 0L).toInt(),
                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                    updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                )
            }
            if (entities.isNotEmpty()) {
                linkDao.insertLinks(entities)
            }
        } catch (e: Exception) {
            android.util.Log.w("LinkRepositoryImpl", "Remote loadMoreFeed failed: ${e.message}")
        }

        linkDao.getAllLinks(limit, 0).mapNotNull { entity ->
            runCatching { entity.toDomain(resolveAuthor(entity.authorId)) }.getOrNull()
        }
    }

    override suspend fun getLinkById(linkId: String): Result<Link> = safeCall {
        val entity = linkDao.getLinkById(linkId) ?: throw Exception("Link $linkId not found")
        entity.toDomain(resolveAuthor(entity.authorId))
    }

    override fun observeLinkById(linkId: String): Flow<Result<Link?>> =
        linkDao.observeLinkById(linkId).map { entity ->
            val link = entity?.let { runCatching { it.toDomain(resolveAuthor(it.authorId)) }.getOrNull() }
            Result.Success(link)
        }

    override fun observeLinksByAuthor(authorId: String): Flow<Result<List<Link>>> =
        linkDao.observeLinksByAuthor(authorId).map { entities ->
            val links = entities.mapNotNull { runCatching { it.toDomain(resolveAuthor(it.authorId)) }.getOrNull() }
            Result.Success(links)
        }

    override fun observeSavedLinks(): Flow<Result<List<Link>>> =
        linkDao.observeSavedLinks().map { entities ->
            val links = entities.mapNotNull { runCatching { it.toDomain(resolveAuthor(it.authorId)) }.getOrNull() }
            Result.Success(links)
        }

    override fun observeRelinkedLinks(): Flow<Result<List<Link>>> =
        linkDao.observeRelinkedLinks().map { entities ->
            val links = entities.mapNotNull { runCatching { it.toDomain(resolveAuthor(it.authorId)) }.getOrNull() }
            Result.Success(links)
        }

    override suspend fun createLink(
        linkType: LinkType,
        description: String?,
        mediaLocalPaths: List<String>,
        location: String?
    ): Result<Link> = safeCall {
        val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            ?: throw IllegalStateException("Not authenticated")
        
        val linkId = java.util.UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        // Local placeholder for immediate UI feedback
        val placeholderMedia = mediaLocalPaths.map { "placeholder://$it" }

        val mappedLinkType = when(linkType) {
            LinkType.FEED -> com.linker.app.data.local.entity.LinkType.FEED
            LinkType.VIDEO -> com.linker.app.data.local.entity.LinkType.VIDEO
            LinkType.REEL -> com.linker.app.data.local.entity.LinkType.REEL
        }

        val entity = com.linker.app.data.local.entity.LinkEntity(
            linkId = linkId,
            authorId = currentUserId,
            linkType = mappedLinkType,
            description = description,
            mediaUrls = placeholderMedia,
            thumbnailUrl = if (mappedLinkType != com.linker.app.data.local.entity.LinkType.FEED) "placeholder" else null,
            videoDuration = if (mappedLinkType != com.linker.app.data.local.entity.LinkType.FEED) 15 else null,
            location = location,
            likesCount = 0,
            commentsCount = 0,
            sharesCount = 0,
            relinksCount = 0,
            savesCount = 0,
            viewsCount = 0,
            isLiked = false,
            isSaved = false,
            isRelinked = false,
            createdAt = now,
            updatedAt = now,
            lastSyncedAt = now
        )

        linkDao.insertLink(entity)

        try {
            firestore.collection("links").document(linkId).set(
                mapOf(
                    "authorId" to currentUserId,
                    "linkType" to linkType.name,
                    "description" to (description ?: ""),
                    "location" to (location ?: ""),
                    "mediaUrls" to emptyList<String>(),
                    "likesCount" to 0,
                    "commentsCount" to 0,
                    "sharesCount" to 0,
                    "createdAt" to now,
                    "updatedAt" to now
                ),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()
        } catch (e: Exception) {
            android.util.Log.w("LinkRepositoryImpl", "Could not create initial remote link doc: ${e.message}")
        }

        if (mediaLocalPaths.isNotEmpty()) {
            val workData = androidx.work.workDataOf(
                "targetId" to linkId,
                "targetType" to "LINK",
                "mediaLocalPaths" to mediaLocalPaths.toTypedArray()
            )
            val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.linker.app.core.work.CloudinaryUploadWorker>()
                .setInputData(workData)
                .setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                        .build()
                )
                .build()
            
            androidx.work.WorkManager.getInstance(appContext).enqueue(workRequest)
        }

        entity.toDomain(resolveAuthor(currentUserId))
    }

    override suspend fun deleteLink(linkId: String): Result<Unit> = safeCall {
        linkDao.deleteLinkById(linkId)
        try {
            firestore.collection("links").document(linkId).delete().await()
        } catch (e: Exception) {
            android.util.Log.w("LinkRepositoryImpl", "Remote link delete failed: ${e.message}")
        }
    }

    override suspend fun deleteLinks(linkIds: List<String>): Result<Int> = safeCall {
        var count = 0
        for (id in linkIds) {
            linkDao.deleteLinkById(id)
            try {
                firestore.collection("links").document(id).delete().await()
            } catch (e: Exception) {
                // Ignore failure
            }
            count++
        }
        count
    }

    override suspend fun toggleLike(linkId: String): Result<Boolean> = safeCall {
        val entity = linkDao.getLinkById(linkId) ?: throw Exception("Link not found")
        val newLiked = !entity.isLiked
        val delta = if (newLiked) 1 else -1
        linkDao.updateLikeStatus(linkId, newLiked, delta)
        
        val currentUserId = auth.currentUser?.uid
        if (currentUserId != null) {
            try {
                val linkRef = firestore.collection("links").document(linkId)
                val likeRef = linkRef.collection("likes").document(currentUserId)
                if (newLiked) {
                    likeRef.set(mapOf("likedAt" to System.currentTimeMillis())).await()
                    linkRef.update("likesCount", com.google.firebase.firestore.FieldValue.increment(1)).await()
                } else {
                    likeRef.delete().await()
                    linkRef.update("likesCount", com.google.firebase.firestore.FieldValue.increment(-1)).await()
                }
            } catch (e: Exception) {
                android.util.Log.w("LinkRepositoryImpl", "Remote like sync failed: ${e.message}")
            }
        }
        newLiked
    }

    override suspend fun toggleSave(linkId: String): Result<Boolean> = safeCall {
        val entity = linkDao.getLinkById(linkId) ?: throw Exception("Link not found")
        val newSaved = !entity.isSaved
        linkDao.updateSaveStatus(linkId, newSaved)
        newSaved
    }

    override suspend fun toggleRelink(linkId: String): Result<Boolean> = safeCall {
        val entity = linkDao.getLinkById(linkId) ?: throw Exception("Link not found")
        val newRelinked = !entity.isRelinked
        val delta = if (newRelinked) 1 else -1
        linkDao.updateRelinkStatus(linkId, newRelinked, delta)
        newRelinked
    }

    override fun recordView(linkId: String) {
        // Optimistic local update only; batched sync handled by WorkManager
        // TODO: implement view batching
    }

    // ── Description Editing ────────────────────────────────────────────────

    override suspend fun updateLinkDescription(linkId: String, newDescription: String): Result<Unit> =
        RetryUtil.retrySafeCall {
            val currentUserId = auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")
            val linkRef = firestore.collection("links").document(linkId)
            val linkDoc = linkRef.get().await()

            val authorId = linkDoc.getString("authorId") ?: ""
            if (authorId != currentUserId) throw SecurityException("Only the author can edit this link")

            val currentEditCount = (linkDoc.getLong("editCount") ?: 0L).toInt()
            if (currentEditCount >= Link.MAX_DESCRIPTION_EDITS) {
                throw IllegalStateException("Bu gönderi en fazla ${Link.MAX_DESCRIPTION_EDITS} kez düzenlenebilir")
            }

            val oldDescription = linkDoc.getString("description")
            val now = System.currentTimeMillis()
            val newVersion = currentEditCount + 1

            val batch = firestore.batch()

            // Save old version to history sub-collection
            if (oldDescription != null) {
                val historyRef = linkRef.collection("descriptionHistory").document("v$newVersion")
                batch.set(historyRef, mapOf(
                    "content" to oldDescription,
                    "editedAt" to now,
                    "editedByUserId" to currentUserId,
                    "version" to newVersion
                ))
            }

            // Update the link document
            batch.update(linkRef, mapOf(
                "description" to newDescription,
                "editCount" to newVersion,
                "updatedAt" to now
            ))

            batch.commit().await()
        }

    override suspend fun getLinkDescriptionHistory(linkId: String): Result<List<DescriptionVersion>> =
        RetryUtil.retrySafeCall {
            val historySnapshot = firestore
                .collection("links")
                .document(linkId)
                .collection("descriptionHistory")
                .orderBy("version", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .await()

            historySnapshot.documents.mapNotNull { doc ->
                val content = doc.getString("content") ?: return@mapNotNull null
                val editedAt = doc.getLong("editedAt") ?: return@mapNotNull null
                val editedByUserId = doc.getString("editedByUserId") ?: return@mapNotNull null
                val version = (doc.getLong("version") ?: return@mapNotNull null).toInt()
                DescriptionVersion(content, editedAt, editedByUserId, version)
            }
        }

    // ── Sharing & Safety ────────────────────────────────────────────────

    override suspend fun sendLinkToDm(linkId: String, recipientUserId: String): Result<Unit> =
        RetryUtil.retrySafeCall {
            val currentUserId = auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")

            // Standard private chat ID format used across ChatRepository
            val sortedIds = listOf(currentUserId, recipientUserId).sorted()
            val chatId = "private_${sortedIds[0]}_${sortedIds[1]}"
            val messageId = java.util.UUID.randomUUID().toString()
            val now = System.currentTimeMillis()

            // Create a MessageType.LINK message in the chat
            firestore.collection("chats").document(chatId)
                .collection("messages")
                .document(messageId)
                .set(mapOf(
                    "messageId" to messageId,
                    "chatId" to chatId,
                    "senderId" to currentUserId,
                    "messageType" to "LINK",
                    "linkId" to linkId,
                    "sharedLinkId" to linkId,
                    "content" to "🔗 Link",
                    "createdAt" to now,
                    "updatedAt" to now,
                    "messageStatus" to "SENT"
                )).await()

            // Update chat's last message metadata
            firestore.collection("chats").document(chatId)
                .update(mapOf(
                    "lastMessageAt" to now,
                    "lastMessageText" to "🔗 Link paylaşıldı"
                )).await()
        }

    override suspend fun reportLink(linkId: String, reason: ReportReason): Result<Unit> =
        RetryUtil.retrySafeCall {
            val currentUserId = auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")
            val reportId = "${currentUserId}_${linkId}"
            firestore.collection("reports").document(reportId).set(
                mapOf(
                    "reporterId" to currentUserId,
                    "contentId" to linkId,
                    "contentType" to "link",
                    "reason" to reason.name,
                    "createdAt" to System.currentTimeMillis()
                )
            ).await()
        }

    override suspend fun getShareableLink(linkId: String): Result<String> {
        return Result.Success("https://linker.app/link/$linkId")
    }
}
