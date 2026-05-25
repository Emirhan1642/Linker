package com.linker.app.core.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.linker.app.domain.repository.NoteRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Worker that periodically purges expired notes from the database.
 */
@HiltWorker
class ExpiredNotesCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val noteRepository: NoteRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            Log.i("ExpiredNotesWorker", "Starting expired notes cleanup")
            val result = noteRepository.purgeExpiredNotes()
            
            if (result is com.linker.app.core.util.Result.Success) {
                Log.i("ExpiredNotesWorker", "Successfully completed expired notes cleanup")
                Result.success()
            } else {
                Log.w("ExpiredNotesWorker", "Failed to purge expired notes")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e("ExpiredNotesWorker", "Exception during expired notes cleanup: ${e.message}", e)
            Result.retry()
        }
    }
}
