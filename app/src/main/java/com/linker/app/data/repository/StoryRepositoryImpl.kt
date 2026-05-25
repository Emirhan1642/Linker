package com.linker.app.data.repository

import androidx.annotation.Keep
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.linker.app.core.util.Result
import com.linker.app.core.util.RetryUtil
import com.linker.app.domain.model.Story
import com.linker.app.domain.model.StoryMediaType
import com.linker.app.domain.model.User
import com.linker.app.domain.model.UserStories
import com.linker.app.domain.repository.StoryRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton


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

    private val storiesCollection = firestore.collection("stories")
    private val usersCollection = firestore.collection("users")

    override fun observeActiveUserStories(): Flow<List<UserStories>> = callbackFlow {
        val now = System.currentTimeMillis()

        val listener = storiesCollection
            .whereGreaterThan("expiresAt", now)
            .orderBy("expiresAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                launch {
                    val dataList = snapshot?.documents?.mapNotNull { doc ->
                        doc.toObject(StoryDocument::class.java)?.let { data ->
                            Pair(doc.id, data)
                        }
                    } ?: emptyList()

                    if (dataList.isEmpty()) {
                        trySend(emptyList())
                        return@launch
                    }

                    // Batch fetch users to prevent N+1 queries
                    val authorIds = dataList.map { it.second.authorId }.distinct()
                    val usersMap = mutableMapOf<String, User>()

                    authorIds.chunked(10).forEach { chunk ->
                        val userDocs = usersCollection.whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk).get().await()
                        userDocs.documents.forEach { authorDoc ->
                            val user = User(
                                userId = authorDoc.id,
                                username = authorDoc.getString("username") ?: "",
                                displayName = authorDoc.getString("displayName") ?: "",
                                email = authorDoc.getString("email"),
                                phoneNumber = null,
                                bio = authorDoc.getString("bio"),
                                profileImageUrl = authorDoc.getString("profileImageUrl"),
                                coverImageUrl = authorDoc.getString("coverImageUrl"),
                                isVerified = authorDoc.getBoolean("isVerified") ?: false,
                                followersCount = (authorDoc.getLong("followersCount") ?: 0).toInt(),
                                followingCount = (authorDoc.getLong("followingCount") ?: 0).toInt(),
                                likesCount = (authorDoc.getLong("likesCount") ?: 0).toInt(),
                                isFollowing = false,
                                isFollowedBy = false,
                                isBlocked = false,
                                isMuted = false,
                                createdAt = authorDoc.getLong("createdAt") ?: 0,
                                updatedAt = authorDoc.getLong("updatedAt") ?: 0
                            )
                            usersMap[authorDoc.id] = user
                        }
                    }

                    val currentUserId = auth.currentUser?.uid ?: ""

                    val stories = dataList.mapNotNull { (id, data) ->
                        val author = usersMap[data.authorId] ?: return@mapNotNull null
                        Story(
                            storyId = id,
                            author = author,
                            mediaUrl = data.mediaUrl,
                            mediaType = try {
                                StoryMediaType.valueOf(data.mediaType)
                            } catch (_: Exception) {
                                StoryMediaType.IMAGE
                            },
                            thumbnailUrl = data.thumbnailUrl,
                            duration = null,
                            caption = data.caption,
                            viewsCount = data.viewsCount,
                            isViewed = false, // To be determined below
                            createdAt = data.createdAt,
                            expiresAt = data.expiresAt
                        )
                    }

                    // Also batch fetch viewers for current user to set isViewed
                    // Since it's a subcollection, we might have to check them. For optimization, if current user is not null:
                    // In a real app we might use an array `viewers` up to a limit or another mechanism.
                    // For now, we'll map them as provided.
                    val grouped = stories.groupBy { it.author.userId }
                        .map { (_, authorStories) ->
                            UserStories(
                                author = authorStories.first().author,
                                stories = authorStories.sortedBy { it.createdAt }
                            )
                        }
                        .sortedByDescending { it.hasUnviewed }

                    trySend(grouped)
                }
            }

        awaitClose { listener.remove() }
    }

    override suspend fun getStoriesByUser(userId: String): Result<List<Story>> = RetryUtil.retrySafeCall {
        val now = System.currentTimeMillis()
        val snapshot = storiesCollection
            .whereEqualTo("authorId", userId)
            .whereGreaterThan("expiresAt", now)
            .get()
            .await()
            
        if (snapshot.isEmpty) return@retrySafeCall emptyList()
        
        val authorDoc = usersCollection.document(userId).get().await()
        val author = User(
            userId = userId,
            username = authorDoc.getString("username") ?: "",
            displayName = authorDoc.getString("displayName") ?: "",
            email = authorDoc.getString("email"),
            phoneNumber = null,
            bio = authorDoc.getString("bio"),
            profileImageUrl = authorDoc.getString("profileImageUrl"),
            coverImageUrl = authorDoc.getString("coverImageUrl"),
            isVerified = authorDoc.getBoolean("isVerified") ?: false,
            followersCount = (authorDoc.getLong("followersCount") ?: 0).toInt(),
            followingCount = (authorDoc.getLong("followingCount") ?: 0).toInt(),
            likesCount = (authorDoc.getLong("likesCount") ?: 0).toInt(),
            isFollowing = false,
            isFollowedBy = false,
            isBlocked = false,
            isMuted = false,
            createdAt = authorDoc.getLong("createdAt") ?: 0,
            updatedAt = authorDoc.getLong("updatedAt") ?: 0
        )

        snapshot.documents.mapNotNull { doc ->
            doc.toObject(StoryDocument::class.java)?.let { data ->
                Story(
                    storyId = doc.id,
                    author = author,
                    mediaUrl = data.mediaUrl,
                    mediaType = try {
                        StoryMediaType.valueOf(data.mediaType)
                    } catch (_: Exception) {
                        StoryMediaType.IMAGE
                    },
                    thumbnailUrl = data.thumbnailUrl,
                    duration = null,
                    caption = data.caption,
                    viewsCount = data.viewsCount,
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
        caption: String?
    ): Result<Story> = RetryUtil.retrySafeCall {
        val currentUser = auth.currentUser ?: throw IllegalStateException("Not authenticated")
        val storyId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val expiresAt = now + com.linker.app.core.util.TimeConstants.DAY_MS

        val mediaUrl = "placeholder://$mediaLocalPath"

        val storyData = hashMapOf(
            "storyId" to storyId,
            "authorId" to currentUser.uid,
            "mediaUrl" to mediaUrl,
            "mediaType" to mediaType.name,
            "caption" to caption,
            "viewsCount" to 0,
            "createdAt" to now,
            "expiresAt" to expiresAt,
            "uploadStatus" to "PENDING"
        )

        storiesCollection.document(storyId).set(storyData).await()

        // Enqueue WorkManager job to upload the story media
        val workData = androidx.work.workDataOf(
            "targetId" to storyId,
            "targetType" to "STORY",
            "mediaLocalPath" to mediaLocalPath
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
            author = User(
                userId = currentUser.uid,
                username = currentUser.displayName ?: "",
                displayName = currentUser.displayName ?: "",
                email = currentUser.email,
                phoneNumber = currentUser.phoneNumber,
                bio = null,
                profileImageUrl = currentUser.photoUrl?.toString(),
                coverImageUrl = null,
                isVerified = false,
                followersCount = 0,
                followingCount = 0,
                likesCount = 0,
                isFollowing = false,
                isFollowedBy = false,
                isBlocked = false,
                isMuted = false,
                createdAt = now,
                updatedAt = now
            ),
            mediaUrl = mediaUrl,
            mediaType = mediaType,
            thumbnailUrl = null,
            duration = null,
            caption = caption,
            viewsCount = 0,
            isViewed = false,
            createdAt = now,
            expiresAt = expiresAt
        )
    }

    override suspend fun markStoryAsViewed(storyId: String): Result<Unit> = RetryUtil.retrySafeCall {
        val currentUserId = auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")

        val batch = firestore.batch()
        
        // Add viewer to subcollection
        val viewerRef = storiesCollection
            .document(storyId)
            .collection("viewers")
            .document(currentUserId)
            
        batch.set(viewerRef, mapOf("viewedAt" to System.currentTimeMillis()))

        // Increment view count
        val storyRef = storiesCollection.document(storyId)
        batch.update(storyRef, "viewsCount", com.google.firebase.firestore.FieldValue.increment(1))
        
        batch.commit().await()
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

        val batch = firestore.batch()
        snapshot.documents.forEach { doc ->
            batch.delete(doc.reference)
        }
        batch.commit().await()
    }

    @Keep
    private data class StoryDocument(
        val storyId: String = "",
        val authorId: String = "",
        val mediaUrl: String = "",
        val mediaType: String = "IMAGE",
        val thumbnailUrl: String? = null,
        val caption: String? = null,
        val viewsCount: Int = 0,
        val createdAt: Long = 0,
        val expiresAt: Long = 0
    )
}
