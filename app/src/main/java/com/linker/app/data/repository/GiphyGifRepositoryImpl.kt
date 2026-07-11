package com.linker.app.data.repository

import com.google.gson.annotations.SerializedName
import com.linker.app.BuildConfig
import com.linker.app.core.util.Result
import com.linker.app.domain.repository.GifItem
import com.linker.app.domain.repository.GifRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import javax.inject.Inject
import javax.inject.Singleton

// Giphy API Models
data class GiphyResponse(
    val data: List<GiphyData>
)

data class GiphyData(
    val id: String,
    val title: String,
    val images: GiphyImages
)

data class GiphyImages(
    @SerializedName("fixed_height") val fixedHeight: GiphyImageDetail,
    @SerializedName("original") val original: GiphyImageDetail
)

data class GiphyImageDetail(
    val url: String,
    val width: String,
    val height: String
)

interface GiphyApiService {
    @GET("v1/gifs/search")
    suspend fun searchGifs(
        @Query("api_key") apiKey: String,
        @Query("q") query: String,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
        @Query("rating") rating: String = "pg-13"
    ): GiphyResponse

    @GET("v1/gifs/trending")
    suspend fun getTrendingGifs(
        @Query("api_key") apiKey: String,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
        @Query("rating") rating: String = "pg-13"
    ): GiphyResponse
}

@Singleton
class GiphyGifRepositoryImpl @Inject constructor() : GifRepository {

    private val api: GiphyApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.giphy.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GiphyApiService::class.java)
    }

    private val apiKey: String
        get() = BuildConfig.GIPHY_API_KEY

    override suspend fun searchGifs(query: String, limit: Int, offset: Int): Result<List<GifItem>> {
        return withContext(Dispatchers.IO) {
            try {
                if (apiKey.isBlank()) {
                    return@withContext Result.Error("Giphy API key is missing")
                }
                val response = api.searchGifs(apiKey, query, limit, offset)
                Result.Success(mapResponse(response))
            } catch (e: Exception) {
                Result.Error("Failed to search GIFs: ${e.localizedMessage}")
            }
        }
    }

    override suspend fun getTrendingGifs(limit: Int, offset: Int): Result<List<GifItem>> {
        return withContext(Dispatchers.IO) {
            try {
                if (apiKey.isBlank()) {
                    return@withContext Result.Error("Giphy API key is missing")
                }
                val response = api.getTrendingGifs(apiKey, limit, offset)
                Result.Success(mapResponse(response))
            } catch (e: Exception) {
                Result.Error("Failed to get trending GIFs: ${e.localizedMessage}")
            }
        }
    }

    private fun mapResponse(response: GiphyResponse): List<GifItem> {
        return response.data.map { gif ->
            val w = gif.images.fixedHeight.width.toFloatOrNull() ?: 1f
            val h = gif.images.fixedHeight.height.toFloatOrNull() ?: 1f
            val aspectRatio = if (h > 0) w / h else 1f
            GifItem(
                id = gif.id,
                url = gif.images.original.url,
                previewUrl = gif.images.fixedHeight.url,
                title = gif.title,
                aspectRatio = aspectRatio
            )
        }
    }
}
