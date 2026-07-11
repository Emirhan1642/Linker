package com.linker.app.domain.repository

import com.linker.app.core.util.Result

/**
 * Basic domain model for a GIF.
 */
data class GifItem(
    val id: String,
    val url: String, // MP4 or GIF url
    val previewUrl: String,
    val title: String,
    val aspectRatio: Float
)

/**
 * Repository interface for fetching GIFs (e.g. from Giphy).
 */
interface GifRepository {
    /**
     * Searches for GIFs using a query string.
     */
    suspend fun searchGifs(query: String, limit: Int = 20, offset: Int = 0): Result<List<GifItem>>

    /**
     * Gets trending GIFs.
     */
    suspend fun getTrendingGifs(limit: Int = 20, offset: Int = 0): Result<List<GifItem>>
}
