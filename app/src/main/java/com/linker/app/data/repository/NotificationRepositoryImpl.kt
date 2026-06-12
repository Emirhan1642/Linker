package com.linker.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.linker.app.core.util.Result
import com.linker.app.core.util.safeCall
import com.linker.app.data.local.dao.NotificationDao
import com.linker.app.data.local.dao.UserDao
import com.linker.app.data.local.entity.NotificationEntity
import com.linker.app.data.local.mapper.toDomain
import com.linker.app.domain.model.Notification
import com.linker.app.domain.model.NotificationActor
import com.linker.app.domain.model.NotificationTarget
import com.linker.app.domain.repository.NotificationRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val notificationDao: NotificationDao,
    private val userDao: UserDao
) : NotificationRepository {

    private val currentUserId: String
        get() = auth.currentUser?.uid ?: ""

    /** Returns the notifications subcollection for the current user */
    private fun notificationsRef() =
        firestore.collection("users").document(currentUserId).collection("notifications")

    override fun observeNotifications(): Flow<Result<List<Notification>>> = callbackFlow {
        if (currentUserId.isBlank()) {
            trySend(Result.Success(emptyList()))
            awaitClose { }
            return@callbackFlow
        }

        val listener = notificationsRef()
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.Success(emptyList()))
                    return@addSnapshotListener
                }
                val notifications = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { mapToNotificationSync(doc.id, it) }
                } ?: emptyList()

                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    val entities = notifications.map { notification ->
                        // Extract targetEntityId and targetEntityType from sealed class
                        val (targetEntityId, targetEntityType) = when (val target = notification.target) {
                            is NotificationTarget.LinkTarget -> target.linkId to "LINK"
                            is NotificationTarget.CommentTarget -> target.commentId to "COMMENT"
                            is NotificationTarget.StoryTarget -> target.storyId to "STORY"
                            is NotificationTarget.UserTarget -> target.userId to "USER"
                            is NotificationTarget.MessageTarget -> target.messageId to "MESSAGE"
                            NotificationTarget.NoTarget -> null to null
                        }
                        
                        NotificationEntity(
                            notificationId = notification.notificationId,
                            notificationType = when (notification.notificationType) {
                                com.linker.app.domain.model.NotificationType.LIKE -> com.linker.app.data.local.entity.NotificationType.LIKE
                                com.linker.app.domain.model.NotificationType.COMMENT -> com.linker.app.data.local.entity.NotificationType.COMMENT
                                com.linker.app.domain.model.NotificationType.REPLY -> com.linker.app.data.local.entity.NotificationType.REPLY
                                com.linker.app.domain.model.NotificationType.FOLLOW -> com.linker.app.data.local.entity.NotificationType.FOLLOW
                                com.linker.app.domain.model.NotificationType.MENTION -> com.linker.app.data.local.entity.NotificationType.MENTION
                                com.linker.app.domain.model.NotificationType.RELINK -> com.linker.app.data.local.entity.NotificationType.RELINK
                                com.linker.app.domain.model.NotificationType.MESSAGE -> com.linker.app.data.local.entity.NotificationType.MESSAGE
                                com.linker.app.domain.model.NotificationType.STORY_VIEW -> com.linker.app.data.local.entity.NotificationType.STORY_VIEW
                                com.linker.app.domain.model.NotificationType.LIVE -> com.linker.app.data.local.entity.NotificationType.LIVE
                            },
                            actorId = notification.actor.userId,
                            targetEntityId = targetEntityId,
                            targetEntityType = targetEntityType,
                            title = notification.title,
                            message = notification.message,
                            imageUrl = notification.imageUrl,
                            actionUrl = notification.actionUrl,
                            isRead = notification.isRead,
                            createdAt = notification.createdAt
                        )
                    }
                    entities.forEach { notificationDao.insertNotification(it) }
                }

                trySend(Result.Success(notifications))
            }
        awaitClose { listener.remove() }
    }

    override fun observeUnreadCount(): Flow<Result<Int>> = callbackFlow {
        if (currentUserId.isBlank()) {
            trySend(Result.Success(0))
            awaitClose { }
            return@callbackFlow
        }

        val listener = notificationsRef()
            .whereEqualTo("isRead", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.Success(0))
                    return@addSnapshotListener
                }
                trySend(Result.Success(snapshot?.size() ?: 0))
            }
        awaitClose { listener.remove() }
    }

    override suspend fun markAsRead(notificationId: String): Result<Unit> = safeCall {
        notificationsRef().document(notificationId)
            .update("isRead", true).await()
        notificationDao.markAsRead(notificationId)
    }

    override suspend fun markAllAsRead(): Result<Unit> = safeCall {
        val query = notificationsRef()
            .whereEqualTo("isRead", false)
            .get()
            .await()

        query.documents.chunked(500).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { doc ->
                batch.update(doc.reference, "isRead", true)
            }
            batch.commit().await()
        }
        notificationDao.markAllAsRead()
    }

    override suspend fun clearAll(): Result<Unit> = safeCall {
        val query = notificationsRef()
            .get()
            .await()

        query.documents.chunked(500).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { doc ->
                batch.delete(doc.reference)
            }
            batch.commit().await()
        }
        notificationDao.deleteAllNotifications()
    }

    override suspend fun insertNotification(notification: Notification): Result<Unit> = safeCall {
        val (targetEntityId, targetEntityType) = when (val target = notification.target) {
            is NotificationTarget.LinkTarget -> target.linkId to "LINK"
            is NotificationTarget.CommentTarget -> target.commentId to "COMMENT"
            is NotificationTarget.StoryTarget -> target.storyId to "STORY"
            is NotificationTarget.UserTarget -> target.userId to "USER"
            is NotificationTarget.MessageTarget -> target.messageId to "MESSAGE"
            NotificationTarget.NoTarget -> null to null
        }
        
        val localNotification = com.linker.app.data.local.entity.NotificationEntity(
            notificationId = notification.notificationId,
            notificationType = when (notification.notificationType) {
                com.linker.app.domain.model.NotificationType.LIKE -> com.linker.app.data.local.entity.NotificationType.LIKE
                com.linker.app.domain.model.NotificationType.COMMENT -> com.linker.app.data.local.entity.NotificationType.COMMENT
                com.linker.app.domain.model.NotificationType.REPLY -> com.linker.app.data.local.entity.NotificationType.REPLY
                com.linker.app.domain.model.NotificationType.FOLLOW -> com.linker.app.data.local.entity.NotificationType.FOLLOW
                com.linker.app.domain.model.NotificationType.MENTION -> com.linker.app.data.local.entity.NotificationType.MENTION
                com.linker.app.domain.model.NotificationType.RELINK -> com.linker.app.data.local.entity.NotificationType.RELINK
                com.linker.app.domain.model.NotificationType.MESSAGE -> com.linker.app.data.local.entity.NotificationType.MESSAGE
                com.linker.app.domain.model.NotificationType.STORY_VIEW -> com.linker.app.data.local.entity.NotificationType.STORY_VIEW
                com.linker.app.domain.model.NotificationType.LIVE -> com.linker.app.data.local.entity.NotificationType.LIVE
            },
            actorId = notification.actor.userId,
            targetEntityId = targetEntityId,
            targetEntityType = targetEntityType,
            title = notification.title,
            message = notification.message,
            imageUrl = notification.imageUrl,
            actionUrl = notification.actionUrl,
            isRead = notification.isRead,
            createdAt = notification.createdAt
        )
        notificationDao.insertNotification(localNotification)
    }

    private fun mapToNotificationSync(notificationId: String, data: Map<String, Any?>): Notification {
        val actorId = data["senderId"] as? String ?: data["actorId"] as? String ?: ""
        val actor = NotificationActor(
            userId = actorId,
            username = "",
            displayName = "",
            profileImageUrl = null,
            isVerified = false
        )

        val typeStr = data["type"] as? String ?: "MESSAGE"
        val notificationType = try {
            com.linker.app.domain.model.NotificationType.valueOf(typeStr)
        } catch (_: Exception) {
            com.linker.app.domain.model.NotificationType.MESSAGE
        }
        
        // Convert legacy string-based target to sealed class
        val targetEntityId = data["messageId"] as? String ?: data["targetEntityId"] as? String
        val targetEntityType = data["targetEntityType"] as? String
        val target = NotificationTarget.fromLegacy(
            entityType = targetEntityType,
            entityId = targetEntityId
        )

        return Notification(
            notificationId = notificationId,
            notificationType = notificationType,
            actor = actor,
            target = target,
            title = data["title"] as? String ?: "",
            message = data["body"] as? String ?: data["message"] as? String ?: "",
            imageUrl = null,
            actionUrl = (data["chatId"] as? String)?.let { "/chat/$it" },
            isRead = data["isRead"] as? Boolean ?: false,
            createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )
    }

    private suspend fun mapToNotification(notificationId: String, data: Map<String, Any?>): Notification {
        val actorId = data["senderId"] as? String ?: data["actorId"] as? String ?: ""
        val actorEntity = userDao.getUserById(actorId)
        val actor = actorEntity?.toDomain()?.let { NotificationActor.from(it) }
            ?: NotificationActor(
                userId = actorId,
                username = "",
                displayName = "",
                profileImageUrl = null,
                isVerified = false
            )

        val typeStr = data["type"] as? String ?: "MESSAGE"
        val notificationType = try {
            com.linker.app.domain.model.NotificationType.valueOf(typeStr)
        } catch (_: Exception) {
            com.linker.app.domain.model.NotificationType.MESSAGE
        }
        
        // Convert legacy string-based target to sealed class
        val targetEntityId = data["messageId"] as? String ?: data["targetEntityId"] as? String
        val targetEntityType = data["targetEntityType"] as? String
        val target = NotificationTarget.fromLegacy(
            entityType = targetEntityType,
            entityId = targetEntityId
        )

        return Notification(
            notificationId = notificationId,
            notificationType = notificationType,
            actor = actor,
            target = target,
            title = data["title"] as? String ?: "",
            message = data["body"] as? String ?: data["message"] as? String ?: "",
            imageUrl = null,
            actionUrl = (data["chatId"] as? String)?.let { "/chat/$it" },
            isRead = data["isRead"] as? Boolean ?: false,
            createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )
    }
}
