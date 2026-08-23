package com.linker.app.core.work

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.google.firebase.firestore.FirebaseFirestore
import com.linker.app.core.util.SecureLogger
import com.linker.app.data.local.dao.LinkDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

@HiltWorker
class CloudinaryUploadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val firestore: FirebaseFirestore,
    private val linkDao: LinkDao
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val targetId = inputData.getString("targetId") ?: return@withContext Result.failure()
        val targetType = inputData.getString("targetType") ?: "STORY"
        val singlePath = inputData.getString("mediaLocalPath")
        val multiplePaths = inputData.getStringArray("mediaLocalPaths")?.toList()

        val paths = when {
            !singlePath.isNullOrBlank() -> listOf(singlePath)
            !multiplePaths.isNullOrEmpty() -> multiplePaths
            else -> return@withContext Result.failure()
        }

        SecureLogger.d(TAG, "Starting Cloudinary upload for $targetType $targetId (${paths.size} items)")

        try {
            try {
                MediaManager.get()
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Cloudinary MediaManager not initialized", e)
                return@withContext Result.retry()
            }

            val uploadedUrls = mutableListOf<String>()
            for ((index, path) in paths.withIndex()) {
                SecureLogger.d(TAG, "Uploading item ${index + 1}/${paths.size} for $targetId")
                var uploadedUrl = uploadFile(path)
                if (uploadedUrl == null) {
                    // Retry once immediately after short delay
                    kotlinx.coroutines.delay(1000)
                    uploadedUrl = uploadFile(path)
                }
                if (uploadedUrl != null) {
                    uploadedUrls.add(uploadedUrl)
                } else {
                    SecureLogger.w(TAG, "Item ${index + 1}/${paths.size} failed for $targetId ($path). Rescheduling work.")
                    return@withContext Result.retry()
                }
            }

            if (uploadedUrls.size != paths.size) {
                SecureLogger.w(TAG, "Upload count mismatch (${uploadedUrls.size}/${paths.size}) for $targetId. Rescheduling work.")
                return@withContext Result.retry()
            }

            when (targetType.uppercase()) {
                "STORY" -> {
                    val primaryUrl = uploadedUrls.first()
                    firestore.collection("stories").document(targetId).set(
                        mapOf(
                            "mediaUrl" to primaryUrl,
                            "uploadStatus" to "SUCCESS"
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    ).await()
                    SecureLogger.d(TAG, "Updated Story $targetId with mediaUrl $primaryUrl")
                }
                "LINK" -> {
                    firestore.collection("links").document(targetId).set(
                        mapOf(
                            "mediaUrls" to uploadedUrls,
                            "uploadStatus" to "SUCCESS"
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    ).await()

                    val existing = linkDao.getLinkById(targetId)
                    if (existing != null) {
                        linkDao.insertLink(existing.copy(mediaUrls = uploadedUrls))
                    }
                    SecureLogger.d(TAG, "Updated Link $targetId with ${uploadedUrls.size} mediaUrls")
                }
            }

            Result.success()
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Error in CloudinaryUploadWorker: ${e.message}", e)
            Result.retry()
        }
    }

    private suspend fun uploadFile(path: String): String? = suspendCancellableCoroutine { cont ->
        try {
            val uploadUri = if (path.startsWith("content://")) {
                val uri = Uri.parse(path)
                try {
                    val tempFile = File(context.cacheDir, "cloudinary_tmp_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(6)}.tmp")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (tempFile.exists() && tempFile.length() > 0) {
                        Uri.fromFile(tempFile)
                    } else {
                        uri
                    }
                } catch (e: Exception) {
                    SecureLogger.w(TAG, "Fallback to direct URI as stream copy failed: ${e.message}")
                    uri
                }
            } else if (path.startsWith("file://")) {
                val uri = Uri.parse(path)
                val file = File(uri.path ?: "")
                if (file.exists()) Uri.fromFile(file) else uri
            } else {
                val file = File(path)
                Uri.fromFile(file)
            }

            val preset = com.linker.app.BuildConfig.CLOUDINARY_UPLOAD_PRESET.ifBlank { "default_preset" }
            val uploadRequest = if (com.linker.app.BuildConfig.CLOUDINARY_API_SECRET.isNotBlank()) {
                MediaManager.get().upload(uploadUri)
            } else {
                MediaManager.get().upload(uploadUri).unsigned(preset)
            }

            val requestId = uploadRequest
                .callback(object : UploadCallback {
                    override fun onStart(requestId: String) {
                        SecureLogger.d(TAG, "Cloudinary upload started for $path (id: $requestId)")
                    }
                    override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}
                    override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                        val url = resultData["secure_url"] as? String
                        SecureLogger.d(TAG, "Cloudinary upload succeeded: $url")
                        cont.resume(url)
                    }
                    override fun onError(requestId: String, error: ErrorInfo) {
                        SecureLogger.e(TAG, "Cloudinary upload error (${error.code}): ${error.description}")
                        cont.resume(null)
                    }
                    override fun onReschedule(requestId: String, error: ErrorInfo) {
                        SecureLogger.w(TAG, "Cloudinary upload rescheduled (${error.code}): ${error.description}")
                        cont.resume(null)
                    }
                })
                .dispatch()

            cont.invokeOnCancellation {
                MediaManager.get().cancelRequest(requestId)
            }
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Failed to initiate upload for $path", e)
            cont.resume(null)
        }
    }

    companion object {
        private const val TAG = "CloudinaryUploadWorker"
    }
}
