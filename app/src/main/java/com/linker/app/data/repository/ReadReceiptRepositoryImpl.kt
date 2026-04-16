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
 */
@Singleton
class ReadReceiptRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ReadReceiptRepository {

    private val messagesCollection = firestore.collection("messages")

    private val currentUserId: String
        get() = auth.currentUser?.uid ?: ""

    override suspend fun markAsRead(messageId: String, chatId: String): Result<Unit> = safeCall {
        val now = System.currentTimeMillis()
        messagesCollection.document(messageId).update(
            mapOf(
                "readReceipts.$currentUserId" to now,
                "readAt" to now,
                "messageStatus" to "READ"
            )
        ).await()
    }

    override suspend fun markChatAsReadUpTo(chatId: String, timestamp: Long): Result<Unit> = safeCall {
        messagesCollection
            .whereEqualTo("chatId", chatId)
            .whereLessThanOrEqualTo("createdAt", timestamp)
            .get()
            .await()
            .documents
            .forEach { doc ->
                doc.reference.update(
                    mapOf(
                        "readReceipts.$currentUserId" to System.currentTimeMillis()
                    )
                ).await()
            }
    }

    override suspend fun getReadReceipts(messageId: String): Result<Map<String, Long>> = safeCall {
        val doc = messagesCollection.document(messageId).get().await()
        val receipts = doc.get("readReceipts") as? Map<String, Any> ?: emptyMap()
        receipts.mapNotNull { (k, v) ->
            val key = k as? String ?: return@mapNotNull null
            val value = (v as? Number)?.toLong() ?: return@mapNotNull null
            key to value
        }.toMap()
    }

    override suspend fun getDeliveryReceipts(messageId: String): Result<Map<String, Long>> = safeCall {
        val doc = messagesCollection.document(messageId).get().await()
        val receipts = doc.get("deliveryReceipts") as? Map<String, Any> ?: emptyMap()
        receipts.mapNotNull { (k, v) ->
            val key = k as? String ?: return@mapNotNull null
            val value = (v as? Number)?.toLong() ?: return@mapNotNull null
            key to value
        }.toMap()
    }
}
