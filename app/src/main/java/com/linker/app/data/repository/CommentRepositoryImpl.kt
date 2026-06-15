package com.linker.app.data.repository

import androidx.annotation.Keep
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.linker.app.core.util.Result
import com.linker.app.core.util.RetryUtil
import com.linker.app.domain.model.Comment
import com.linker.app.domain.model.CommentVersion
import com.linker.app.domain.model.Notification
import com.linker.app.domain.model.NotificationType
import com.linker.app.domain.model.ReportReason
import com.linker.app.domain.model.User
import com.linker.app.domain.repository.CommentRepository
import com.linker.app.domain.repository.NotificationRepository
import com.linker.app.data.local.entity.NotificationEntity
import com.linker.app.domain.model.CommentAuthor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
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

    override fun observeComments(linkId: String): Flow<Result<List<Comment>>> = callbackFlow {
        val listener = commentsCollection
            .whereEqualTo("linkId", linkId)
            .whereEqualTo("parentCommentId", null) // Top-level comments only
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.Success(emptyList()))
                    return@addSnapshotListener
                }

                launch {
                    val dataList = snapshot?.documents?.mapNotNull { doc ->
                        doc.toObject(CommentDocument::class.java)?.let { data ->
                            Pair(doc.id, data)
                        }
                    } ?: emptyList()

                    val comments = mapDocumentsToComments(dataList)
                    trySend(Result.Success(comments))
                }
            }

        awaitClose { listener.remove() }
    }

    override suspend fun getComments(
        linkId: String,
        limit: Int,
        beforeTimestamp: Long?
    ): Result<List<Comment>> = RetryUtil.retrySafeCall {
        var query = commentsCollection
            .whereEqualTo("linkId", linkId)
            .whereEqualTo("parentCommentId", null)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            
        if (beforeTimestamp != null) {
            query = query.whereLessThan("createdAt", beforeTimestamp)
        }
        
        val snapshot = query
            .limit(limit.toLong())
            .get()
            .await()

        val dataList = snapshot.documents.mapNotNull { doc ->
            doc.toObject(CommentDocument::class.java)?.let { Pair(doc.id, it) }
        }
        mapDocumentsToComments(dataList)
    }

    override suspend fun getReplyCount(parentCommentId: String): Result<Int> = RetryUtil.retrySafeCall {
        val doc = commentsCollection.document(parentCommentId).get().await()
        (doc.getLong("repliesCount") ?: 0L).toInt()
    }

    override suspend fun getReplies(parentCommentId: String): Result<List<Comment>> = RetryUtil.retrySafeCall {
        val snapshot = commentsCollection
            .whereEqualTo("parentCommentId", parentCommentId)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .get()
            .await()

        val dataList = snapshot.documents.mapNotNull { doc ->
            doc.toObject(CommentDocument::class.java)?.let { Pair(doc.id, it) }
        }
        mapDocumentsToComments(dataList)
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
        
        // Calculate nesting level
        val nestingLevel = if (parentCommentId != null) {
            val parentDoc = commentsCollection.document(parentCommentId).get().await()
            val parentNestingLevel = (parentDoc.getLong("nestingLevel") ?: 0L).toInt()
            parentNestingLevel + 1
        } else {
            0
        }

        val commentData = hashMapOf(
            "commentId" to commentId,
            "linkId" to linkId,
            "authorId" to currentUser.uid,
            "content" to content,
            "gifUrl" to gifUrl,
            "parentCommentId" to parentCommentId,
            "nestingLevel" to nestingLevel,
            "likesCount" to 0,
            "repliesCount" to 0,
            "isPinned" to false,
            "isEdited" to false,
            "createdAt" to now,
            "updatedAt" to now
        )

        val batch = firestore.batch()
        batch.set(commentsCollection.document(commentId), commentData)

        // If this is a reply, increment parent's repliesCount
        if (parentCommentId != null) {
            batch.update(
                commentsCollection.document(parentCommentId),
                "repliesCount", com.google.firebase.firestore.FieldValue.increment(1)
            )
        }

        // Increment link's comments count
        batch.update(
            linksCollection.document(linkId),
            "commentsCount", com.google.firebase.firestore.FieldValue.increment(1)
        )
        batch.commit().await()

        // Get author info
        val authorDoc = usersCollection.document(currentUser.uid).get().await()
        val author = CommentAuthor(
            userId = currentUser.uid,
            username = authorDoc.getString("username") ?: "",
            displayName = authorDoc.getString("displayName") ?: "",
            profileImageUrl = authorDoc.getString("profileImageUrl"),
            isVerified = authorDoc.getBoolean("isVerified") ?: false
        )

        Comment(
            commentId = commentId,
            linkId = linkId,
            author = author,
            content = content,
            gifUrl = gifUrl,
            parentCommentId = parentCommentId,
            nestingLevel = nestingLevel,
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

        val batch = firestore.batch()
        if (isLiked) {
            // Unlike
            batch.delete(likesRef)
            batch.update(
                commentsCollection.document(commentId),
                "likesCount", com.google.firebase.firestore.FieldValue.increment(-1)
            )
        } else {
            // Like
            batch.set(likesRef, mapOf("likedAt" to System.currentTimeMillis()))
            batch.update(
                commentsCollection.document(commentId),
                "likesCount", com.google.firebase.firestore.FieldValue.increment(1)
            )
        }
        batch.commit().await()

        !isLiked
    }

    override suspend fun deleteComment(commentId: String): Result<Unit> = RetryUtil.retrySafeCall {
        val currentUserId = auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")
        val commentDoc = commentsCollection.document(commentId).get().await()
        val linkId = commentDoc.getString("linkId")
        val parentCommentId = commentDoc.getString("parentCommentId")
        val repliesCount = commentDoc.getLong("repliesCount") ?: 0L

        val batch = firestore.batch()

        if (repliesCount > 0) {
            // Soft delete: keep the document but blank the content
            batch.update(commentsCollection.document(commentId), mapOf(
                "content" to "[Silindi]",
                "isDeleted" to true
            ))
        } else {
            batch.delete(commentsCollection.document(commentId))
        }

        // If this was a reply, decrement parent's repliesCount
        if (parentCommentId != null) {
            batch.update(
                commentsCollection.document(parentCommentId),
                "repliesCount", com.google.firebase.firestore.FieldValue.increment(-1)
            )
        }

        // Decrement link's comments count
        if (linkId != null) {
            batch.update(
                linksCollection.document(linkId),
                "commentsCount", com.google.firebase.firestore.FieldValue.increment(-1)
            )
        }
        batch.commit().await()
    }

    override suspend fun editComment(commentId: String, newContent: String): Result<Unit> = RetryUtil.retrySafeCall {
        val currentUserId = auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")
        val commentRef = commentsCollection.document(commentId)
        val commentDoc = commentRef.get().await()

        val authorId = commentDoc.getString("authorId") ?: ""
        if (authorId != currentUserId) throw SecurityException("Only the author can edit this comment")

        val currentEditCount = (commentDoc.getLong("editCount") ?: 0L).toInt()
        if (currentEditCount >= Comment.MAX_EDITS) {
            throw IllegalStateException("Bu yorum en fazla ${Comment.MAX_EDITS} kez düzenlenebilir")
        }

        val oldContent = commentDoc.getString("content") ?: ""
        val now = System.currentTimeMillis()
        val newVersion = currentEditCount + 1

        // Save old version to edit history sub-collection
        val historyRef = commentRef.collection("editHistory").document("v$newVersion")

        val batch = firestore.batch()
        batch.set(historyRef, mapOf(
            "content" to oldContent,
            "editedAt" to now,
            "version" to newVersion
        ))
        batch.update(commentRef, mapOf(
            "content" to newContent,
            "isEdited" to true,
            "editCount" to newVersion,
            "updatedAt" to now
        ))
        batch.commit().await()
    }

    override suspend fun getCommentEditHistory(commentId: String): Result<List<CommentVersion>> = RetryUtil.retrySafeCall {
        val historySnapshot = commentsCollection
            .document(commentId)
            .collection("editHistory")
            .orderBy("version", Query.Direction.DESCENDING)
            .get()
            .await()

        historySnapshot.documents.mapNotNull { doc ->
            val content = doc.getString("content") ?: return@mapNotNull null
            val editedAt = doc.getLong("editedAt") ?: return@mapNotNull null
            val version = (doc.getLong("version") ?: return@mapNotNull null).toInt()
            CommentVersion(content = content, editedAt = editedAt, version = version)
        }
    }

    override suspend fun reportComment(commentId: String, reason: ReportReason): Result<Unit> = RetryUtil.retrySafeCall {
        val currentUserId = auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")
        val reportId = "${currentUserId}_${commentId}"
        firestore.collection("reports").document(reportId).set(
            mapOf(
                "reporterId" to currentUserId,
                "contentId" to commentId,
                "contentType" to "comment",
                "reason" to reason.name,
                "createdAt" to System.currentTimeMillis()
            )
        ).await()
    }

    /**
     * Firestore [toObject] için düz veri taşıyıcı; alan adları koleksiyon şemasıyla aynı kalmalı (@Keep).
     * Eşleme suspend işlemleri içerdiği için nested class içinde değil, repository metodunda yapılır.
     */
    @Keep
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
        val isDeleted: Boolean = false,
        val editCount: Int = 0,
        val createdAt: Long = 0,
        val updatedAt: Long = 0
    )

    private suspend fun mapDocumentsToComments(dataList: List<Pair<String, CommentDocument>>): List<Comment> {
        if (dataList.isEmpty()) return emptyList()

        // Batch fetch users
        val authorIds = dataList.map { it.second.authorId }.distinct()
        val usersMap = mutableMapOf<String, CommentAuthor>()

        authorIds.chunked(10).forEach { chunk ->
            val userDocs = usersCollection.whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk).get().await()
            userDocs.documents.forEach { authorDoc ->
                val author = CommentAuthor(
                    userId = authorDoc.id,
                    username = authorDoc.getString("username") ?: "",
                    displayName = authorDoc.getString("displayName") ?: "",
                    profileImageUrl = authorDoc.getString("profileImageUrl"),
                    isVerified = authorDoc.getBoolean("isVerified") ?: false
                )
                usersMap[authorDoc.id] = author
            }
        }

        val currentUserId = auth.currentUser?.uid

        // We use kotlinx.coroutines.async to fetch isLiked in parallel for all comments
        return kotlinx.coroutines.coroutineScope {
            dataList.map { (docId, data) ->
                async {
                    val author = usersMap[data.authorId] ?: return@async null

                    val isLiked = if (currentUserId != null) {
                        try {
                            val likeDoc = commentsCollection
                                .document(docId)
                                .collection("likes")
                                .document(currentUserId)
                                .get()
                                .await()
                            likeDoc.exists()
                        } catch (e: Exception) {
                            false
                        }
                    } else {
                        false
                    }

                    Comment(
                        commentId = docId,
                        linkId = data.linkId,
                        author = author,
                        content = data.content,
                        gifUrl = data.gifUrl,
                        parentCommentId = data.parentCommentId,
                        likesCount = data.likesCount,
                        repliesCount = data.repliesCount,
                        isLiked = isLiked,
                        isPinned = data.isPinned,
                        isEdited = data.isEdited,
                        editCount = data.editCount,
                        createdAt = data.createdAt,
                        updatedAt = data.updatedAt
                    )
                }
            }.mapNotNull { it.await() }
        }
    }
}
