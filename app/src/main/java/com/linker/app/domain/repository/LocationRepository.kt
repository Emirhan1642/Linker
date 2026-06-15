package com.linker.app.domain.repository

import com.linker.app.core.util.Result

data class LocationSearchResult(
    val name: String,
    val displayName: String,
    val lat: Double,
    val lon: Double
)

interface LocationRepository {
    /**
     * Searches for a location using OpenStreetMap Nominatim API.
     */
    suspend fun searchLocation(query: String): Result<List<LocationSearchResult>>
}
