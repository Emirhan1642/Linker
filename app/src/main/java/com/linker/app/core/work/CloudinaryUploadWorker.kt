package com.linker.app.core.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.android.qualifiers.ApplicationContext

class CloudinaryUploadWorker(
    @ApplicationContext context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        // TODO: Implement actual Cloudinary upload logic
        return Result.success()
    }
}
