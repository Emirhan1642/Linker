package com.linker.app.domain.usecase.chat

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Typing indicator management
 * Shows when other users are typing in a chat
 */
@Singleton
class TypingIndicatorUseCase @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val currentUserProvider: com.linker.app.domain.usecase.user.CurrentUserProvider
) {
    private var typingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        private const val TYPING_TIMEOUT = 5000L // 5 seconds
        private const val COLLECTION_TYPING = "typing"
    }

    /**
     * Start typing indicator for current user
     */
    fun startTyping(chatId: String) {
        typingJob?.cancel()
        typingJob = scope.launch {
            val userId = currentUserProvider.getCurrentUserId() ?: return@launch

            val typingRef = firestore
                .collection(COLLECTION_TYPING)
                .document(chatId)
                .collection("users")
                .document(userId)

            typingRef.set(mapOf(
                "startedAt" to System.currentTimeMillis()
            ))

            // Auto-clear after timeout
            delay(TYPING_TIMEOUT)
            stopTyping(chatId)
        }
    }

    /**
     * Stop typing indicator for current user
     */
    fun stopTyping(chatId: String) {
        typingJob?.cancel()
        val userId = currentUserProvider.getCurrentUserId() ?: return

        scope.launch {
            firestore
                .collection(COLLECTION_TYPING)
                .document(chatId)
                .collection("users")
                .document(userId)
                .delete()
        }
    }

    /**
     * Observe typing users in a chat (excluding current user)
     */
    fun observeTypingUsers(chatId: String): Flow<List<String>> = callbackFlow {
        val listener = firestore
            .collection(COLLECTION_TYPING)
            .document(chatId)
            .collection("users")
            .addSnapshotListener { snapshot, _ ->
                val now = System.currentTimeMillis()
                val currentUserId = currentUserProvider.getCurrentUserId()

                val typingUsers = snapshot?.documents?.mapNotNull { doc ->
                    val startedAt = (doc.get("startedAt") as? Number)?.toLong() ?: 0L

                    // Only show if typing within last 5 seconds
                    if (now - startedAt < TYPING_TIMEOUT && doc.id != currentUserId) {
                        doc.id
                    } else null
                } ?: emptyList()

                trySend(typingUsers)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        scope.cancel()
    }
}
