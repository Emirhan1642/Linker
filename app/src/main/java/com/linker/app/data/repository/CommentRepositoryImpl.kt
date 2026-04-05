package com.linker.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.linker.app.core.util.Result
import com.linker.app.core.util.RetryUtil
import com.linker.app.domain.model.Comment
import com.linker.app.domain.model.Notification
import com.linker.app.domain.model.NotificationType
import com.linker.app.domain.model.User
import com.linker.app.domain.repository.CommentRepository
import com.linker.app.domain.repository.NotificationRepository
import com.linker.app.data.local.entity.NotificationEntity
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Comment Repository Implementation
 *
 * Manages comments and nested replies
 */
@Singleton
class CommentRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : CommentRepository {

    private val commentsCollection = firestore.collection("comments")
    private val usersCollection = firestore.collection("users")
    private val linksCollection = firestore.collection("links")

    override fun observeComments(linkId: String): Flow<List<Comment>> = callbackFlow {
        val listener = commentsCollection
            .whereEqualTo("linkId", linkId)
            .whereEqualTo("parentCommentId", null) // Top-level comments only
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val comments = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(CommentDocument::class.java)?.toComment(doc.id)
                } ?: emptyList()

                trySend(comments)
            }

        awaitClose { listener.remove() }
    }

    override suspend fun getComments(
        linkId: String,
        limit: Int,
        offset: Int
    ): Result<List<Comment>> = RetryUtil.retrySafeCall {
        val snapshot = commentsCollection
            .whereEqualTo("linkId", linkId)
            .whereEqualTo("parentCommentId", null)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .get()
            .await()

        snapshot.documents.drop(offset).mapNotNull { doc ->
            doc.toObject(CommentDocument::class.java)?.toComment(doc.id)
        }
    }

    override suspend fun getReplies(parentCommentId: String): Result<List<Comment>> = RetryUtil.retrySafeCall {
        val snapshot = commentsCollection
            .whereEqualTo("parentCommentId", parentCommentId)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .get()
            .await()

        snapshot.documents.mapNotNull { doc ->
            doc.toObject(CommentDocument::class.java)?.toComment(doc.id)
        }
    }

    override suspend fun addComment(
        linkId: String,
        content: String,
        gifUrl: String?,
        parentCommentId: String?
    ): Result<Comment> = RetryUtil.retrySafeCall {
        val currentUser = auth.currentUser ?: throw IllegalStateException("Not authenticated")
        val commentId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val commentData = hashMapOf(
            "commentId" to commentId,
            "linkId" to linkId,
            "authorId" to currentUser.uid,
            "content" to content,
            "gifUrl" to gifUrl,
            "parentCommentId" to parentCommentId,
            "likesCount" to 0,
            "repliesCount" to 0,
            "isPinned" to false,
            "isEdited" to false,
            "createdAt" to now,
            "updatedAt" to now
        )

        commentsCollection.document(commentId).set(commentData).await()

        // If this is a reply, increment parent's repliesCount
        if (parentCommentId != null) {
            commentsCollection.document(parentCommentId).update(
                "repliesCount", com.google.firebase.firestore.FieldValue.increment(1)
            ).await()
        }

        // Increment link's comments count
        linksCollection.document(linkId).update(
            "commentsCount", com.google.firebase.firestore.FieldValue.increment(1)
        ).await()

        // Get author info
        val authorDoc = usersCollection.document(currentUser.uid).get().await()
        val author = User(
            userId = currentUser.uid,
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

        Comment(
            commentId = commentId,
            linkId = linkId,
            author = author,
            content = content,
            gifUrl = gifUrl,
            parentCommentId = parentCommentId,
            likesCount = 0,
            repliesCount = 0,
            isLiked = false,
            isPinned = false,
            isEdited = false,
            createdAt = now,
            updatedAt = now
        )
    }

    override suspend fun toggleLike(commentId: String): Result<Boolean> = RetryUtil.retrySafeCall {
        val currentUserId = auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")

        val likesRef = commentsCollection
            .document(commentId)
            .collection("likes")
            .document(currentUserId)

        val likeDoc = likesRef.get().await()
        val isLiked = likeDoc.exists()

        if (isLiked) {
            // Unlike
            likesRef.delete().await()
            commentsCollection.document(commentId).update(
                "likesCount", com.google.firebase.firestore.FieldValue.increment(-1)
            ).await()
        } else {
            // Like
            likesRef.set(mapOf("likedAt" to System.currentTimeMillis())).await()
            commentsCollection.document(commentId).update(
                "likesCount", com.google.firebase.firestore.FieldValue.increment(1)
            ).await()
        }

        !isLiked
    }

    override suspend fun deleteComment(commentId: String): Result<Unit> = RetryUtil.retrySafeCall {
        val commentDoc = commentsCollection.document(commentId).get().await()
        val linkId = commentDoc.getString("linkId")
        val parentCommentId = commentDoc.getString("parentCommentId")

        // Delete the comment
        commentsCollection.document(commentId).delete().await()

        // If this was a reply, decrement parent's repliesCount
        if (parentCommentId != null) {
            commentsCollection.document(parentCommentId).update(
                "repliesCount", com.google.firebase.firestore.FieldValue.increment(-1)
            ).await()
        }

        // Decrement link's comments count
        if (linkId != null) {
            linksCollection.document(linkId).update(
                "commentsCount", com.google.firebase.firestore.FieldValue.increment(-1)
            ).await()
        }
    }

    // Firestore document model
    private data class CommentDocument(
        val commentId: String = "",
        val linkId: String = "",
        val authorId: String = "",
        val content: String = "",
        val gifUrl: String? = null,
        val parentCommentId: String? = null,
        val likesCount: Int = 0,
        val repliesCount: Int = 0,
        val isPinned: Boolean = false,
        val isEdited: Boolean = false,
        val createdAt: Long = 0,
        val updatedAt: Long = 0
    ) {
        suspend fun toComment(docId: String): Comment? {
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

            // Check if current user liked this comment
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
            val isLiked = if (currentUserId != null) {
                val likeDoc = commentsCollection
                    .document(docId)
                    .collection("likes")
                    .document(currentUserId)
                    .get()
                    .await()
                likeDoc.exists()
            } else false

            return Comment(
                commentId = docId,
                linkId = linkId,
                author = author,
                content = content,
                gifUrl = gifUrl,
                parentCommentId = parentCommentId,
                likesCount = likesCount,
                repliesCount = repliesCount,
                isLiked = isLiked,
                isPinned = isPinned,
                isEdited = isEdited,
                createdAt = createdAt,
                updatedAt = updatedAt
            )
        }
    }
}
