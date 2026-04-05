package com.linker.app.core.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.linker.app.core.util.Result as LinkerResult
import com.linker.app.domain.model.DeliveryMethod
import com.linker.app.domain.repository.ChatRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Ağ varken bekleyen / hatalı kuyruk mesajlarını Firestore'a itmek için periyodik çalışır.
 */
@HiltWorker
class MessageQueueWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val chatRepository: ChatRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): ListenableWorker.Result {
        return try {
            when (chatRepository.retryFailedMessages(DeliveryMethod.ONLINE)) {
                is LinkerResult.Success -> ListenableWorker.Result.success()
                is LinkerResult.Error -> ListenableWorker.Result.retry()
                is LinkerResult.Loading -> ListenableWorker.Result.retry()
            }
        } catch (_: Exception) {
            ListenableWorker.Result.retry()
        }
    }
}
