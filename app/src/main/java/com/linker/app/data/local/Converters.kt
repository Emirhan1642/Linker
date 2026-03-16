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
    }
    
    // List<String> converters
    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return value?.let { json.encodeToString(it) }
    }
    
    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        return value?.let { json.decodeFromString(it) }
    }
    
    // Map<String, String> converters (for reactions)
    @TypeConverter
    fun fromStringMap(value: Map<String, String>?): String? {
        return value?.let { json.encodeToString(it) }
    }
    
    @TypeConverter
    fun toStringMap(value: String?): Map<String, String>? {
        return value?.let { json.decodeFromString(it) }
    }
    
    // LinkType converters
    @TypeConverter
    fun fromLinkType(value: LinkType): String {
        return value.name
    }
    
    @TypeConverter
    fun toLinkType(value: String): LinkType {
        return LinkType.valueOf(value)
    }
    
    // StoryMediaType converters
    @TypeConverter
    fun fromStoryMediaType(value: StoryMediaType): String {
        return value.name
    }
    
    @TypeConverter
    fun toStoryMediaType(value: String): StoryMediaType {
        return StoryMediaType.valueOf(value)
    }
    
    // NoteType converters
    @TypeConverter
    fun fromNoteType(value: NoteType): String {
        return value.name
    }
    
    @TypeConverter
    fun toNoteType(value: String): NoteType {
        return NoteType.valueOf(value)
    }
    
    // ChatType converters
    @TypeConverter
    fun fromChatType(value: ChatType): String {
        return value.name
    }
    
    @TypeConverter
    fun toChatType(value: String): ChatType {
        return ChatType.valueOf(value)
    }
    
    // MessageType converters
    @TypeConverter
    fun fromMessageType(value: MessageType): String {
        return value.name
    }
    
    @TypeConverter
    fun toMessageType(value: String): MessageType {
        return MessageType.valueOf(value)
    }
    
    // MessageStatus converters
    @TypeConverter
    fun fromMessageStatus(value: MessageStatus): String {
        return value.name
    }
    
    @TypeConverter
    fun toMessageStatus(value: String): MessageStatus {
        return MessageStatus.valueOf(value)
    }
    
    // DeliveryMethod converters
    @TypeConverter
    fun fromDeliveryMethod(value: DeliveryMethod): String {
        return value.name
    }
    
    @TypeConverter
    fun toDeliveryMethod(value: String): DeliveryMethod {
        return DeliveryMethod.valueOf(value)
    }
    
    // QueueStatus converters
    @TypeConverter
    fun fromQueueStatus(value: QueueStatus): String {
        return value.name
    }
    
    @TypeConverter
    fun toQueueStatus(value: String): QueueStatus {
        return QueueStatus.valueOf(value)
    }
    
    // CacheType converters
    @TypeConverter
    fun fromCacheType(value: CacheType): String {
        return value.name
    }
    
    @TypeConverter
    fun toCacheType(value: String): CacheType {
        return CacheType.valueOf(value)
    }
    
    // NotificationType converters
    @TypeConverter
    fun fromNotificationType(value: NotificationType): String {
        return value.name
    }
    
    @TypeConverter
    fun toNotificationType(value: String): NotificationType {
        return NotificationType.valueOf(value)
    }
}
