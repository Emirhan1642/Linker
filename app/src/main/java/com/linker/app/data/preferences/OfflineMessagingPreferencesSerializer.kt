package com.linker.app.data.preferences

import androidx.datastore.core.Serializer
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/**
 * Serializer for OfflineMessagingPreferences using kotlinx.serialization
 * 
 * Handles serialization and deserialization of preferences to/from DataStore
 */
object OfflineMessagingPreferencesSerializer : Serializer<OfflineMessagingPreferences> {
    
    override val defaultValue: OfflineMessagingPreferences
        get() = OfflineMessagingPreferences()
    
    override suspend fun readFrom(input: InputStream): OfflineMessagingPreferences {
        return try {
            Json.decodeFromString(
                OfflineMessagingPreferences.serializer(),
                input.readBytes().decodeToString()
            )
        } catch (e: Exception) {
            defaultValue
        }
    }
    
    override suspend fun writeTo(
        t: OfflineMessagingPreferences,
        output: OutputStream
    ) {
        output.write(
            Json.encodeToString(
                OfflineMessagingPreferences.serializer(),
                t
            ).encodeToByteArray()
        )
    }
}
