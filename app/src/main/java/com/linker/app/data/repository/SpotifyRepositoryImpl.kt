package com.linker.app.data.repository

import android.util.Base64
import com.google.gson.annotations.SerializedName
import com.linker.app.BuildConfig
import com.linker.app.core.util.Result
import com.linker.app.domain.repository.SpotifyRepository
import com.linker.app.presentation.screens.note.SpotifyTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import javax.inject.Inject
import javax.inject.Singleton

// Retrofit Models
data class SpotifyAuthResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("expires_in") val expiresIn: Int
)

data class SpotifySearchResponse(
    val tracks: SpotifyTracksInfo
)

data class SpotifyTracksInfo(
    val items: List<SpotifyTrackItem>
)

data class SpotifyTrackItem(
    val id: String,
    val name: String,
    val artists: List<SpotifyArtist>,
    val album: SpotifyAlbum
)

data class SpotifyArtist(val name: String)
data class SpotifyAlbum(val images: List<SpotifyImage>)
data class SpotifyImage(val url: String)

// Retrofit Interfaces
interface SpotifyAuthService {
    @FormUrlEncoded
    @POST("api/token")
    suspend fun getAccessToken(
        @Header("Authorization") authHeader: String,
        @Field("grant_type") grantType: String = "client_credentials"
    ): SpotifyAuthResponse
}

interface SpotifyApiService {
    @GET("v1/search")
    suspend fun searchTracks(
        @Header("Authorization") authHeader: String,
        @Query("q") query: String,
        @Query("type") type: String = "track",
        @Query("limit") limit: Int = 20
    ): SpotifySearchResponse
}

@Singleton
class SpotifyRepositoryImpl @Inject constructor() : SpotifyRepository {

    private var currentAccessToken: String? = null
    private var tokenExpiryTime: Long = 0

    private val authService: SpotifyAuthService by lazy {
        Retrofit.Builder()
            .baseUrl("https://accounts.spotify.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SpotifyAuthService::class.java)
    }

    private val apiService: SpotifyApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.spotify.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SpotifyApiService::class.java)
    }

    private suspend fun getValidToken(): String? {
        if (currentAccessToken != null && System.currentTimeMillis() < tokenExpiryTime) {
            return currentAccessToken
        }

        val clientId = BuildConfig.SPOTIFY_CLIENT_ID
        val clientSecret = BuildConfig.SPOTIFY_CLIENT_SECRET

        if (clientId.isBlank() || clientSecret.isBlank()) {
            return null // Keys not configured
        }

        try {
            val authString = "$clientId:$clientSecret"
            val encodedAuth = Base64.encodeToString(authString.toByteArray(), Base64.NO_WRAP)
            
            val response = authService.getAccessToken("Basic $encodedAuth")
            currentAccessToken = response.accessToken
            tokenExpiryTime = System.currentTimeMillis() + (response.expiresIn * 1000) - 60000 // 1 minute buffer
            return currentAccessToken
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    override suspend fun searchTracks(query: String): Result<List<SpotifyTrack>> = withContext(Dispatchers.IO) {
        val token = getValidToken() ?: return@withContext Result.Error("Spotify API Keys not configured or invalid.")

        try {
            val response = apiService.searchTracks("Bearer $token", query)
            val mappedResults = response.tracks.items.map { item ->
                SpotifyTrack(
                    id = item.id,
                    name = item.name,
                    artistName = item.artists.firstOrNull()?.name ?: "Unknown Artist",
                    albumArtUrl = item.album.images.firstOrNull()?.url
                )
            }
            Result.Success(mappedResults)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to search Spotify")
        }
    }
}
