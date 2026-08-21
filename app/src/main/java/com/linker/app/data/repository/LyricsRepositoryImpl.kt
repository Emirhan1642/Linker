package com.linker.app.data.repository

import com.google.gson.annotations.SerializedName
import com.linker.app.core.util.Result
import com.linker.app.domain.repository.LyricsRepository
import com.linker.app.domain.repository.SyncedLyricLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

data class LrclibResponse(
    @SerializedName("syncedLyrics") val syncedLyrics: String?
)

interface LrclibApiService {
    @GET("api/get")
    suspend fun getLyrics(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String
    ): LrclibResponse
    
    @GET("api/search")
    suspend fun searchLyrics(
        @Query("q") query: String
    ): List<LrclibResponse>
}

@Singleton
class LyricsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : LyricsRepository {

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "LinkerApp/1.0 (Android)")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    private val apiService: LrclibApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://lrclib.net/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LrclibApiService::class.java)
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("lyrics_cache_prefs", Context.MODE_PRIVATE)
    }
    private val gson = Gson()

    // Thread-safe memory cache to prevent reloading the same lyrics across coroutines
    private val lyricsCache = java.util.concurrent.ConcurrentHashMap<String, List<SyncedLyricLine>>()

    override fun getCachedLyrics(trackName: String, artistName: String): List<SyncedLyricLine>? {
        val cacheKey = "$trackName-$artistName"
        
        // 1. Check memory cache
        lyricsCache[cacheKey]?.let { return it }
        
        // 2. Check disk cache
        prefs.getString(cacheKey, null)?.let { json ->
            try {
                val type = object : TypeToken<List<SyncedLyricLine>>() {}.type
                val list: List<SyncedLyricLine> = gson.fromJson(json, type)
                if (list.isNotEmpty()) {
                    lyricsCache[cacheKey] = list
                    return list
                }
            } catch (e: Exception) {
                // Ignore parsing errors
            }
        }
        
        return null
    }

    override suspend fun getSyncedLyrics(trackName: String, artistName: String): Result<List<SyncedLyricLine>> = withContext(Dispatchers.IO) {
        val cacheKey = "$trackName-$artistName"
        getCachedLyrics(trackName, artistName)?.let { return@withContext Result.Success(it) }

        try {
            val cleanName = trackName.replace(Regex("(?i)\\s*-\\s*(remastered|remaster|radio edit|live|feat\\.|ft\\.).*"), "").trim()
            var lyrics: String? = null
            kotlinx.coroutines.coroutineScope {
                val channel = kotlinx.coroutines.channels.Channel<String?>(2)
                
                launch {
                    val res = try {
                        apiService.searchLyrics("$cleanName $artistName")
                            .firstOrNull { !it.syncedLyrics.isNullOrBlank() }?.syncedLyrics
                    } catch (e: Exception) { null }
                    channel.send(res)
                }
                
                launch {
                    val res = try {
                        apiService.getLyrics(cleanName, artistName).syncedLyrics
                    } catch (e: Exception) { null }
                    channel.send(res)
                }

                val first = channel.receive()
                if (!first.isNullOrBlank()) {
                    lyrics = first
                    coroutineContext.cancelChildren() // Cancel the slower request
                } else {
                    lyrics = channel.receive() // Wait for the second one if the first failed
                }
            }

            if (lyrics.isNullOrBlank()) {
                return@withContext Result.Error("Şarkı sözü bulunamadı")
            }

            val parsedLines = parseLrc(lyrics)
            if (parsedLines.isEmpty()) {
                return@withContext Result.Error("Senkronize söz bulunamadı")
            }

            lyricsCache[cacheKey] = parsedLines
            prefs.edit().putString(cacheKey, gson.toJson(parsedLines)).apply()
            Result.Success(parsedLines)
        } catch (e: Exception) {
            Result.Error("Bağlantı zaman aşımına uğradı veya söz bulunamadı.")
        }
    }

    private fun parseLrc(lrcContent: String): List<SyncedLyricLine> {
        val lines = mutableListOf<SyncedLyricLine>()
        // Match [mm:ss.xx] text
        val regex = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)")
        
        lrcContent.lines().forEach { line ->
            val matchResult = regex.find(line)
            if (matchResult != null) {
                val minutes = matchResult.groupValues[1].toLong()
                val seconds = matchResult.groupValues[2].toLong()
                val millisRaw = matchResult.groupValues[3]
                val millis = if (millisRaw.length == 2) millisRaw.toLong() * 10 else millisRaw.toLong()
                
                val text = matchResult.groupValues[4].trim()
                
                val timeMs = (minutes * 60 * 1000) + (seconds * 1000) + millis
                if (text.isNotEmpty()) {
                    lines.add(SyncedLyricLine(timeMs, text))
                }
            }
        }
        return lines.sortedBy { it.timeMs }
    }
}
