package com.linker.app.domain.repository

import android.net.Uri
import com.linker.app.core.util.Result

interface MediaUploadRepository {
    /**
     * Uploads a list of local Uris to a remote storage provider (e.g. Cloudinary)
     * and returns the list of uploaded URLs.
     */
    suspend fun uploadMedia(uris: List<Uri>): Result<List<String>>
}
