package com.linker.app.data.local

import androidx.room.TypeConverter
import com.linker.app.data.local.entity.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Room Type Converters
 * 
 * Converts complex types to/from database-compatible types
 */
class Converters {
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = false
    }

    private fun logConversionError(type: String, value: String, error: Exception) {
        android.util.Log.e("Converters", "Failed to convert $type: $value", error)
    }
    
    // List<String> converters
    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return if (value.isNullOrEmpty()) null else json.encodeToString(value)
    }
    
    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString<List<String>>(value)
        } catch (e: Exception) {
            logConversionError("List<String>", value, e)
            emptyList()
        }
    }
    
    // Map<String, String> converters (for reactions)
    @TypeConverter
    fun fromStringMap(value: Map<String, String>?): String? {
        return if (value.isNullOrEmpty()) null else json.encodeToString(value)
    }
    
    @TypeConverter
    fun toStringMap(value: String?): Map<String, String> {
        if (value.isNullOrBlank()) return emptyMap()
        return try {
            json.decodeFromString<Map<String, String>>(value)
        } catch (e: Exception) {
            logConversionError("Map<String, String>", value, e)
            emptyMap()
        }
    }
    
    // LinkType converters
    @TypeConverter
    fun fromLinkType(value: LinkType): String {
        return value.name
    }
    
    @TypeConverter
    fun toLinkType(value: String): LinkType {
        return try {
            LinkType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            logConversionError("LinkType", value, e)
            LinkType.values().first()
        }
    }
    
    // StoryMediaType converters
    @TypeConverter
    fun fromStoryMediaType(value: StoryMediaType): String {
        return value.name
    }
    
    @TypeConverter
    fun toStoryMediaType(value: String): StoryMediaType {
        return try {
            StoryMediaType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            logConversionError("StoryMediaType", value, e)
            StoryMediaType.values().first()
        }
    }
    
    // NoteType converters
    @TypeConverter
    fun fromNoteType(value: NoteType): String {
        return value.name
    }
    
    @TypeConverter
    fun toNoteType(value: String): NoteType {
        return try {
            NoteType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            logConversionError("NoteType", value, e)
            NoteType.values().first()
        }
    }
    
    // ChatType converters
    @TypeConverter
    fun fromChatType(value: ChatType): String {
        return value.name
    }
    
    @TypeConverter
    fun toChatType(value: String): ChatType {
        return try {
            ChatType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            logConversionError("ChatType", value, e)
            ChatType.values().first()
        }
    }
    
    // MessageType converters
    @TypeConverter
    fun fromMessageType(value: MessageType): String {
        return value.name
    }
    
    @TypeConverter
    fun toMessageType(value: String): MessageType {
        return try {
            MessageType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            logConversionError("MessageType", value, e)
            MessageType.values().first()
        }
    }
    
    // MessageStatus converters
    @TypeConverter
    fun fromMessageStatus(value: MessageStatus): String {
        return value.name
    }
    
    @TypeConverter
    fun toMessageStatus(value: String): MessageStatus {
        return try {
            MessageStatus.valueOf(value)
        } catch (e: IllegalArgumentException) {
            logConversionError("MessageStatus", value, e)
            MessageStatus.SENT
        }
    }
    
    // DeliveryMethod converters
    @TypeConverter
    fun fromDeliveryMethod(value: DeliveryMethod): String {
        return value.name
    }
    
    @TypeConverter
    fun toDeliveryMethod(value: String): DeliveryMethod {
        return try {
            DeliveryMethod.valueOf(value)
        } catch (e: IllegalArgumentException) {
            logConversionError("DeliveryMethod", value, e)
            DeliveryMethod.values().first()
        }
    }
    
    // QueueStatus converters
    @TypeConverter
    fun fromQueueStatus(value: QueueStatus): String {
        return value.name
    }
    
    @TypeConverter
    fun toQueueStatus(value: String): QueueStatus {
        return try {
            QueueStatus.valueOf(value)
        } catch (e: IllegalArgumentException) {
            logConversionError("QueueStatus", value, e)
            QueueStatus.PENDING
        }
    }
    
    // CacheType converters
    @TypeConverter
    fun fromCacheType(value: CacheType): String {
        return value.name
    }
    
    @TypeConverter
    fun toCacheType(value: String): CacheType {
        return try {
            CacheType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            logConversionError("CacheType", value, e)
            CacheType.values().first()
        }
    }
    
    // NotificationType converters
    @TypeConverter
    fun fromNotificationType(value: NotificationType): String {
        return value.name
    }
    
    @TypeConverter
    fun toNotificationType(value: String): NotificationType {
        return try {
            NotificationType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            logConversionError("NotificationType", value, e)
            NotificationType.values().first()
        }
    }
}
