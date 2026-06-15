package com.linker.app.data.repository

import com.google.gson.annotations.SerializedName
import com.linker.app.core.util.Result
import com.linker.app.domain.repository.LocationRepository
import com.linker.app.domain.repository.LocationSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import javax.inject.Inject
import javax.inject.Singleton

// OSM Nominatim Response Model
data class NominatimResponse(
    @SerializedName("place_id") val placeId: Long,
    @SerializedName("lat") val lat: String,
    @SerializedName("lon") val lon: String,
    @SerializedName("display_name") val displayName: String,
    @SerializedName("name") val name: String
)

interface NominatimService {
    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 10,
        @Query("addressdetails") addressDetails: Int = 1
    ): List<NominatimResponse>
}

@Singleton
class LocationRepositoryImpl @Inject constructor() : LocationRepository {

    // Ideally injected, but creating locally for simplicity and modularity here
    private val api: NominatimService by lazy {
        Retrofit.Builder()
            .baseUrl("https://nominatim.openstreetmap.org/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NominatimService::class.java)
    }

    override suspend fun searchLocation(query: String): Result<List<LocationSearchResult>> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext Result.Success(emptyList())

        try {
            val response = api.search(query)
            val results = response.map {
                LocationSearchResult(
                    name = it.name.ifBlank { it.displayName.split(",").firstOrNull() ?: "Unknown" },
                    displayName = it.displayName,
                    lat = it.lat.toDoubleOrNull() ?: 0.0,
                    lon = it.lon.toDoubleOrNull() ?: 0.0
                )
            }
            Result.Success(results)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error while searching location")
        }
    }
}
