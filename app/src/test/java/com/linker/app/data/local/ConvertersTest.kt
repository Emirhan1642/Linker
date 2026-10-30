package com.linker.app.data.local

import com.linker.app.data.local.entity.CacheType
import com.linker.app.data.local.entity.ChatType
import com.linker.app.data.local.entity.DeliveryMethod
import com.linker.app.data.local.entity.LinkType
import com.linker.app.data.local.entity.MessageStatus
import com.linker.app.data.local.entity.MessageType
import com.linker.app.data.local.entity.NoteType
import com.linker.app.data.local.entity.NotificationType
import com.linker.app.data.local.entity.QueueStatus
import com.linker.app.data.local.entity.StoryMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConvertersTest {

    private lateinit var converters: Converters

    @Before
    fun setUp() {
        converters = Converters()
    }

    // --- List<String> ---

    @Test
    fun testStringList_roundTrip() {
        val original = listOf("alpha", "beta", "gamma", "türkçe_karakterler_ğüşöçİ")
        val serialized = converters.fromStringList(original)
        assertNotNull(serialized)

        val deserialized = converters.toStringList(serialized)
        assertEquals(original, deserialized)
    }

    @Test
    fun testStringList_nullAndEmpty() {
        assertNull(converters.fromStringList(null))
        assertTrue(converters.toStringList(null).isEmpty())
        assertTrue(converters.toStringList("").isEmpty())
        assertTrue(converters.toStringList("   ").isEmpty())
        assertTrue(converters.toStringList("invalid json syntax {").isEmpty())
    }

    // --- List<Boolean> ---

    @Test
    fun testBooleanList_roundTrip() {
        val original = listOf(true, false, true, true, false)
        val serialized = converters.fromBooleanList(original)
        assertNotNull(serialized)

        val deserialized = converters.toBooleanList(serialized)
        assertEquals(original, deserialized)
    }

    @Test
    fun testBooleanList_nullAndEmpty() {
        assertNull(converters.fromBooleanList(null))
        assertTrue(converters.toBooleanList(null).isEmpty())
        assertTrue(converters.toBooleanList("").isEmpty())
        assertTrue(converters.toBooleanList("not a json").isEmpty())
    }

    // --- Map<String, String> (Reactions) ---

    @Test
    fun testStringMap_roundTrip() {
        val original = mapOf("user_1" to "❤️", "user_2" to "👍", "user_3" to "🔥")
        val serialized = converters.fromStringMap(original)
        assertNotNull(serialized)

        val deserialized = converters.toStringMap(serialized)
        assertEquals(original, deserialized)
    }

    @Test
    fun testStringMap_nullAndEmpty() {
        assertNull(converters.fromStringMap(null))
        assertTrue(converters.toStringMap(null).isEmpty())
        assertTrue(converters.toStringMap("").isEmpty())
        assertTrue(converters.toStringMap("bad format").isEmpty())
    }

    // --- DeliveryMethod & Fallback ---

    @Test
    fun testDeliveryMethod_roundTripAndFallback() {
        assertEquals(DeliveryMethod.ONLINE, converters.toDeliveryMethod(converters.fromDeliveryMethod(DeliveryMethod.ONLINE)))
        assertEquals(DeliveryMethod.BLE, converters.toDeliveryMethod(converters.fromDeliveryMethod(DeliveryMethod.BLE)))
        assertEquals(DeliveryMethod.WIFI_DIRECT, converters.toDeliveryMethod(converters.fromDeliveryMethod(DeliveryMethod.WIFI_DIRECT)))

        // Fallbacks: "INTERNET" / "ONLINE"
        assertEquals(DeliveryMethod.ONLINE, converters.toDeliveryMethod("INTERNET"))
        assertEquals(DeliveryMethod.ONLINE, converters.toDeliveryMethod("internet"))
        assertEquals(DeliveryMethod.ONLINE, converters.toDeliveryMethod("ONLINE"))
        // Unknown string defaults to ONLINE
        assertEquals(DeliveryMethod.ONLINE, converters.toDeliveryMethod("UNKNOWN_METHOD"))
    }

    // --- Entity Enums ---

    @Test
    fun testLinkType_roundTripAndFallback() {
        LinkType.values().forEach { type ->
            assertEquals(type, converters.toLinkType(converters.fromLinkType(type)))
        }
        assertEquals(LinkType.values().first(), converters.toLinkType("NON_EXISTING"))
    }

    @Test
    fun testStoryMediaType_roundTripAndFallback() {
        StoryMediaType.values().forEach { type ->
            assertEquals(type, converters.toStoryMediaType(converters.fromStoryMediaType(type)))
        }
        assertEquals(StoryMediaType.values().first(), converters.toStoryMediaType("INVALID_TYPE"))
    }

    @Test
    fun testNoteType_roundTripAndFallback() {
        NoteType.values().forEach { type ->
            assertEquals(type, converters.toNoteType(converters.fromNoteType(type)))
        }
        assertEquals(NoteType.values().first(), converters.toNoteType("INVALID_NOTE_TYPE"))
    }

    @Test
    fun testChatType_roundTripAndFallback() {
        ChatType.values().forEach { type ->
            assertEquals(type, converters.toChatType(converters.fromChatType(type)))
        }
        assertEquals(ChatType.values().first(), converters.toChatType("UNKNOWN_CHAT"))
    }

    @Test
    fun testMessageType_roundTripAndFallback() {
        MessageType.values().forEach { type ->
            assertEquals(type, converters.toMessageType(converters.fromMessageType(type)))
        }
        assertEquals(MessageType.values().first(), converters.toMessageType("INVALID_MSG_TYPE"))
    }

    @Test
    fun testMessageStatus_roundTripAndFallback() {
        MessageStatus.values().forEach { status ->
            assertEquals(status, converters.toMessageStatus(converters.fromMessageStatus(status)))
        }
        assertEquals(MessageStatus.SENT, converters.toMessageStatus("UNKNOWN_STATUS"))
    }

    @Test
    fun testQueueStatus_roundTripAndFallback() {
        QueueStatus.values().forEach { status ->
            assertEquals(status, converters.toQueueStatus(converters.fromQueueStatus(status)))
        }
        assertEquals(QueueStatus.PENDING, converters.toQueueStatus("UNKNOWN_QUEUE_STATUS"))
    }

    @Test
    fun testCacheType_roundTripAndFallback() {
        CacheType.values().forEach { cache ->
            assertEquals(cache, converters.toCacheType(converters.fromCacheType(cache)))
        }
        assertEquals(CacheType.values().first(), converters.toCacheType("UNKNOWN_CACHE"))
    }

    @Test
    fun testNotificationType_roundTripAndFallback() {
        NotificationType.values().forEach { notif ->
            assertEquals(notif, converters.toNotificationType(converters.fromNotificationType(notif)))
        }
        assertEquals(NotificationType.values().first(), converters.toNotificationType("UNKNOWN_NOTIF"))
    }
}
