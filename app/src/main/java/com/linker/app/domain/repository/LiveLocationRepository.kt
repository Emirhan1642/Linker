package com.linker.app.domain.repository

import com.linker.app.core.util.Result
import kotlinx.coroutines.flow.Flow

/**
 * Device location data from GPS/network provider.
 *
 * @property lat Latitude in decimal degrees.
 * @property lon Longitude in decimal degrees.
 * @property updatedAt Epoch ms when this location was acquired.
 */
data class DeviceLocation(
    val lat: Double,
    val lon: Double,
    val updatedAt: Long
)

/**
 * Human-readable place name resolved via reverse geocoding.
 *
 * @property city City/municipality name (e.g. "Istanbul").
 * @property district Sub-district / neighbourhood (e.g. "Kadıköy"). May be empty.
 */
data class PlaceName(
    val city: String,
    val district: String
) {
    /** Returns "District, City" or just "City" if district is blank. */
    fun display(): String = if (district.isNotBlank()) "$district, $city" else city
}

/**
 * Repository for acquiring the device's live GPS location and resolving
 * human-readable place names via reverse geocoding.
 *
 * Implementations must ensure that location access permissions have been
 * granted before calling these methods.
 */
interface LiveLocationRepository {

    /**
     * Returns the device's best last-known location, or requests a fresh
     * one if none is available. Suspend until a result is produced.
     *
     * Returns [Result.Error] if permissions are not granted or the device
     * cannot determine a location within a reasonable timeout.
     */
    suspend fun getCurrentLocation(): Result<DeviceLocation>

    /**
     * Converts GPS coordinates to a human-readable [PlaceName] using the
     * OpenStreetMap Nominatim reverse geocoding API.
     *
     * @param lat Latitude.
     * @param lon Longitude.
     */
    suspend fun reverseGeocode(lat: Double, lon: Double): Result<PlaceName>

    /**
     * Emits [DeviceLocation] updates at the requested interval while the
     * returned [Flow] is collected.
     *
     * The flow completes when the collection scope is cancelled (e.g. when
     * the ViewModel is cleared).
     *
     * @param intervalMs Minimum time between updates in milliseconds.
     */
    fun observeLocationUpdates(intervalMs: Long = 5_000L): Flow<DeviceLocation>
}
