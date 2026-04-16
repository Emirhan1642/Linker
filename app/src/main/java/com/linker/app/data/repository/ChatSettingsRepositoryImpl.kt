package com.linker.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.linker.app.core.util.Result
import com.linker.app.core.util.safeCall
import com.linker.app.domain.repository.ChatSettingsRepository
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of ChatSettingsRepository
 * Handles chat settings and group management
 */
@Singleton
class ChatSettingsRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ChatSettingsRepository {

    private val chatsCollection = firestore.collection("chats")

    private val currentUserId: String
        get() = auth.currentUser?.uid ?: ""

    override suspend fun updateChatSettings(
        chatId: String,
        chatName: String?,
        chatImageUrl: String?,
        permissions: Map<String, Any>?
    ): Result<Unit> = safeCall {
        val updates = mutableMapOf<String, Any>("updatedAt" to System.currentTimeMillis())
        chatName?.let { updates["chatName"] = it }
        chatImageUrl?.let { updates["chatImageUrl"] = it }
        permissions?.let { updates["groupPermissions"] = it }
        chatsCollection.document(chatId).update(updates).await()
    }

    override suspend fun archiveChat(chatId: String, archive: Boolean): Result<Unit> = safeCall {
        val field = if (archive) {
            mapOf("archivedBy" to FieldValue.arrayUnion(currentUserId))
        } else {
            mapOf("archivedBy" to FieldValue.arrayRemove(currentUserId))
        }
        chatsCollection.document(chatId).update(field).await()
    }

    override suspend fun pinChat(chatId: String, pin: Boolean): Result<Unit> = safeCall {
        val field = if (pin) {
            mapOf("pinnedBy" to FieldValue.arrayUnion(currentUserId))
        } else {
            mapOf("pinnedBy" to FieldValue.arrayRemove(currentUserId))
        }
        chatsCollection.document(chatId).update(field).await()
    }

    override suspend fun muteChat(chatId: String, mute: Boolean): Result<Unit> = safeCall {
        val field = if (mute) {
            mapOf("mutedBy" to FieldValue.arrayUnion(currentUserId))
        } else {
            mapOf("mutedBy" to FieldValue.arrayRemove(currentUserId))
        }
        chatsCollection.document(chatId).update(field).await()
    }

    override suspend fun blockChat(chatId: String, block: Boolean): Result<Unit> = safeCall {
        val field = if (block) {
            mapOf("blockedBy" to FieldValue.arrayUnion(currentUserId))
        } else {
            mapOf("blockedBy" to FieldValue.arrayRemove(currentUserId))
        }
        chatsCollection.document(chatId).update(field).await()
    }

    override suspend fun addParticipants(chatId: String, userIds: List<String>): Result<Unit> = safeCall {
        chatsCollection.document(chatId).update(
            mapOf(
                "participantIds" to FieldValue.arrayUnion(*userIds.toTypedArray()),
                "updatedAt" to System.currentTimeMillis()
            )
        ).await()
    }

    override suspend fun removeParticipant(chatId: String, userId: String): Result<Unit> = safeCall {
        chatsCollection.document(chatId).update(
            mapOf(
                "participantIds" to FieldValue.arrayRemove(userId),
                "updatedAt" to System.currentTimeMillis()
            )
        ).await()
    }

    override suspend fun leaveGroupChat(chatId: String): Result<Unit> = safeCall {
        removeParticipant(chatId, currentUserId)
    }

    override suspend fun transferGroupOwnership(chatId: String, newOwnerId: String): Result<Unit> = safeCall {
        chatsCollection.document(chatId).update(
            mapOf(
                "createdBy" to newOwnerId,
                "adminIds" to FieldValue.arrayUnion(newOwnerId),
                "updatedAt" to System.currentTimeMillis()
            )
        ).await()
    }

    override suspend fun promoteToAdmin(chatId: String, userId: String): Result<Unit> = safeCall {
        chatsCollection.document(chatId).update(
            mapOf(
                "adminIds" to FieldValue.arrayUnion(userId),
                "updatedAt" to System.currentTimeMillis()
            )
        ).await()
    }
}
