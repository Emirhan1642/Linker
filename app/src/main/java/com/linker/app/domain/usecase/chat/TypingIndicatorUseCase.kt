package com.linker.app.domain.usecase.chat

import com.linker.app.core.util.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

interface TypingIndicatorRepository {
    suspend fun startTyping(chatId: String, userId: String): Result<Unit>
    suspend fun stopTyping(chatId: String, userId: String): Result<Unit>
    fun observeTypingUsers(chatId: String, excludeUserId: String): Flow<List<String>>
}

@Singleton
class TypingIndicatorUseCase @Inject constructor(
    private val typingIndicatorRepository: TypingIndicatorRepository,
    private val currentUserProvider: com.linker.app.domain.usecase.user.CurrentUserProvider
) {
    private var typingJob: Job? = null
    private val typingJobLock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun startTyping(chatId: String) {
        if (chatId.isBlank()) return
        
        scope.launch {
            typingJobLock.withLock {
                typingJob?.cancel()
                typingJob = launch {
                    try {
                        val userId = currentUserProvider.getCurrentUserId() ?: return@launch
                        when (typingIndicatorRepository.startTyping(chatId, userId)) {
                            is Result.Success -> {
                                delay(5000L)
                                stopTyping(chatId)
                            }
                            is Result.Error -> { /* Handle error */ }
                            is Result.Loading -> { /* Ignored */ }
                        }
                    } catch (e: Exception) {
                        /* Ignore cancellation, log other exceptions */
                    }
                }
            }
        }
    }

    fun stopTyping(chatId: String) {
        if (chatId.isBlank()) return
        
        scope.launch {
            typingJobLock.withLock {
                typingJob?.cancel()
                val userId = currentUserProvider.getCurrentUserId() ?: return@launch
                typingIndicatorRepository.stopTyping(chatId, userId)
            }
        }
    }

    fun observeTypingUsers(chatId: String): Flow<List<String>> {
        if (chatId.isBlank()) return flowOf(emptyList())
        val currentUserId = currentUserProvider.getCurrentUserId() ?: return flowOf(emptyList())
        return typingIndicatorRepository.observeTypingUsers(chatId, currentUserId).catch { emit(emptyList()) }
    }

    fun cleanup() {
        scope.cancel()
    }
}
