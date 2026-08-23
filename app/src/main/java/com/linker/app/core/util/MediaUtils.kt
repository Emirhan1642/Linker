package com.linker.app.core.util

import android.content.Context
import android.net.Uri

object MediaUtils {

    /**
     * Strips "placeholder://" prefix from media URLs so they can be loaded by Coil, ExoPlayer, etc.
     * Returns empty string if the URL is blank or is a text-only placeholder.
     */
    fun sanitizeMediaUrl(rawUrl: String?): String {
        if (rawUrl.isNullOrBlank() || rawUrl == "placeholder://text_only" || rawUrl == "placeholder://empty") {
            return ""
        }
        return if (rawUrl.startsWith("placeholder://")) {
            rawUrl.removePrefix("placeholder://")
        } else {
            rawUrl
        }
    }

    /**
     * Determines whether a given Uri points to a video using ContentResolver MIME type and path extension.
     */
    fun isVideoUri(context: Context, uri: Uri): Boolean {
        try {
            val mimeType = context.contentResolver.getType(uri)
            if (mimeType?.startsWith("video/", ignoreCase = true) == true) {
                return true
            }
        } catch (_: Exception) {}

        val path = uri.toString().lowercase()
        return path.endsWith(".mp4") || path.endsWith(".mov") || path.endsWith(".mkv") ||
                path.endsWith(".webm") || path.endsWith(".3gp") || path.endsWith(".avi") ||
                path.contains("/video")
    }

    /**
     * Determines whether a URL or path is a video based on file extension and path patterns.
     */
    fun isVideoUrl(url: String?): Boolean {
        val clean = sanitizeMediaUrl(url).lowercase()
        if (clean.isBlank()) return false
        return clean.endsWith(".mp4") || clean.endsWith(".mov") || clean.endsWith(".mkv") ||
                clean.endsWith(".webm") || clean.endsWith(".3gp") || clean.endsWith(".avi") ||
                clean.contains("video/upload") || clean.contains("/video")
    }

    /**
     * Persistently copies a content:// or external Uri to the app's internal filesDir
     * while the calling Activity/Scope still has temporary read permissions.
     */
    fun copyUriToInternalStorage(context: Context, uriString: String): String {
        if (uriString.isBlank() || uriString.startsWith("http://") || uriString.startsWith("https://")) {
            return uriString
        }
        val clean = sanitizeMediaUrl(uriString)
        try {
            val uri = Uri.parse(clean)
            if (uri.scheme == "file") {
                val file = java.io.File(uri.path ?: "")
                if (file.exists()) return file.absolutePath
            }

            val mimeType = try { context.contentResolver.getType(uri) } catch (_: Exception) { null }
            val extension = when {
                mimeType?.startsWith("video/") == true || isVideoUrl(clean) -> "mp4"
                mimeType?.contains("png") == true || clean.endsWith(".png", ignoreCase = true) -> "png"
                mimeType?.contains("gif") == true || clean.endsWith(".gif", ignoreCase = true) -> "gif"
                else -> "jpg"
            }

            val isVideo = mimeType?.startsWith("video/") == true || isVideoUrl(clean)
            val uploadDir = java.io.File(context.filesDir, "media_uploads").apply { if (!exists()) mkdirs() }
            val targetFile = java.io.File(uploadDir, "upload_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(8)}.$extension")

            if (!isVideo) {
                // Compress image to ensure it stays well under Cloudinary's 10MB limit
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val bytes = input.readBytes()
                    val boundsOptions = android.graphics.BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)

                    var inSampleSize = 1
                    val maxDim = 1920
                    while ((boundsOptions.outWidth / inSampleSize) > maxDim || (boundsOptions.outHeight / inSampleSize) > maxDim) {
                        inSampleSize *= 2
                    }

                    // Read EXIF orientation to prevent portrait/landscape rotation issues
                    val exif = try {
                        android.media.ExifInterface(java.io.ByteArrayInputStream(bytes))
                    } catch (_: Exception) {
                        null
                    }
                    val orientation = exif?.getAttributeInt(
                        android.media.ExifInterface.TAG_ORIENTATION,
                        android.media.ExifInterface.ORIENTATION_NORMAL
                    ) ?: android.media.ExifInterface.ORIENTATION_NORMAL

                    val rotationDegrees = when (orientation) {
                        android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        android.media.ExifInterface.ORIENTATION_TRANSPOSE -> 90f
                        android.media.ExifInterface.ORIENTATION_TRANSVERSE -> 270f
                        else -> 0f
                    }

                    val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                        this.inSampleSize = inSampleSize
                    }
                    val decodedBitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
                    val finalBitmap = if (rotationDegrees != 0f && decodedBitmap != null) {
                        val matrix = android.graphics.Matrix().apply { postRotate(rotationDegrees) }
                        val rotated = android.graphics.Bitmap.createBitmap(
                            decodedBitmap, 0, 0, decodedBitmap.width, decodedBitmap.height, matrix, true
                        )
                        if (rotated != decodedBitmap) {
                            decodedBitmap.recycle()
                        }
                        rotated
                    } else {
                        decodedBitmap
                    }

                    if (finalBitmap != null) {
                        targetFile.outputStream().use { output ->
                            finalBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, output)
                        }
                        finalBitmap.recycle()
                    } else {
                        targetFile.outputStream().use { output ->
                            output.write(bytes)
                        }
                    }
                }
            } else {
                // Video: copy byte stream directly
                context.contentResolver.openInputStream(uri)?.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }

            if (targetFile.exists() && targetFile.length() > 0) {
                return targetFile.absolutePath
            }
        } catch (e: Exception) {
            android.util.Log.e("MediaUtils", "Failed to copy URI to internal storage: ${e.message}", e)
        }
        return clean
    }

    /**
     * Extracts duration in seconds for video files.
     */
    fun getVideoDurationSeconds(context: Context, pathOrUri: String): Double? {
        var retriever: android.media.MediaMetadataRetriever? = null
        return try {
            retriever = android.media.MediaMetadataRetriever()
            if (pathOrUri.startsWith("content://")) {
                retriever.setDataSource(context, Uri.parse(pathOrUri))
            } else {
                retriever.setDataSource(pathOrUri)
            }
            val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull()
            if (durationMs != null && durationMs > 0) {
                durationMs.toDouble() / 1000.0
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("MediaUtils", "Failed to extract video duration", e)
            null
        } finally {
            try {
                retriever?.release()
            } catch (_: Exception) {}
        }
    }
}

