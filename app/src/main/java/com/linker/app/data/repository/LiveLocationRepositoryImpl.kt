package com.linker.app.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.gson.annotations.SerializedName
import com.linker.app.core.util.Result
import com.linker.app.domain.repository.DeviceLocation
import com.linker.app.domain.repository.LiveLocationRepository
import com.linker.app.domain.repository.PlaceName
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

// ─────────────────────────────────────────────
// Nominatim Reverse Geocode API models
// ─────────────────────────────────────────────

private data class NominatimReverseResponse(
    @SerializedName("address") val address: NominatimAddress?
)

private data class NominatimAddress(
    @SerializedName("city") val city: String?,
    @SerializedName("town") val town: String?,
    @SerializedName("village") val village: String?,
    @SerializedName("county") val county: String?,
    @SerializedName("state") val state: String?,
    @SerializedName("suburb") val suburb: String?,
    @SerializedName("city_district") val cityDistrict: String?,
    @SerializedName("district") val district: String?,
    @SerializedName("neighbourhood") val neighbourhood: String?,
    @SerializedName("quarter") val quarter: String?
)

private interface NominatimReverseService {
    @GET("reverse")
    suspend fun reverse(
        @Header("User-Agent") userAgent: String = "LinkerApp/1.0",
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("format") format: String = "json",
        @Query("addressdetails") addressDetails: Int = 1,
        @Query("zoom") zoom: Int = 14
    ): NominatimReverseResponse
}

// ─────────────────────────────────────────────
// Implementation
// ─────────────────────────────────────────────

@Singleton
class LiveLocationRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : LiveLocationRepository {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val nominatim: NominatimReverseService by lazy {
        Retrofit.Builder()
            .baseUrl("https://nominatim.openstreetmap.org/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NominatimReverseService::class.java)
    }

    @SuppressLint("MissingPermission")
    override suspend fun getCurrentLocation(): Result<DeviceLocation> = withContext(Dispatchers.IO) {
        try {
            // 1. Try last known location first (fast path)
            val lastKnown = fusedClient.lastLocation.await()
            if (lastKnown != null) {
                return@withContext Result.Success(
                    DeviceLocation(lastKnown.latitude, lastKnown.longitude, System.currentTimeMillis())
                )
            }

            // 2. Request a fresh single update via suspendCancellableCoroutine
            val freshLocation: DeviceLocation = suspendCancellableCoroutine { cont ->
                val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L)
                    .setMaxUpdates(1)
                    .setWaitForAccurateLocation(false)
                    .build()

                val callback = object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        fusedClient.removeLocationUpdates(this)
                        val loc = result.lastLocation
                        if (loc != null) {
                            cont.resume(DeviceLocation(loc.latitude, loc.longitude, System.currentTimeMillis()))
                        } else {
                            cont.cancel(Exception("Location result was null"))
                        }
                    }
                }

                fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
                cont.invokeOnCancellation { fusedClient.removeLocationUpdates(callback) }
            }
            Result.Success(freshLocation)
        } catch (e: SecurityException) {
            Result.Error("Konum izni verilmedi")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Konum alınamadı")
        }
    }

    override suspend fun reverseGeocode(lat: Double, lon: Double): Result<PlaceName> =
        withContext(Dispatchers.IO) {
            try {
                val response = nominatim.reverse(lat = lat, lon = lon)
                val addr = response.address
                    ?: return@withContext Result.Error("Adres çözümlenemedi")

                // Best city candidate
                val city = addr.city
                    ?: addr.town
                    ?: addr.village
                    ?: addr.county
                    ?: addr.state
                    ?: "Bilinmeyen"

                // Best district/suburb candidate
                val district = addr.district
                    ?: addr.cityDistrict
                    ?: addr.suburb
                    ?: addr.neighbourhood
                    ?: addr.quarter
                    ?: ""

                Result.Success(PlaceName(city = city, district = district))
            } catch (e: Exception) {
                Result.Error(e.message ?: "Reverse geocode hatası")
            }
        }

    @SuppressLint("MissingPermission")
    override fun observeLocationUpdates(intervalMs: Long): Flow<DeviceLocation> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    trySend(DeviceLocation(loc.latitude, loc.longitude, System.currentTimeMillis()))
                }
            }
        }

        try {
            fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            close(e)
        }

        awaitClose { fusedClient.removeLocationUpdates(callback) }
    }
}
