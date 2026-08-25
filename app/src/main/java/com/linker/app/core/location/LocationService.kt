package com.linker.app.core.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class LocationVenue(
    val id: String,
    val name: String,
    val address: String,
    val category: String? = null,
    val distanceMeters: Int? = null
)

@Singleton
class LocationService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Location? = withContext(Dispatchers.IO) {
        try {
            val cts = CancellationTokenSource()
            val location = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cts.token
            ).await()
            location ?: fusedLocationClient.lastLocation.await()
        } catch (e: Exception) {
            android.util.Log.w("LocationService", "Failed to get current location: ${e.message}")
            null
        }
    }

    /**
     * Searches places & venues using Komoot Photon API (Free OpenStreetMap-based place search)
     * with local Android Geocoder fallback.
     */
    suspend fun searchPlaces(
        query: String,
        userLat: Double? = null,
        userLon: Double? = null
    ): List<LocationVenue> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val results = mutableListOf<LocationVenue>()

        try {
            val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
            val urlString = buildString {
                append("https://photon.komoot.io/api/?q=").append(encodedQuery).append("&limit=30")
                if (userLat != null && userLon != null) {
                    append("&lat=").append(userLat).append("&lon=").append(userLon)
                }
            }

            val url = URL(urlString)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("User-Agent", "LinkerApp/1.0")
            }

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val features = json.optJSONArray("features") ?: JSONArray()

                for (i in 0 until features.length()) {
                    val feature = features.getJSONObject(i)
                    val properties = feature.optJSONObject("properties") ?: continue
                    val name = properties.optString("name", "").trim()
                    if (name.isBlank()) continue

                    val city = properties.optString("city", properties.optString("county", properties.optString("state", "")))
                    val country = properties.optString("country", "")
                    val district = properties.optString("district", "")
                    val street = properties.optString("street", "")

                    val addressParts = listOf(street, district, city, country).filter { it.isNotBlank() }
                    val address = addressParts.joinToString(", ")
                    val osmKey = properties.optString("osm_key", "place")
                    val osmValue = properties.optString("osm_value", "")
                    val categoryLabel = formatCategoryLabel(osmKey, osmValue)

                    results.add(
                        LocationVenue(
                            id = properties.optString("osm_id", "osm_$i"),
                            name = name,
                            address = address,
                            category = categoryLabel
                        )
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("LocationService", "Photon search failed: ${e.message}, trying Geocoder")
        }

        // Fallback to Android native Geocoder if Photon was empty or failed
        if (results.isEmpty()) {
            try {
                if (Geocoder.isPresent()) {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocationName(query, 15) ?: emptyList()
                    addresses.forEachIndexed { idx, addr ->
                        val featureName = addr.featureName ?: addr.thoroughfare ?: addr.subLocality ?: addr.locality ?: query
                        val addressLine = addr.getAddressLine(0) ?: listOfNotNull(addr.subLocality, addr.locality, addr.adminArea, addr.countryName).joinToString(", ")
                        results.add(
                            LocationVenue(
                                id = "geo_$idx",
                                name = featureName,
                                address = addressLine,
                                category = "Mekan"
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("LocationService", "Geocoder fallback search failed: ${e.message}")
            }
        }

        results.distinctBy { "${it.name}_${it.address}" }
    }

    /**
     * Gets nearby venues, points of interest, and districts using coordinates via Overpass API & Nominatim.
     */
    suspend fun getNearbyPlaces(
        latitude: Double,
        longitude: Double
    ): List<LocationVenue> = withContext(Dispatchers.IO) {
        val venues = mutableListOf<LocationVenue>()

        // 1. Fetch real nearby POIs (cafes, restaurants, shops, parks) from OpenStreetMap Overpass API
        try {
            val overpassQuery = """[out:json][timeout:5];(node(around:2500,$latitude,$longitude)["name"]["amenity"];node(around:2500,$latitude,$longitude)["name"]["shop"];node(around:2500,$latitude,$longitude)["name"]["tourism"];node(around:2500,$latitude,$longitude)["name"]["leisure"];);out body 40;"""
            val url = URL("https://overpass-api.de/api/interpreter?data=" + URLEncoder.encode(overpassQuery, "UTF-8"))
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("User-Agent", "LinkerApp/1.0")
            }

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val elements = json.optJSONArray("elements") ?: JSONArray()

                for (i in 0 until elements.length()) {
                    val elem = elements.getJSONObject(i)
                    val tags = elem.optJSONObject("tags") ?: continue
                    val name = tags.optString("name", "").trim()
                    if (name.isBlank()) continue

                    val street = tags.optString("addr:street", "")
                    val district = tags.optString("addr:district", tags.optString("addr:suburb", tags.optString("addr:city", "")))
                    val address = listOf(street, district).filter { it.isNotBlank() }.joinToString(", ")
                    val amenity = tags.optString("amenity", tags.optString("shop", tags.optString("tourism", tags.optString("leisure", ""))))
                    val categoryLabel = formatCategoryLabel("amenity", amenity)

                    venues.add(
                        LocationVenue(
                            id = "op_${elem.optLong("id", i.toLong())}",
                            name = name,
                            address = address,
                            category = categoryLabel
                        )
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("LocationService", "Overpass POI query failed: ${e.message}")
        }

        // 2. Fetch reverse geocoded details from Nominatim (OpenStreetMap)
        try {
            val urlString = "https://nominatim.openstreetmap.org/reverse?format=json&lat=$latitude&lon=$longitude&zoom=18&addressdetails=1"
            val url = URL(urlString)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("User-Agent", "LinkerApp/1.0 (contact@linker.app)")
            }

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val addressObj = json.optJSONObject("address")

                if (addressObj != null) {
                    val road = addressObj.optString("road", "")
                    val neighbourhood = addressObj.optString("neighbourhood", addressObj.optString("suburb", ""))
                    val district = addressObj.optString("district", addressObj.optString("county", ""))
                    val city = addressObj.optString("city", addressObj.optString("province", addressObj.optString("state", "")))
                    val amenity = addressObj.optString("amenity", addressObj.optString("shop", addressObj.optString("tourism", "")))

                    if (amenity.isNotBlank()) {
                        venues.add(0, LocationVenue("nom_amenity", amenity, listOf(road, neighbourhood, district, city).filter { it.isNotBlank() }.joinToString(", "), "Mekan"))
                    }
                    if (neighbourhood.isNotBlank()) {
                        venues.add(0, LocationVenue("nom_neigh", neighbourhood, listOf(district, city).filter { it.isNotBlank() }.joinToString(", "), "Semt / Mahalle"))
                    }
                    if (district.isNotBlank()) {
                        venues.add(0, LocationVenue("nom_dist", district, city, "İlçe"))
                    }
                    if (city.isNotBlank()) {
                        venues.add(0, LocationVenue("nom_city", city, addressObj.optString("country", "Türkiye"), "Şehir"))
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("LocationService", "Nominatim reverse failed: ${e.message}")
        }

        // 3. Fallback to Android native Geocoder
        if (venues.isEmpty()) {
            try {
                if (Geocoder.isPresent()) {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(latitude, longitude, 8) ?: emptyList()
                    addresses.forEachIndexed { idx, addr ->
                        val subLoc = addr.subLocality ?: addr.thoroughfare
                        val loc = addr.locality ?: addr.adminArea
                        if (subLoc != null) {
                            venues.add(LocationVenue("geo_sub_$idx", subLoc, loc ?: "", "Semt / Mekan"))
                        }
                        if (loc != null && venues.none { it.name == loc }) {
                            venues.add(LocationVenue("geo_loc_$idx", loc, addr.countryName ?: "", "Şehir"))
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("LocationService", "Geocoder reverse failed: ${e.message}")
            }
        }

        // Add standard fallback popular landmark places if completely offline
        if (venues.isEmpty()) {
            venues.addAll(
                listOf(
                    LocationVenue("def_1", "Kadıköy", "İstanbul, Türkiye", "İlçe"),
                    LocationVenue("def_2", "Beşiktaş", "İstanbul, Türkiye", "İlçe"),
                    LocationVenue("def_3", "Kızılay", "Çankaya, Ankara", "Meydan"),
                    LocationVenue("def_4", "Alsancak", "Konak, İzmir", "Semt"),
                    LocationVenue("def_5", "Muratpaşa", "Antalya, Türkiye", "İlçe")
                )
            )
        }

        venues.distinctBy { it.name }
    }

    private fun formatCategoryLabel(key: String, value: String): String {
        val v = value.lowercase()
        return when {
            v.contains("cafe") || v.contains("coffee") -> "☕ Kafe"
            v.contains("restaurant") || v.contains("fast_food") || v.contains("food") -> "🍽️ Restoran"
            v.contains("bar") || v.contains("pub") -> "🍸 Bar / Pub"
            v.contains("park") || v.contains("garden") -> "🌳 Park"
            v.contains("mall") || v.contains("supermarket") || v.contains("shop") -> "🛍️ Mağaza / AVM"
            v.contains("museum") || v.contains("gallery") || v.contains("theatre") || v.contains("cinema") -> "🎭 Sanat & Kültür"
            v.contains("hotel") || v.contains("hostel") -> "🏨 Otel"
            v.contains("gym") || v.contains("fitness") || v.contains("sports") -> "💪 Spor"
            v.contains("hospital") || v.contains("pharmacy") -> "🏥 Sağlık"
            v.contains("university") || v.contains("school") || v.contains("college") -> "🎓 Eğitim"
            key == "place" -> "📍 Konum"
            else -> "📍 Mekan"
        }
    }
}
