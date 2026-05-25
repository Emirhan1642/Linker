package com.linker.app.data.preferences

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Serializer for OfflineMessagingPreferences using kotlinx.serialization
 * 
 * This serializer handles conversion between OfflineMessagingPreferences objects
 * and JSON format for DataStore persistence.
 * 
 * **Format:** JSON (human-readable)
 * **File Location:** /data/data/com.linker.app/files/offline_messaging_preferences.pb
 * **Max File Size:** 1 MB
 * 
 * **Thread Safety:**
 * - All methods are thread-safe
 * - DataStore handles concurrent access
 * 
 * **Error Handling:**
 * - Corrupted data: Throws CorruptionException (handled by DataStore)
 * - Invalid data: Throws CorruptionException with validation error
 * - IO errors: Throws IOException
 * 
 * @see OfflineMessagingPreferences for data model
 * @see androidx.datastore.core.Serializer for interface documentation
 */
object OfflineMessagingPreferencesSerializer : Serializer<OfflineMessagingPreferences> {
    
    private const val TAG = "OfflineMessagingPreferencesSerializer"
    private const val MAX_FILE_SIZE_BYTES = 1024 * 1024 // 1 MB
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = false // Disable in production for smaller file size
        encodeDefaults = false // Don't encode default values to save space
        coerceInputValues = true
    }
    
    override val defaultValue: OfflineMessagingPreferences
        get() = OfflineMessagingPreferences()
    
    override suspend fun readFrom(input: InputStream): OfflineMessagingPreferences {
        return try {
            val bytes = input.readBytes()
            
            if (bytes.isEmpty()) {
                android.util.Log.w(TAG, "Empty preferences file, using defaults")
                return defaultValue
            }
            
            if (bytes.size > MAX_FILE_SIZE_BYTES) {
                android.util.Log.e(TAG, "Preferences file too large: ${bytes.size} bytes")
                throw CorruptionException("Preferences file too large: ${bytes.size} bytes")
            }
            
            val jsonString = bytes.decodeToString()
            
            if (jsonString.isBlank()) {
                android.util.Log.w(TAG, "Blank preferences data, using defaults")
                return defaultValue
            }
            
            android.util.Log.d(TAG, "Reading preferences (${bytes.size} bytes)")
            
            json.decodeFromString(
                OfflineMessagingPreferences.serializer(),
                jsonString
            )
        } catch (e: SerializationException) {
            android.util.Log.e(TAG, "Corrupted preferences data", e)
            throw CorruptionException("Cannot deserialize preferences", e)
        } catch (e: IllegalArgumentException) {
            android.util.Log.e(TAG, "Invalid preferences data", e)
            throw CorruptionException("Invalid preferences data", e)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Unexpected error reading preferences", e)
            throw CorruptionException("Cannot read preferences", e)
        }
    }
    
    override suspend fun writeTo(
        t: OfflineMessagingPreferences,
        output: OutputStream
    ) {
        try {
            val jsonString = json.encodeToString(
                OfflineMessagingPreferences.serializer(),
                t
            )
            val bytes = jsonString.encodeToByteArray()
            android.util.Log.d(TAG, "Writing preferences (${bytes.size} bytes)")
            
            output.write(bytes)
            output.flush()
            android.util.Log.d(TAG, "Preferences written successfully")
        } catch (e: SerializationException) {
            android.util.Log.e(TAG, "Failed to serialize preferences", e)
            throw IOException("Cannot serialize preferences", e)
        } catch (e: IOException) {
            android.util.Log.e(TAG, "Failed to write preferences to disk", e)
            throw e
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Unexpected error writing preferences", e)
            throw IOException("Cannot write preferences", e)
        }
    }
}
