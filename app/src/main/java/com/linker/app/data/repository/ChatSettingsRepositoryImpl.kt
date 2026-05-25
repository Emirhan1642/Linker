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

    companion object {
        private const val MAX_GROUP_PARTICIPANTS = 256
    }

    override suspend fun updateChatSettings(
        chatId: String,
        chatName: String?,
        chatImageUrl: String?,
        permissions: Map<String, Any>?
    ): Result<Unit> = safeCall {
        val chatDoc = chatsCollection.document(chatId).get().await()
        if (!chatDoc.exists()) throw IllegalArgumentException("Chat not found")
        
        val chatData = chatDoc.data ?: throw IllegalStateException("Chat data is null")
        val chatType = chatData["chatType"] as? String
        val participantIds = (chatData["participantIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        val adminIds = (chatData["adminIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        val createdBy = chatData["createdBy"] as? String
        
        if (!participantIds.contains(currentUserId)) {
            throw SecurityException("User is not a participant of this chat")
        }
        
        if (chatType == "GROUP") {
            val isAdmin = adminIds.contains(currentUserId) || createdBy == currentUserId
            if (!isAdmin) throw SecurityException("Only admins can update group settings")
        }
        
        if (chatType == "PRIVATE" && (chatName != null || permissions != null)) {
            throw SecurityException("Cannot set name or permissions for private chats")
        }
        
        chatName?.let {
            if (it.isBlank() || it.length > 100) throw IllegalArgumentException("Chat name must be 1-100 characters")
        }

        val updates = mutableMapOf<String, Any>("updatedAt" to System.currentTimeMillis())
        chatName?.let { updates["chatName"] = it.trim() }
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
        val chatDoc = chatsCollection.document(chatId).get().await()
        if (!chatDoc.exists()) throw IllegalArgumentException("Chat not found")
        
        val chatData = chatDoc.data ?: throw IllegalStateException("Chat data is null")
        val chatType = chatData["chatType"] as? String
        val participantIds = (chatData["participantIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        val adminIds = (chatData["adminIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        val createdBy = chatData["createdBy"] as? String
        
        if (chatType != "GROUP") throw IllegalArgumentException("Can only add participants to group chats")
        
        val isAdmin = adminIds.contains(currentUserId) || createdBy == currentUserId
        if (!isAdmin) throw SecurityException("Only admins can add participants")
        
        if (userIds.isEmpty()) throw IllegalArgumentException("Must provide at least one user ID")
        
        val newUserIds = userIds.distinct().filter { it !in participantIds }
        if (newUserIds.isEmpty()) return@safeCall
        
        if (participantIds.size + newUserIds.size > MAX_GROUP_PARTICIPANTS) {
            throw IllegalStateException("Group participant limit ($MAX_GROUP_PARTICIPANTS) would be exceeded")
        }

        chatsCollection.document(chatId).update(
            mapOf(
                "participantIds" to FieldValue.arrayUnion(*newUserIds.toTypedArray()),
                "updatedAt" to System.currentTimeMillis()
            )
        ).await()
    }

    override suspend fun removeParticipant(chatId: String, userId: String): Result<Unit> = safeCall {
        val chatDoc = chatsCollection.document(chatId).get().await()
        if (!chatDoc.exists()) throw IllegalArgumentException("Chat not found")
        
        val chatData = chatDoc.data ?: throw IllegalStateException("Chat data is null")
        val participantIds = (chatData["participantIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        val adminIds = (chatData["adminIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        val createdBy = chatData["createdBy"] as? String
        
        val isAdmin = adminIds.contains(currentUserId) || createdBy == currentUserId
        if (!isAdmin) throw SecurityException("Only admins can remove participants")
        
        if (userId == createdBy) throw IllegalStateException("Cannot remove the group owner")
        if (userId == currentUserId) throw IllegalArgumentException("Use leaveGroupChat to leave")
        if (!participantIds.contains(userId)) throw IllegalArgumentException("User is not a participant")

        chatsCollection.document(chatId).update(
            mapOf(
                "participantIds" to FieldValue.arrayRemove(userId),
                "adminIds" to FieldValue.arrayRemove(userId),
                "updatedAt" to System.currentTimeMillis()
            )
        ).await()
    }

    override suspend fun leaveGroupChat(chatId: String): Result<Unit> = safeCall {
        chatsCollection.document(chatId).update(
            mapOf(
                "participantIds" to FieldValue.arrayRemove(currentUserId),
                "adminIds" to FieldValue.arrayRemove(currentUserId),
                "updatedAt" to System.currentTimeMillis()
            )
        ).await()
    }

    override suspend fun transferGroupOwnership(chatId: String, newOwnerId: String): Result<Unit> = safeCall {
        val chatDoc = chatsCollection.document(chatId).get().await()
        if (!chatDoc.exists()) throw IllegalArgumentException("Chat not found")
        
        val chatData = chatDoc.data ?: throw IllegalStateException("Chat data is null")
        val chatType = chatData["chatType"] as? String
        val participantIds = (chatData["participantIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        val createdBy = chatData["createdBy"] as? String
        
        if (chatType != "GROUP") throw IllegalArgumentException("Can only transfer ownership of group chats")
        if (createdBy != currentUserId) throw SecurityException("Only the current owner can transfer ownership")
        if (!participantIds.contains(newOwnerId)) throw IllegalArgumentException("New owner must be a participant")
        if (newOwnerId == currentUserId) throw IllegalArgumentException("You are already the owner")

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
