package com.linker.app.data.repository

import androidx.annotation.Keep
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.linker.app.core.util.Result
import com.linker.app.core.util.RetryUtil
import com.linker.app.domain.model.ReportReason
import com.linker.app.domain.model.Story
import com.linker.app.domain.model.StoryAuthor
import com.linker.app.domain.model.StoryMediaType
import com.linker.app.domain.model.User
import com.linker.app.domain.model.UserMetrics
import com.linker.app.domain.model.UserPrivacy
import com.linker.app.domain.model.UserReference
import com.linker.app.domain.model.UserRelationship
import com.linker.app.domain.model.UserStories
import com.linker.app.domain.repository.StoryRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.collections.forEach


/**
 * Story Repository Implementation
 *
 * Manages story data from Firestore with local caching
 */
@Singleton
class StoryRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context
) : StoryRepository {

    private val viewedStoriesCache = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val likedStoriesCache = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private val storiesCollection = firestore.collection("stories")
    private val usersCollection = firestore.collection("users")

    override fun observeActiveUserStories(): Flow<Result<List<UserStories>>> = callbackFlow {
        val now = System.currentTimeMillis()

        val listener = storiesCollection
            .whereGreaterThan("expiresAt", now)
            .orderBy("expiresAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.Success(emptyList()))
                    return@addSnapshotListener
                }

                launch(kotlinx.coroutines.Dispatchers.IO) {
                    val dataList = snapshot?.documents?.mapNotNull { doc ->
                        doc.toObject(StoryDocument::class.java)?.let { data ->
                            Pair(doc.id, data)
                        }
                    } ?: emptyList()

                    if (dataList.isEmpty()) {
                        trySend(Result.Success(emptyList()))
                        return@launch
                    }

                    val currentUserId = auth.currentUser?.uid ?: ""

                    // Filter stories based on privacy settings
                    val currentFollowingIds = if (currentUserId.isNotEmpty()) {
                        try {
                            usersCollection.document(currentUserId).collection("following").get().await().documents.map { it.id }.toSet()
                        } catch (_: Exception) { emptySet() }
                    } else emptySet()

                    val visibleDataList = dataList.filter { (_, data) ->
                        if (data.authorId == currentUserId) return@filter true
                        when (data.privacy.uppercase()) {
                            "PUBLIC" -> true
                            "FOLLOWERS" -> currentFollowingIds.contains(data.authorId)
                            "CLOSE_FRIENDS" -> data.closeFriends?.contains(currentUserId) == true
                            else -> true
                        }
                    }

                    if (visibleDataList.isEmpty()) {
                        trySend(Result.Success(emptyList()))
                        return@launch
                    }

                    // Batch fetch users to prevent N+1 queries
                    val authorIds = visibleDataList.map { it.second.authorId }.distinct()
                    val usersMap = mutableMapOf<String, StoryAuthor>()

                    authorIds.chunked(10).forEach { chunk ->
                        val userDocs = usersCollection.whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk).get().await()
                        userDocs.documents.forEach { authorDoc ->
                            val user = StoryAuthor(
                                userId = authorDoc.id,
                                username = authorDoc.getString("username") ?: "",
                                displayName = authorDoc.getString("displayName") ?: "",
                                profileImageUrl = authorDoc.getString("profileImageUrl"),
                            )
                            usersMap[authorDoc.id] = user
                        }
                    }

                    // Prepopulate liked cache from document likedBy field
                    visibleDataList.forEach { (id, data) ->
                        if (currentUserId.isNotEmpty() && data.likedBy.contains(currentUserId)) {
                            likedStoriesCache.add(id)
                        }
                    }

                    val unverifiedStories = visibleDataList.filter { (id, _) -> !viewedStoriesCache.contains(id) }
                    if (currentUserId.isNotEmpty() && unverifiedStories.isNotEmpty()) {
                        coroutineScope {
                            val deferreds = unverifiedStories.take(25).map { pair ->
                                async {
                                    try {
                                        val viewerDoc = storiesCollection.document(pair.first)
                                            .collection("viewers")
                                            .document(currentUserId)
                                            .get()
                                            .await()
                                        if (viewerDoc.exists()) {
                                            viewedStoriesCache.add(pair.first)
                                        }
                                    } catch (_: Exception) {}
                                }
                            }
                            deferreds.awaitAll()
                        }
                    }

                    val stories = visibleDataList.mapNotNull { (id, data) ->
                        val author = usersMap[data.authorId] ?: return@mapNotNull null
                        val mappedType = try {
                            StoryMediaType.valueOf(data.mediaType)
                        } catch (_: Exception) {
                            StoryMediaType.IMAGE
                        }
                        val storyDuration = if (mappedType == StoryMediaType.VIDEO) {
                            (data.duration ?: 15).coerceAtLeast(1)
                        } else null

                        val isLiked = (currentUserId.isNotEmpty() && data.likedBy.contains(currentUserId)) || likedStoriesCache.contains(id)

                        Story(
                            storyId = id,
                            author = author,
                            mediaUrl = data.mediaUrl,
                            mediaType = mappedType,
                            thumbnailUrl = data.thumbnailUrl,
                            duration = storyDuration,
                            caption = data.caption,
                            viewsCount = data.viewsCount,
                            likesCount = data.likesCount,
                            isViewed = viewedStoriesCache.contains(id),
                            isLiked = isLiked,
                            createdAt = data.createdAt,
                            expiresAt = data.expiresAt
                        )
                    }
                    val grouped = stories.groupBy { it.author.userId }
                        .map { (_, authorStories) ->
                            UserStories(
                                author = authorStories.first().author,
                                stories = authorStories.sortedBy { it.createdAt }
                            )
                        }
                        .sortedByDescending { it.hasUnviewed }

                    trySend(Result.Success(grouped))
                }
            }

        awaitClose { listener.remove() }
    }

    override suspend fun refreshStories(limit: Int): Result<List<UserStories>> = Result.Success(emptyList())

    override suspend fun loadMoreStories(beforeTimestamp: Long, limit: Int): Result<List<UserStories>> = Result.Success(emptyList())

    override suspend fun getStoriesByUser(userId: String): Result<List<Story>> = RetryUtil.retrySafeCall {
        val now = System.currentTimeMillis()
        val snapshot = storiesCollection
            .whereEqualTo("authorId", userId)
            .whereGreaterThan("expiresAt", now)
            .get()
            .await()
            
        if (snapshot.isEmpty) return@retrySafeCall emptyList()
        
        val authorDoc = usersCollection.document(userId).get().await()
        val author = StoryAuthor(
            userId = userId,
            username = authorDoc.getString("username") ?: "",
            displayName = authorDoc.getString("displayName") ?: "",
            profileImageUrl = authorDoc.getString("profileImageUrl"),
        )

        snapshot.documents.mapNotNull { doc ->
            doc.toObject(StoryDocument::class.java)?.let { data ->
                val mappedType = try {
                    StoryMediaType.valueOf(data.mediaType)
                } catch (_: Exception) {
                    StoryMediaType.IMAGE
                }
                val storyDuration = if (mappedType == StoryMediaType.VIDEO) {
                    (data.duration ?: 15).coerceAtLeast(1)
                } else null

                Story(
                    storyId = doc.id,
                    author = author,
                    mediaUrl = data.mediaUrl,
                    mediaType = mappedType,
                    thumbnailUrl = data.thumbnailUrl,
                    duration = storyDuration,
                    caption = data.caption,
                    viewsCount = data.viewsCount,
                    likesCount = data.likesCount,
                    isViewed = false,
                    createdAt = data.createdAt,
                    expiresAt = data.expiresAt
                )
            }
        }
    }

    override suspend fun createStory(
        mediaLocalPath: String,
        mediaType: StoryMediaType,
        caption: String?,
        privacy: com.linker.app.domain.repository.StoryPrivacy
    ): Result<Story> = RetryUtil.retrySafeCall {
        val currentUser = auth.currentUser ?: throw IllegalStateException("Not authenticated")
        val storyId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val expiresAt = now + com.linker.app.core.util.TimeConstants.DAY_MS

        val persistentPath = com.linker.app.core.util.MediaUtils.copyUriToInternalStorage(appContext, mediaLocalPath)
        val mediaUrl = "placeholder://$persistentPath"

        val calculatedDuration = if (mediaType == StoryMediaType.VIDEO) {
            (com.linker.app.core.util.MediaUtils.getVideoDurationSeconds(appContext, persistentPath) ?: 15.0).toInt().coerceAtLeast(1)
        } else null

        val storyData = hashMapOf(
            "storyId" to storyId,
            "authorId" to currentUser.uid,
            "mediaUrl" to mediaUrl,
            "mediaType" to mediaType.name,
            "caption" to caption,
            "duration" to calculatedDuration,
            "privacy" to privacy.name,
            "viewsCount" to 0,
            "likesCount" to 0,
            "createdAt" to now,
            "expiresAt" to expiresAt,
            "uploadStatus" to "PENDING"
        )

        storiesCollection.document(storyId).set(storyData).await()

        // Enqueue WorkManager job to upload the story media
        val workData = androidx.work.workDataOf(
            "targetId" to storyId,
            "targetType" to "STORY",
            "mediaLocalPath" to persistentPath
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

        Story(
            storyId = storyId,
            author = StoryAuthor(
                userId = currentUser.uid,
                username = currentUser.displayName ?: "",
                displayName = currentUser.displayName ?: "",
                profileImageUrl = currentUser.photoUrl?.toString(),
            ),
            mediaUrl = mediaUrl,
            mediaType = mediaType,
            thumbnailUrl = null,
            duration = calculatedDuration,
            caption = caption,
            viewsCount = 0,
            likesCount = 0,
            isViewed = false,
            createdAt = now,
            expiresAt = expiresAt
        )
    }

    override suspend fun markStoryAsViewed(storyId: String): Result<Unit> = RetryUtil.retrySafeCall {
        val currentUserId = auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")

        if (viewedStoriesCache.contains(storyId)) {
            return@retrySafeCall
        }

        val viewerRef = storiesCollection
            .document(storyId)
            .collection("viewers")
            .document(currentUserId)

        val viewerDoc = viewerRef.get().await()
        if (viewerDoc.exists()) {
            viewedStoriesCache.add(storyId)
            return@retrySafeCall
        }

        val batch = firestore.batch()
        batch.set(viewerRef, mapOf("viewedAt" to System.currentTimeMillis(), "userId" to currentUserId))
        val storyRef = storiesCollection.document(storyId)
        batch.update(storyRef, "viewsCount", com.google.firebase.firestore.FieldValue.increment(1))
        batch.commit().await()
        viewedStoriesCache.add(storyId)
    }

    override suspend fun deleteStory(storyId: String): Result<Unit> = RetryUtil.retrySafeCall {
        val currentUserId = auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")
        
        val doc = storiesCollection.document(storyId).get().await()
        if (doc.exists()) {
            val authorId = doc.getString("authorId")
            if (authorId == currentUserId) {
                doc.reference.delete().await()
            } else {
                throw SecurityException("Unauthorized: You can only delete your own stories")
            }
        }
    }

    override suspend fun purgeExpiredStories(): Result<Unit> = RetryUtil.retrySafeCall {
        val now = System.currentTimeMillis()
        val snapshot = storiesCollection
            .whereLessThan("expiresAt", now)
            .get()
            .await()

        snapshot.documents.chunked(500).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { doc ->
                batch.delete(doc.reference)
            }
            batch.commit().await()
        }
    }

    override suspend fun getViewCount(storyId: String): Result<Int> = RetryUtil.retrySafeCall {
        val snapshot = storiesCollection.document(storyId).collection("viewers").get().await()
        snapshot.size()
    }

    override suspend fun getViewers(storyId: String): Result<List<com.linker.app.domain.repository.StoryViewer>> = RetryUtil.retrySafeCall {
        val viewersSnap = storiesCollection.document(storyId).collection("viewers").get().await()
        if (viewersSnap.isEmpty) return@retrySafeCall emptyList()

        val viewerUserIds = viewersSnap.documents.mapNotNull { it.getString("userId") ?: it.id }
        val usersMap = mutableMapOf<String, StoryAuthor>()

        viewerUserIds.chunked(10).forEach { chunk ->
            val userDocs = usersCollection.whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk).get().await()
            userDocs.documents.forEach { doc ->
                usersMap[doc.id] = StoryAuthor(
                    userId = doc.id,
                    username = doc.getString("username") ?: "",
                    displayName = doc.getString("displayName") ?: "",
                    profileImageUrl = doc.getString("profileImageUrl")
                )
            }
        }

        val likesSnap = storiesCollection.document(storyId).collection("likes").get().await()
        val likedUserIds = likesSnap.documents.map { it.id }.toSet()

        viewersSnap.documents.mapNotNull { vDoc ->
            val uId = vDoc.getString("userId") ?: vDoc.id
            val author = usersMap[uId] ?: return@mapNotNull null
            val viewedAt = vDoc.getLong("viewedAt") ?: System.currentTimeMillis()
            com.linker.app.domain.repository.StoryViewer(
                userId = uId,
                username = author.username.ifBlank { author.displayName },
                avatarUrl = author.profileImageUrl,
                viewedAt = viewedAt,
                hasLiked = likedUserIds.contains(uId)
            )
        }.sortedByDescending { it.viewedAt }
    }

    override suspend fun replyToStory(storyId: String, content: String): Result<Unit> = RetryUtil.retrySafeCall {
        val currentUserId = auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")
        val storyDoc = storiesCollection.document(storyId).get().await()
        val authorId = storyDoc.getString("authorId") ?: return@retrySafeCall
        
        // 1. Save reply to story replies subcollection
        val replyId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        storiesCollection.document(storyId).collection("replies").document(replyId).set(
            mapOf(
                "replyId" to replyId,
                "storyId" to storyId,
                "senderId" to currentUserId,
                "content" to content,
                "createdAt" to now
            )
        ).await()

        // 2. Send direct message in chat with story author
        try {
            val chatId = if (currentUserId < authorId) "${currentUserId}_${authorId}" else "${authorId}_${currentUserId}"
            val messageId = UUID.randomUUID().toString()
            val messageText = "📷 Hikayeye yanıt verdi: $content"
            
            firestore.collection("chats").document(chatId).collection("messages").document(messageId).set(
                mapOf(
                    "messageId" to messageId,
                    "chatId" to chatId,
                    "senderId" to currentUserId,
                    "recipientId" to authorId,
                    "content" to messageText,
                    "text" to messageText,
                    "messageType" to "TEXT",
                    "messageStatus" to "SENT",
                    "status" to "SENT",
                    "createdAt" to now,
                    "timestamp" to now
                )
            ).await()
            
            firestore.collection("chats").document(chatId).set(
                mapOf(
                    "chatId" to chatId,
                    "participantIds" to listOf(currentUserId, authorId),
                    "participants" to listOf(currentUserId, authorId),
                    "lastMessage" to messageText,
                    "lastMessageSenderId" to currentUserId,
                    "lastMessageTimestamp" to now,
                    "type" to "DIRECT",
                    "updatedAt" to now,
                    "createdAt" to now
                ),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()
        } catch (e: Exception) {
            android.util.Log.e("StoryRepo", "Failed to send chat message for story reply", e)
        }
    }
    override suspend fun getReplyCount(storyId: String): Result<Int> = Result.Success(0)
    override suspend fun updateStoryPrivacy(storyId: String, privacy: com.linker.app.domain.repository.StoryPrivacy): Result<Unit> = Result.Success(Unit)
    override suspend fun updateCloseFriendsList(userIds: List<String>): Result<Unit> = Result.Success(Unit)
    override suspend fun getCloseFriendsList(): Result<List<String>> = Result.Success(emptyList())
    override suspend fun addToHighlight(storyId: String, highlightId: String): Result<Unit> = Result.Success(Unit)
    override suspend fun removeFromHighlight(storyId: String, highlightId: String): Result<Unit> = Result.Success(Unit)
    override suspend fun getHighlights(userId: String): Result<List<com.linker.app.domain.repository.StoryHighlight>> = Result.Success(emptyList())
    override suspend fun createHighlight(title: String, coverStoryId: String?): Result<com.linker.app.domain.repository.StoryHighlight> = Result.Success(com.linker.app.domain.repository.StoryHighlight("", title, null, emptyList()))

    // ── New: Engagement & Safety ───────────────────────────────────────────

    override suspend fun toggleLikeStory(storyId: String): Result<Boolean> = RetryUtil.retrySafeCall {
        val currentUserId = auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")
        val likesRef = storiesCollection
            .document(storyId)
            .collection("likes")
            .document(currentUserId)

        val likeDoc = likesRef.get().await()
        val isLiked = likeDoc.exists()

        val batch = firestore.batch()
        if (isLiked) {
            likedStoriesCache.remove(storyId)
            batch.delete(likesRef)
            batch.update(
                storiesCollection.document(storyId),
                "likesCount", com.google.firebase.firestore.FieldValue.increment(-1),
                "likedBy", com.google.firebase.firestore.FieldValue.arrayRemove(currentUserId)
            )
        } else {
            likedStoriesCache.add(storyId)
            batch.set(likesRef, mapOf("likedAt" to System.currentTimeMillis(), "userId" to currentUserId))
            batch.update(
                storiesCollection.document(storyId),
                "likesCount", com.google.firebase.firestore.FieldValue.increment(1),
                "likedBy", com.google.firebase.firestore.FieldValue.arrayUnion(currentUserId)
            )
        }
        batch.commit().await()
        !isLiked
    }

    override suspend fun reactToStory(storyId: String, emoji: String?): Result<Unit> = RetryUtil.retrySafeCall {
        val currentUserId = auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")
        val reactionRef = storiesCollection
            .document(storyId)
            .collection("reactions")
            .document(currentUserId)

        if (emoji == null) {
            reactionRef.delete().await()
        } else {
            reactionRef.set(mapOf(
                "emoji" to emoji,
                "reactedAt" to System.currentTimeMillis()
            )).await()
        }
    }

    override suspend fun reportStory(storyId: String, reason: ReportReason): Result<Unit> = RetryUtil.retrySafeCall {
        val currentUserId = auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")
        val reportId = "${currentUserId}_${storyId}"
        firestore.collection("reports").document(reportId).set(
            mapOf(
                "reporterId" to currentUserId,
                "contentId" to storyId,
                "contentType" to "story",
                "reason" to reason.name,
                "createdAt" to System.currentTimeMillis()
            )
        ).await()
    }

    override suspend fun getShareableLink(storyId: String): Result<String> {
        // Deep link format: linker://story/{storyId}
        // In production, this would go through Firebase Dynamic Links
        return Result.Success("https://linker.app/story/$storyId")
    }

    @Keep
    private data class StoryDocument(
        val storyId: String = "",
        val authorId: String = "",
        val mediaUrl: String = "",
        val mediaType: String = "IMAGE",
        val thumbnailUrl: String? = null,
        val duration: Int? = null,
        val caption: String? = null,
        val privacy: String = "PUBLIC",
        val closeFriends: List<String>? = null,
        val viewsCount: Int = 0,
        val likesCount: Int = 0,
        val likedBy: List<String> = emptyList(),
        val createdAt: Long = 0,
        val expiresAt: Long = 0
    )
}
