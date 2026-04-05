package com.linker.app.data.repository

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
    private val auth: FirebaseAuth
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

                val stories = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(StoryDocument::class.java)?.toStory(doc.id)
                } ?: emptyList()

                // Group by author
                val grouped = stories.groupBy { it.author.userId }
                    .map { (authorId, authorStories) ->
                        UserStories(
                            author = authorStories.first().author,
                            stories = authorStories.sortedBy { it.createdAt },
                            hasUnviewed = authorStories.any { !it.isViewed }
                        )
                    }
                    .sortedByDescending { it.hasUnviewed }

                trySend(grouped)
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

        snapshot.documents.mapNotNull { doc ->
            doc.toObject(StoryDocument::class.java)?.toStory(doc.id)
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
        val expiresAt = now + 24 * 60 * 60 * 1000 // 24 hours

        // TODO: Upload media to Cloudinary
        val mediaUrl = "placeholder://$mediaLocalPath"

        val storyData = hashMapOf(
            "storyId" to storyId,
            "authorId" to currentUser.uid,
            "mediaUrl" to mediaUrl,
            "mediaType" to mediaType.name,
            "caption" to caption,
            "viewsCount" to 0,
            "createdAt" to now,
            "expiresAt" to expiresAt
        )

        storiesCollection.document(storyId).set(storyData).await()

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

        // Add viewer to subcollection
        storiesCollection
            .document(storyId)
            .collection("viewers")
            .document(currentUserId)
            .set(mapOf("viewedAt" to System.currentTimeMillis()))
            .await()

        // Increment view count
        storiesCollection.document(storyId).update(
            "viewsCount", com.google.firebase.firestore.FieldValue.increment(1)
        ).await()
    }

    override suspend fun deleteStory(storyId: String): Result<Unit> = RetryUtil.retrySafeCall {
        storiesCollection.document(storyId).delete().await()
    }

    override suspend fun purgeExpiredStories(): Result<Unit> = RetryUtil.retrySafeCall {
        val now = System.currentTimeMillis()
        val snapshot = storiesCollection
            .whereLessThan("expiresAt", now)
            .get()
            .await()

        snapshot.documents.forEach { doc ->
            doc.reference.delete()
        }
    }

    // Firestore document model
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
    ) {
        suspend fun toStory(docId: String): Story? {
            // Get author info
            val authorDoc = usersCollection.document(authorId).get().await()
            val author = User(
                userId = authorId,
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

            return Story(
                storyId = docId,
                author = author,
                mediaUrl = mediaUrl,
                mediaType = try { StoryMediaType.valueOf(mediaType) } catch (_: Exception) { StoryMediaType.IMAGE },
                thumbnailUrl = thumbnailUrl,
                duration = null,
                caption = caption,
                viewsCount = viewsCount,
                isViewed = false, // TODO: Check viewers collection
                createdAt = createdAt,
                expiresAt = expiresAt
            )
        }
    }
}
