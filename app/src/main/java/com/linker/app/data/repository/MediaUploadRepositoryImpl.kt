package com.linker.app.data.repository

import android.content.Context
import android.net.Uri
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.linker.app.core.util.Result
import com.linker.app.domain.repository.MediaUploadRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.resume

class MediaUploadRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : MediaUploadRepository {

    override suspend fun uploadMedia(uris: List<Uri>): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            // Check if MediaManager is initialized. This should ideally be done in Application class.
            // But doing a lazy check here is a safe fallback.
            try {
                MediaManager.get()
            } catch (e: Exception) {
                // If not initialized, it will throw an exception.
                // In a real app, initialize this in LinkerApp.kt with AppSecrets.
                return@withContext Result.Error("Cloudinary is not initialized. Please configure AppSecrets in Application class.")
            }

            val deferredUrls = uris.map { uri ->
                async {
                    uploadSingleMedia(uri)
                }
            }

            val urls = deferredUrls.awaitAll()
            
            // If any upload failed, urls will contain nulls or we can just filter
            val successfulUrls = urls.filterNotNull()

            if (successfulUrls.isEmpty() && uris.isNotEmpty()) {
                Result.Error("Failed to upload media files.")
            } else {
                Result.Success(successfulUrls)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown upload error")
        }
    }

    private suspend fun uploadSingleMedia(uri: Uri): String? = suspendCancellableCoroutine { continuation ->
        val requestId = MediaManager.get().upload(uri)
            .unsigned("default_preset") // Using unsigned upload as default if signatures are not set up
            .callback(object : UploadCallback {
                override fun onStart(requestId: String) {}

                override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}

                override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                    val url = resultData["secure_url"] as? String
                    continuation.resume(url)
                }

                override fun onError(requestId: String, error: ErrorInfo) {
                    continuation.resume(null)
                }

                override fun onReschedule(requestId: String, error: ErrorInfo) {
                    continuation.resume(null)
                }
            })
            .dispatch()

        continuation.invokeOnCancellation {
            MediaManager.get().cancelRequest(requestId)
        }
    }
}
