package com.linker.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.linker.app.core.util.Result
import com.linker.app.core.util.safeCall
import com.linker.app.domain.repository.ReadReceiptRepository
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of ReadReceiptRepository
 * Handles read receipts and delivery receipts
 *
 * Firestore path: chats/{chatId}/messages/{messageId}
 */
@Singleton
class ReadReceiptRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ReadReceiptRepository {

    private val currentUserId: String
        get() = auth.currentUser?.uid ?: ""

    private fun messagesRef(chatId: String) =
        firestore.collection("chats").document(chatId).collection("messages")

    override suspend fun markAsRead(messageId: String, chatId: String): Result<Unit> = safeCall {
        if (messageId.isBlank() || chatId.isBlank()) throw IllegalArgumentException("messageId and chatId cannot be blank")
        if (currentUserId.isBlank()) throw Exception("Not signed in")
        
        val now = System.currentTimeMillis()
        val docRef = messagesRef(chatId).document(messageId)
        
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            if (!snapshot.exists()) {
                throw Exception("Message not found")
            }
            
            @Suppress("UNCHECKED_CAST")
            val readReceipts = snapshot.get("readReceipts") as? Map<String, Any> ?: emptyMap()
            if (!readReceipts.containsKey(currentUserId)) {
                transaction.update(
                    docRef,
                    mapOf(
                        "readReceipts.$currentUserId" to now,
                        "readAt" to now,
                        "messageStatus" to "READ"
                    )
                )
            }
            null
        }.await()
    }

    override suspend fun markChatAsReadUpTo(chatId: String, timestamp: Long): Result<Unit> = safeCall {
        if (chatId.isBlank()) throw IllegalArgumentException("chatId cannot be blank")
        if (currentUserId.isBlank()) throw Exception("Not signed in")
        
        val now = System.currentTimeMillis()
        val documents = messagesRef(chatId)
            .whereLessThanOrEqualTo("createdAt", timestamp)
            .get()
            .await()
            .documents

        if (documents.isEmpty()) return@safeCall

        // Split into chunks of 500 for Firestore batch limits
        documents.chunked(500).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { doc ->
                val senderId = doc.getString("senderId")
                if (senderId != currentUserId) {
                    @Suppress("UNCHECKED_CAST")
                    val readReceipts = doc.get("readReceipts") as? Map<String, Any> ?: emptyMap()
                    val isAlreadyRead = readReceipts.containsKey(currentUserId)
                    
                    if (!isAlreadyRead) {
                        batch.update(
                            doc.reference,
                            mapOf(
                                "readReceipts.$currentUserId" to now,
                                "readAt" to now,
                                "messageStatus" to "READ"
                            )
                        )
                    }
                }
            }
            batch.commit().await()
        }
    }

    override suspend fun getReadReceipts(chatId: String, messageId: String): Result<Map<String, Long>> = safeCall {
        val doc = messagesRef(chatId).document(messageId).get().await()
        @Suppress("UNCHECKED_CAST")
        val receipts = doc.get("readReceipts") as? Map<String, Any> ?: emptyMap()
        receipts.mapNotNull { (k, v) ->
            val key = k as? String ?: return@mapNotNull null
            val value = (v as? Number)?.toLong() ?: return@mapNotNull null
            key to value
        }.toMap()
    }

    override suspend fun getDeliveryReceipts(chatId: String, messageId: String): Result<Map<String, Long>> = safeCall {
        val doc = messagesRef(chatId).document(messageId).get().await()
        @Suppress("UNCHECKED_CAST")
        val receipts = doc.get("deliveryReceipts") as? Map<String, Any> ?: emptyMap()
        receipts.mapNotNull { (k, v) ->
            val key = k as? String ?: return@mapNotNull null
            val value = (v as? Number)?.toLong() ?: return@mapNotNull null
            key to value
        }.toMap()
    }
}
