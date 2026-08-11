package com.linker.app.data.remote

import com.linker.app.domain.model.PoiInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import com.linker.app.BuildConfig

object FoursquareApiClient {
    
    suspend fun fetchNearbyPOIs(latitude: Double, longitude: Double, radiusMeters: Int = 10000): List<PoiInfo> {
        return withContext(Dispatchers.IO) {
            val apiKey = BuildConfig.FOURSQUARE_API_KEY.replace("\"", "").trim()
            if (apiKey.isBlank()) {
                android.util.Log.e("FoursquareApiClient", "API Key is missing!")
                return@withContext emptyList()
            }

            // Temiz (Sosyal) Harita için seçilmiş V3 Kategori ID'leri
            val categories = listOf(
                "13000", // Yeme-İçme (Dining and Drinking)
                "10000", // Eğlence & Kültür (Arts and Entertainment)
                "16000", // Park & Doğa & Landmark (Landmarks and Outdoors)
                "17000", // Alışveriş & Market (Retail)
                "15000"  // Sağlık & Klinik (Health and Medicine)
            )

            // Asenkron olarak 5 farklı istek yolla (250 mekan kapasitesi)
            val deferredPois = categories.map { categoryId ->
                async {
                    fetchCategoryPOIs(latitude, longitude, radiusMeters, categoryId, apiKey)
                }
            }
            
            // Tüm sonuçların gelmesini bekle
            val lists = deferredPois.awaitAll()
            
            // Farklı kategorilerden gelen mekanları Popülerlik Sırasına göre (interleave / Round-Robin) harmanlayalım.
            // Çünkü Foursquare listeyi popülerliğe göre yollar. 5 ayrı listenin 1. elemanlarını (en popülerleri) başa dizelim.
            val interleavedList = mutableListOf<PoiInfo>()
            val maxLen = lists.maxOfOrNull { it.size } ?: 0
            for (i in 0 until maxLen) {
                for (list in lists) {
                    if (i < list.size) {
                        interleavedList.add(list[i])
                    }
                }
            }
            
            // Mükerrerleri (Aynı mekanın 2 farklı kategoride çıkması durumu) temizle
            val finalPois = interleavedList.distinctBy { it.id }
            android.util.Log.d("FoursquareApiClient", "Total 5-Category Harvest: ${finalPois.size} POIs generated.")
            finalPois
        }
    }

    private fun fetchCategoryPOIs(latitude: Double, longitude: Double, radiusMeters: Int, categoryId: String, apiKey: String): List<PoiInfo> {
        val pois = mutableListOf<PoiInfo>()
        try {
            // limits=50 ve category filtresi eklendi
            val urlString = "https://places-api.foursquare.com/places/search?ll=$latitude,$longitude&radius=$radiusMeters&categories=$categoryId&limit=50"
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("X-Places-Api-Version", "2025-06-17")

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(response)
                val results = jsonObject.optJSONArray("results") ?: jsonObject.optJSONArray("places")

                if (results != null) {
                    for (i in 0 until results.length()) {
                        val element = results.optJSONObject(i) ?: continue
                        val name = element.optString("name", "")
                        if (name.isBlank()) continue
                        
                        var lat = element.optDouble("latitude", 0.0)
                        var lon = element.optDouble("longitude", 0.0)
                        
                        if (lat == 0.0) {
                            val geocodes = element.optJSONObject("geocodes")
                            if (geocodes != null) {
                                val main = geocodes.optJSONObject("main")
                                if (main != null) {
                                    lat = main.optDouble("latitude", 0.0)
                                    lon = main.optDouble("longitude", 0.0)
                                }
                            }
                        }
                        
                        if (lat == 0.0 && lon == 0.0) continue
                        
                        var type = "Mekan"
                        val categoriesArr = element.optJSONArray("categories")
                        if (categoriesArr != null && categoriesArr.length() > 0) {
                            val firstCat = categoriesArr.optJSONObject(0)
                            if (firstCat != null) {
                                type = firstCat.optString("name", "Mekan")
                            }
                        }
                        
                        val fsqId = element.optString("fsq_place_id", element.optString("fsq_id", element.optString("fsqId", element.optString("id", ""))))
                        val numericId = fsqId.hashCode().toLong()
                        
                        pois.add(PoiInfo(numericId, name, type, lat, lon))
                    }
                }
            }
            connection.disconnect()
        } catch (e: Exception) {
            android.util.Log.e("FoursquareApiClient", "Cat $categoryId Error: ${e.message}")
        }
        return pois
    }
}
