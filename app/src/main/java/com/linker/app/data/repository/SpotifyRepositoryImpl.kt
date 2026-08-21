package com.linker.app.data.repository

import android.util.Base64
import com.google.gson.annotations.SerializedName
import com.linker.app.BuildConfig
import com.linker.app.core.util.Result
import com.linker.app.data.local.LocalTrackCache
import com.linker.app.domain.model.*
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
import retrofit2.http.Path
import retrofit2.http.Query
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

// Retrofit Models
data class SpotifyAuthResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("expires_in") val expiresIn: Int
)

data class SpotifySearchResponse(
    val tracks: SpotifyTracksInfo?,
    val artists: SpotifyArtistsInfo?,
    val albums: SpotifyAlbumsInfo?,
    val playlists: SpotifyPlaylistsInfo?
)

data class SpotifyAlbumsInfo(
    val items: List<SpotifyAlbumItem>
)

data class SpotifyArtistsInfo(
    val items: List<SpotifyArtistItem>
)

data class SpotifyPlaylistsInfo(
    val items: List<SpotifyPlaylistSearchItem>
)

data class SpotifyPlaylistSearchItem(
    val id: String,
    val name: String,
    val images: List<SpotifyImage>?
)

data class SpotifyArtistItem(
    val id: String,
    val name: String,
    val images: List<SpotifyImage>?,
    val followers: SpotifyFollowers?
)

data class SpotifyFollowers(
    val total: Int
)

data class SpotifyArtistTopTracksResponse(
    val tracks: List<SpotifyTrackItem>
)

data class SpotifyArtistAlbumsResponse(
    val items: List<SpotifyAlbumItem>
)

data class SpotifyAlbumItem(
    val id: String,
    val name: String,
    val images: List<SpotifyImage>?,
    @SerializedName("release_date") val releaseDate: String?,
    @SerializedName("album_type") val albumType: String?
)

data class SpotifyRecommendationsResponse(
    val tracks: List<SpotifyTrackItem>
)

data class SpotifyPlaylistResponse(
    val items: List<SpotifyPlaylistItem>
)

data class SpotifyPlaylistItem(
    val track: SpotifyTrackItem?
)

data class SpotifyTracksInfo(
    val items: List<SpotifyTrackItem>
)

data class SpotifyTrackItem(
    val id: String?,
    val name: String,
    val artists: List<SpotifyArtist>,
    val album: SpotifyAlbum,
    @SerializedName("preview_url") val previewUrl: String?,
    @SerializedName("duration_ms") val durationMs: Long = 0L,
    val explicit: Boolean = false
)

data class SpotifyArtist(val id: String?, val name: String)
data class SpotifyAlbum(val images: List<SpotifyImage>?)
data class SpotifyImage(val url: String)

data class SpotifyAlbumResponse(
    val id: String,
    val name: String,
    val images: List<SpotifyImage>?,
    @SerializedName("release_date") val releaseDate: String?,
    val artists: List<SpotifyArtistItem>?,
    val tracks: SpotifyTracksInfo?
)

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
    suspend fun search(
        @Header("Authorization") authHeader: String,
        @Query("q") query: String,
        @Query("type") type: String,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null
    ): SpotifySearchResponse

    @GET("v1/artists/{id}")
    suspend fun getArtist(
        @Header("Authorization") authHeader: String,
        @Path("id") artistId: String
    ): SpotifyArtistItem

    @GET("v1/artists/{id}/top-tracks")
    suspend fun getArtistTopTracks(
        @Header("Authorization") authHeader: String,
        @Path("id") artistId: String,
        @Query("market") market: String = "TR"
    ): SpotifyArtistTopTracksResponse

    @GET("v1/albums/{id}")
    suspend fun getAlbum(
        @Header("Authorization") authHeader: String,
        @Path("id") albumId: String
    ): SpotifyAlbumResponse

    @GET("v1/artists/{id}/albums")
    suspend fun getArtistAlbums(
        @Header("Authorization") authHeader: String,
        @Path("id") artistId: String,
        @Query("include_groups") includeGroups: String = "album,single",
        @Query("limit") limit: Int = 10
    ): SpotifyArtistAlbumsResponse

    @GET("v1/recommendations")
    suspend fun getRecommendations(
        @Header("Authorization") authHeader: String,
        @Query("seed_genres") seedGenres: String,
        @Query("limit") limit: Int = 15
    ): SpotifyRecommendationsResponse

    @GET("v1/playlists/{playlist_id}/items")
    suspend fun getPlaylistTracks(
        @Header("Authorization") authHeader: String,
        @Path("playlist_id") playlistId: String,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null
    ): SpotifyPlaylistResponse
}

@Singleton
class SpotifyRepositoryImpl @Inject constructor(
    private val localTrackCache: LocalTrackCache
) : SpotifyRepository {

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

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .build()
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

    override suspend fun searchTracks(query: String, limit: Int?, offset: Int?): Result<List<SpotifyTrack>> = withContext(Dispatchers.IO) {
        val token = getValidToken() ?: return@withContext Result.Error("Spotify API Keys not configured or invalid.")

        try {
            val response = apiService.search(
                authHeader = "Bearer $token",
                query = query,
                type = "track",
                limit = limit,
                offset = offset
            )
            val mappedResults = response.tracks?.items?.filter { !it.id.isNullOrBlank() }?.map { item ->
                SpotifyTrack(
                    id = item.id ?: "",
                    name = item.name,
                    artistName = item.artists.firstOrNull()?.name ?: "Unknown Artist",
                    albumArtUrl = item.album.images?.firstOrNull()?.url,
                    previewUrl = item.previewUrl,
                    durationMs = item.durationMs,
                    isExplicit = item.explicit
                )
            } ?: emptyList()
            Result.Success(mappedResults)
        } catch (e: retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string() ?: "No error body"
            Result.Error("Spotify API Error: ${e.code()} - $errorBody")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to search Spotify")
        }
    }

    override suspend fun search(query: String, type: SpotifySearchType, limit: Int?, offset: Int?): Result<List<SpotifySearchResultItem>> = withContext(Dispatchers.IO) {
        val token = getValidToken() ?: return@withContext Result.Error("Spotify API Keys not configured or invalid.")

        try {
            val typeStr = when (type) {
                SpotifySearchType.ALL -> "track,artist,album"
                SpotifySearchType.TRACKS -> "track"
                SpotifySearchType.ARTISTS -> "artist"
                SpotifySearchType.ALBUMS -> "album"
            }
            
            val response = apiService.search(
                authHeader = "Bearer $token",
                query = query,
                type = typeStr,
                limit = limit,
                offset = offset
            )
            
            val mappedResults = mutableListOf<SpotifySearchResultItem>()
            
            // Add artists first if present
            response.artists?.items?.forEach { artistItem ->
                if (artistItem.id.isNotBlank() && artistItem.name.isNotBlank()) {
                    mappedResults.add(
                        SpotifySearchResultItem.Artist(
                            SpotifyArtistDomain(
                                id = artistItem.id,
                                name = artistItem.name,
                                imageUrl = artistItem.images?.firstOrNull()?.url,
                                followerCount = artistItem.followers?.total ?: 0
                            )
                        )
                    )
                }
            }
            
            // Add albums
            response.albums?.items?.forEach { albumItem ->
                if (albumItem.id.isNotBlank() && albumItem.name.isNotBlank()) {
                    mappedResults.add(
                        SpotifySearchResultItem.Album(
                            SpotifyAlbumDomain(
                                id = albumItem.id,
                                name = albumItem.name,
                                imageUrl = albumItem.images?.firstOrNull()?.url,
                                releaseYear = albumItem.releaseDate?.take(4)
                            )
                        )
                    )
                }
            }
            
            // Add tracks
            response.tracks?.items?.filter { !it.id.isNullOrBlank() }?.forEach { item ->
                mappedResults.add(
                    SpotifySearchResultItem.Track(
                        SpotifyTrack(
                            id = item.id ?: "",
                            name = item.name,
                            artistName = item.artists.firstOrNull()?.name ?: "Unknown Artist",
                            albumArtUrl = item.album.images?.firstOrNull()?.url,
                            previewUrl = item.previewUrl,
                            durationMs = item.durationMs,
                            isExplicit = item.explicit
                        )
                    )
                )
            }
            
            Result.Success(mappedResults)
        } catch (e: retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string() ?: "No error body"
            Result.Error("Spotify API Error: ${e.code()} - $errorBody")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to search Spotify")
        }
    }

    private data class ScrapedArtistProfileData(
        val domain: SpotifyArtistDomain,
        val topTracks: List<SpotifyTrack>,
        val popularReleases: List<SpotifyAlbumDomain>,
        val albums: List<SpotifyAlbumDomain>,
        val singles: List<SpotifyAlbumDomain>,
        val compilations: List<SpotifyAlbumDomain>
    )

    private suspend fun scrapeFullArtistProfileData(artistId: String): ScrapedArtistProfileData? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://open.spotify.com/artist/$artistId")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null
            val html = response.body?.string() ?: return@withContext null

            val regex = """id="initialState" type="text/plain">([^<]+)</script>""".toRegex()
            val matchResult = regex.find(html) ?: return@withContext null
            
            val base64Data = matchResult.groupValues[1]
            val jsonString = String(Base64.decode(base64Data, Base64.DEFAULT), Charsets.UTF_8)
            val jsonObject = org.json.JSONObject(jsonString)
            
            val entities = jsonObject.optJSONObject("entities")?.optJSONObject("items") ?: return@withContext null
            val artistObj = entities.optJSONObject("spotify:artist:$artistId") ?: return@withContext null
            
            // 1. Artist Domain Info
            val profileObj = artistObj.optJSONObject("profile")
            val statsObj = artistObj.optJSONObject("stats")
            val visualsObj = artistObj.optJSONObject("visuals")
            
            val artistName = profileObj?.optString("name") ?: "Unknown Artist"
            val followers = statsObj?.optInt("followers", 0) ?: 0
            val avatarUrl = visualsObj?.optJSONObject("avatarImage")?.optJSONArray("sources")?.optJSONObject(0)?.optString("url")
            
            val domain = SpotifyArtistDomain(
                id = artistId,
                name = artistName,
                imageUrl = avatarUrl,
                followerCount = followers
            )
            
            // 2. Top Tracks
            val topTracksArray = artistObj.optJSONObject("discography")?.optJSONObject("topTracks")?.optJSONArray("items")
            val tracks = mutableListOf<SpotifyTrack>()
            if (topTracksArray != null) {
                for (i in 0 until topTracksArray.length()) {
                    val trackObj = topTracksArray.optJSONObject(i)?.optJSONObject("track") ?: continue
                    val id = trackObj.optString("uri", "").substringAfterLast(":", "")
                    if (id.isEmpty()) continue
                    
                    val name = trackObj.optString("name", "Unknown Track")
                    val explicit = trackObj.optJSONObject("contentRating")?.optString("label") == "EXPLICIT"
                    
                    var previewUrl: String? = null
                    val audioPreviews = trackObj.optJSONObject("previews")?.optJSONObject("audioPreviews")?.optJSONArray("items")
                    if (audioPreviews != null && audioPreviews.length() > 0) {
                        val url = audioPreviews.optJSONObject(0)?.optString("url")
                        if (!url.isNullOrBlank()) {
                            previewUrl = url
                        }
                    }
                    
                    var albumArtUrl: String? = null
                    val sources = trackObj.optJSONObject("albumOfTrack")?.optJSONObject("coverArt")?.optJSONArray("sources")
                    if (sources != null && sources.length() > 0) {
                        albumArtUrl = sources.optJSONObject(0)?.optString("url")
                    }
                    
                    tracks.add(
                        SpotifyTrack(
                            id = id,
                            name = name,
                            artistName = artistName,
                            albumArtUrl = albumArtUrl,
                            previewUrl = previewUrl,
                            durationMs = 0L,
                            isExplicit = explicit
                        )
                    )
                }
            }
            
            // 3. Albums & Singles & Compilations & Popular Releases
            val discography = artistObj.optJSONObject("discography")
            
            fun parseItems(type: String): List<SpotifyAlbumDomain> {
                val albumsList = mutableListOf<SpotifyAlbumDomain>()
                val itemsArray = discography?.optJSONObject(type)?.optJSONArray("items") ?: return albumsList
                for (i in 0 until itemsArray.length()) {
                    val itemWrapper = itemsArray.optJSONObject(i) ?: continue
                    
                    val albumObj = if (itemWrapper.has("releases")) {
                        itemWrapper.optJSONObject("releases")?.optJSONArray("items")?.optJSONObject(0) ?: continue
                    } else {
                        itemWrapper
                    }
                    
                    val uri = albumObj.optString("uri", "")
                    val id = uri.substringAfterLast(":", "")
                    if (id.isEmpty()) continue
                    
                    val name = albumObj.optString("name", "Unknown")
                    var imageUrl: String? = null
                    val sources = albumObj.optJSONObject("coverArt")?.optJSONArray("sources")
                    if (sources != null && sources.length() > 0) {
                        imageUrl = sources.optJSONObject(0)?.optString("url")
                    }
                    val year = albumObj.optJSONObject("date")?.optInt("year")?.toString()
                    
                    if (albumsList.none { it.id == id }) {
                        albumsList.add(SpotifyAlbumDomain(id, name, imageUrl, year))
                    }
                }
                return albumsList
            }
            
            val popularReleases = parseItems("popularReleasesAlbums")
            val albums = parseItems("albums")
            val singles = parseItems("singles")
            val compilations = parseItems("compilations")
            
            return@withContext ScrapedArtistProfileData(domain, tracks, popularReleases, albums, singles, compilations)
            
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    override suspend fun getArtistProfile(artistId: String): Result<SpotifyArtistProfile> = withContext(Dispatchers.IO) {
        val token = getValidToken() ?: return@withContext Result.Error("Spotify API Keys not configured or invalid.")

        try {
            val scrapedData = scrapeFullArtistProfileData(artistId) ?: return@withContext Result.Error("Failed to scrape artist profile")
            
            // 4. Try to fetch "This is [Artist Name]" playlist via search
            var thisIsPlaylistId: String? = null
            try {
                val searchRes = apiService.search("Bearer $token", "This Is ${scrapedData.domain.name}", "playlist", 1)
                val firstPlaylist = searchRes.playlists?.items?.firstOrNull()
                if (firstPlaylist != null && firstPlaylist.name.contains("This Is ${scrapedData.domain.name}", ignoreCase = true)) {
                    thisIsPlaylistId = firstPlaylist.id
                }
            } catch (e: Exception) {
                // Ignore search error for playlist
            }

            Result.Success(
                SpotifyArtistProfile(
                    artist = scrapedData.domain,
                    topTracks = scrapedData.topTracks.take(5),
                    popularReleases = scrapedData.popularReleases,
                    albums = scrapedData.albums,
                    singles = scrapedData.singles,
                    compilations = scrapedData.compilations,
                    thisIsPlaylistId = thisIsPlaylistId
                )
            )
        } catch (e: retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string() ?: "No error body"
            Result.Error("Spotify API Error: ${e.code()} - $errorBody")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to fetch artist profile")
        }
    }

    private suspend fun scrapeAlbumPreviews(albumId: String): Map<String, String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://open.spotify.com/album/$albumId")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyMap()
            val html = response.body?.string() ?: return@withContext emptyMap()

            val regex = """id="initialState" type="text/plain">([^<]+)</script>""".toRegex()
            val matchResult = regex.find(html) ?: return@withContext emptyMap()
            
            val base64Data = matchResult.groupValues[1]
            val jsonString = String(Base64.decode(base64Data, Base64.DEFAULT), Charsets.UTF_8)
            val jsonObject = org.json.JSONObject(jsonString)
            
            val entities = jsonObject.optJSONObject("entities")?.optJSONObject("items") ?: return@withContext emptyMap()
            val albumObj = entities.optJSONObject("spotify:album:$albumId") ?: return@withContext emptyMap()
            
            val tracksV2 = albumObj.optJSONObject("tracksV2")?.optJSONArray("items") ?: return@withContext emptyMap()
            
            val previews = mutableMapOf<String, String>()
            for (i in 0 until tracksV2.length()) {
                val trackObj = tracksV2.optJSONObject(i)?.optJSONObject("track") ?: continue
                val id = trackObj.optString("uri", "").substringAfterLast(":", "")
                if (id.isEmpty()) continue
                
                val audioPreviews = trackObj.optJSONObject("previews")?.optJSONObject("audioPreviews")?.optJSONArray("items")
                if (audioPreviews != null && audioPreviews.length() > 0) {
                    val url = audioPreviews.optJSONObject(0)?.optString("url")
                    if (!url.isNullOrBlank()) {
                        previews[id] = url
                    }
                }
            }
            return@withContext previews
        } catch (e: Exception) {
            return@withContext emptyMap()
        }
    }

    override suspend fun getAlbumProfile(albumId: String): Result<SpotifyAlbumProfile> = withContext(Dispatchers.IO) {
        val token = getValidToken() ?: return@withContext Result.Error("Spotify API Keys not configured or invalid.")

        try {
            val response = apiService.getAlbum("Bearer $token", albumId)
            
            val albumImageUrl = response.images?.firstOrNull()?.url
            val albumDomain = SpotifyAlbumDomain(
                id = response.id,
                name = response.name,
                imageUrl = albumImageUrl,
                releaseYear = response.releaseDate?.take(4)
            )

            val artistsDomain = response.artists?.map {
                SpotifyArtistDomain(
                    id = it.id ?: "",
                    name = it.name,
                    imageUrl = null,
                    followerCount = 0
                )
            } ?: emptyList()

            val previewsMap = scrapeAlbumPreviews(albumId)

            val tracks = response.tracks?.items?.map { item ->
                val trackId = item.id ?: ""
                SpotifyTrack(
                    id = trackId,
                    name = item.name,
                    artistName = item.artists?.joinToString(", ") { it.name }?.takeIf { it.isNotBlank() } ?: "Unknown Artist",
                    albumArtUrl = albumImageUrl, // Map parent album image to tracks
                    previewUrl = previewsMap[trackId] ?: item.previewUrl,
                    durationMs = item.durationMs,
                    isExplicit = item.explicit
                )
            } ?: emptyList()

            Result.Success(
                SpotifyAlbumProfile(
                    album = albumDomain,
                    artists = artistsDomain,
                    tracks = tracks
                )
            )
        } catch (e: retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string() ?: "No error body"
            Result.Error("Spotify API Error: ${e.code()} - $errorBody")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to fetch album profile")
        }
    }

    override suspend fun getPlaylistTracks(playlistId: String, limit: Int?, offset: Int?): Result<List<SpotifyTrack>> = withContext(Dispatchers.IO) {
        val token = getValidToken() ?: return@withContext Result.Error("Spotify API Keys not configured or invalid.")

        try {
            val response = apiService.getPlaylistTracks(
                authHeader = "Bearer $token",
                playlistId = playlistId,
                limit = limit,
                offset = offset
            )
            val mappedResults = response.items.mapNotNull { it.track }.filter { !it.id.isNullOrBlank() }.map { item ->
                SpotifyTrack(
                    id = item.id ?: "",
                    name = item.name,
                    artistName = item.artists.firstOrNull()?.name ?: "Unknown Artist",
                    albumArtUrl = item.album.images?.firstOrNull()?.url,
                    previewUrl = item.previewUrl,
                    durationMs = item.durationMs,
                    isExplicit = item.explicit
                )
            }
            Result.Success(mappedResults)
        } catch (e: retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string() ?: "No error body"
            Result.Error("Spotify API Error: ${e.code()} - $errorBody")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to get playlist tracks")
        }
    }

    override suspend fun scrapePlaylistTracks(playlistId: String, limit: Int?, offset: Int?): Result<List<SpotifyTrack>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://open.spotify.com/playlist/$playlistId")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.Error("Web request failed: ${response.code}")
            }
            val html = response.body?.string() ?: return@withContext Result.Error("Empty HTML body")

            val regex = """id="initialState" type="text/plain">([^<]+)</script>""".toRegex()
            val matchResult = regex.find(html) ?: return@withContext Result.Error("Could not find initial state in HTML")
            
            val base64Data = matchResult.groupValues[1]
            val jsonString = String(Base64.decode(base64Data, Base64.DEFAULT), Charsets.UTF_8)
            
            val jsonObject = org.json.JSONObject(jsonString)
            val entities = jsonObject.optJSONObject("entities")?.optJSONObject("items")
            val playlistObj = entities?.optJSONObject("spotify:playlist:$playlistId")
            val contentItems = playlistObj?.optJSONObject("content")?.optJSONArray("items")
                ?: return@withContext Result.Error("Could not parse playlist tracks")

            val allTracks = mutableListOf<SpotifyTrack>()
            for (i in 0 until contentItems.length()) {
                val itemV2 = contentItems.optJSONObject(i)?.optJSONObject("itemV2") ?: continue
                val data = itemV2.optJSONObject("data") ?: continue
                if (data.optString("__typename") != "Track") continue

                val uri = data.optString("uri", "")
                val id = uri.substringAfterLast(":", "")
                if (id.isEmpty()) continue

                val name = data.optString("name", "Unknown Track")
                
                val artistsArray = data.optJSONObject("artists")?.optJSONArray("items")
                val artistName = if (artistsArray != null && artistsArray.length() > 0) {
                    artistsArray.optJSONObject(0)?.optJSONObject("profile")?.optString("name", "Unknown Artist") ?: "Unknown Artist"
                } else "Unknown Artist"

                val albumSources = data.optJSONObject("albumOfTrack")?.optJSONObject("coverArt")?.optJSONArray("sources")
                val albumArtUrl = if (albumSources != null && albumSources.length() > 0) {
                    albumSources.optJSONObject(0)?.optString("url")
                } else null

                val audioPreviews = data.optJSONObject("previews")?.optJSONObject("audioPreviews")?.optJSONArray("items")
                val previewUrl = if (audioPreviews != null && audioPreviews.length() > 0) {
                    audioPreviews.optJSONObject(0)?.optString("url")
                } else null

                val durationMs = data.optJSONObject("duration")?.optLong("totalMilliseconds", 0L) ?: 0L
                val isExplicit = data.optJSONObject("contentRating")?.optString("label") == "EXPLICIT"

                allTracks.add(
                    SpotifyTrack(
                        id = id,
                        name = name,
                        artistName = artistName,
                        albumArtUrl = albumArtUrl,
                        previewUrl = previewUrl,
                        durationMs = durationMs,
                        isExplicit = isExplicit
                    )
                )
            }

            // Apply pagination manually
            val start = offset ?: 0
            val size = limit ?: allTracks.size
            
            if (start >= allTracks.size) {
                return@withContext Result.Success(emptyList())
            }
            val end = minOf(start + size, allTracks.size)
            
            Result.Success(allTracks.subList(start, end))
        } catch (e: Exception) {
            Result.Error("Scraping failed: ${e.message}")
        }
    }

    override suspend fun scrapeTrackPreviewUrl(trackId: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://open.spotify.com/track/$trackId")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null
            val html = response.body?.string() ?: return@withContext null

            val regex = """id="initialState" type="text/plain">([^<]+)</script>""".toRegex()
            val matchResult = regex.find(html) ?: return@withContext null
            
            val base64Data = matchResult.groupValues[1]
            val jsonString = String(Base64.decode(base64Data, Base64.DEFAULT), Charsets.UTF_8)
            val jsonObject = org.json.JSONObject(jsonString)
            
            val entities = jsonObject.optJSONObject("entities")?.optJSONObject("items") ?: return@withContext null
            val trackObj = entities.optJSONObject("spotify:track:$trackId") ?: return@withContext null
            
            val audioPreviews = trackObj.optJSONObject("previews")?.optJSONObject("audioPreviews")?.optJSONArray("items")
            if (audioPreviews != null && audioPreviews.length() > 0) {
                val url = audioPreviews.optJSONObject(0)?.optString("url")
                if (!url.isNullOrBlank()) {
                    return@withContext url
                }
            }
            return@withContext null
        } catch (e: Exception) {
            return@withContext null
        }
    }

    override suspend fun getRecommendations(): Result<List<SpotifyTrack>> = withContext(Dispatchers.IO) {
        val token = getValidToken() ?: return@withContext Result.Error("Spotify API Keys not configured or invalid.")

        try {
            // Using a generic search query to get popular tracks to avoid 403 Forbidden on Playlists
            val response = apiService.search(
                authHeader = "Bearer $token",
                query = "year:2024",
                type = "track",
                limit = 10
            )
            val mappedResults = response.tracks?.items?.filter { !it.id.isNullOrBlank() }?.map { track ->
                SpotifyTrack(
                    id = track.id ?: "",
                    name = track.name,
                    artistName = track.artists.firstOrNull()?.name ?: "Unknown Artist",
                    albumArtUrl = track.album.images?.firstOrNull()?.url,
                    previewUrl = track.previewUrl,
                    durationMs = track.durationMs
                )
            } ?: emptyList()
            Result.Success(mappedResults)
        } catch (e: retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string() ?: "No error body"
            Result.Error("Spotify API Error: ${e.code()} - $errorBody")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to get recommendations")
        }
    }

    override suspend fun getRecommendationsByGenre(genre: String, limit: Int): Result<List<SpotifyTrack>> = withContext(Dispatchers.IO) {
        val token = getValidToken() ?: return@withContext Result.Error("Spotify API Keys not configured or invalid.")

        try {
            val response = apiService.getRecommendations(
                authHeader = "Bearer $token",
                seedGenres = genre,
                limit = limit
            )
            val mappedResults = response.tracks.filter { !it.id.isNullOrBlank() }.map { track ->
                SpotifyTrack(
                    id = track.id ?: "",
                    name = track.name,
                    artistName = track.artists.firstOrNull()?.name ?: "Unknown Artist",
                    albumArtUrl = track.album.images?.firstOrNull()?.url,
                    previewUrl = track.previewUrl,
                    durationMs = track.durationMs,
                    isExplicit = track.explicit
                )
            }
            Result.Success(mappedResults)
        } catch (e: retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string() ?: "No error body"
            Result.Error("Spotify API Error: ${e.code()} - $errorBody")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to get recommendations")
        }
    }

    override fun getLocalRecentTracks(): List<SpotifyTrack> {
        return localTrackCache.getRecentTracks()
    }

    override fun saveLocalRecentTrack(track: SpotifyTrack) {
        localTrackCache.saveTrack(track)
    }
}
