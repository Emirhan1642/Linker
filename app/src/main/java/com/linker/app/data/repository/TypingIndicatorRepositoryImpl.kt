package com.linker.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.linker.app.core.util.Result
import com.linker.app.domain.usecase.chat.TypingIndicatorRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TypingIndicatorRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : TypingIndicatorRepository {

    companion object {
        private const val TYPING_TIMEOUT_MS = 6000L
    }

    override suspend fun startTyping(chatId: String, userId: String): Result<Unit> {
        return try {
            firestore.collection("chats")
                .document(chatId)
                .collection("typing")
                .document(userId)
                .set(
                    mapOf(
                        "userId" to userId,
                        "timestamp" to System.currentTimeMillis()
                    )
                )
                .await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to set typing indicator")
        }
    }

    override suspend fun stopTyping(chatId: String, userId: String): Result<Unit> {
        return try {
            firestore.collection("chats")
                .document(chatId)
                .collection("typing")
                .document(userId)
                .delete()
                .await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to clear typing indicator")
        }
    }

    override fun observeTypingUsers(chatId: String, excludeUserId: String): Flow<List<String>> = callbackFlow {
        val listener = firestore.collection("chats")
            .document(chatId)
            .collection("typing")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val now = System.currentTimeMillis()
                val typingUsers = snapshot?.documents
                    ?.mapNotNull { doc ->
                        val uid = doc.getString("userId") ?: doc.id
                        val ts = doc.getLong("timestamp") ?: 0L
                        if (uid != excludeUserId && (now - ts) < TYPING_TIMEOUT_MS) {
                            uid
                        } else {
                            null
                        }
                    } ?: emptyList()

                trySend(typingUsers)
            }

        awaitClose {
            listener.remove()
        }
    }
}
