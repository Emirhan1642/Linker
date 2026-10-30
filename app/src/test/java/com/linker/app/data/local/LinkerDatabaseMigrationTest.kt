package com.linker.app.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import com.linker.app.data.local.entity.DeliveryMethod
import com.linker.app.data.local.entity.LinkType
import com.linker.app.data.local.entity.MessageType
import com.linker.app.data.local.entity.QueueStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkerDatabaseMigrationTest {

    @Test
    fun migration_7_8_creates_all_expected_indices() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)

        LinkerDatabase.MIGRATION_7_8.migrate(db)

        // Verify ble_nodes table creation
        verify { db.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS ble_nodes") }) }

        // Verify all 4 required ble_nodes indices are created
        verify { db.execSQL(match { it.contains("index_ble_nodes_lastSeen") }) }
        verify { db.execSQL(match { it.contains("index_ble_nodes_isConnected") }) }
        verify { db.execSQL(match { it.contains("index_ble_nodes_deviceAddress") }) }
        verify { db.execSQL(match { it.contains("index_ble_nodes_rssi") }) }

        // Verify message_id_cache table and indices
        verify { db.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS message_id_cache") }) }
        verify { db.execSQL(match { it.contains("index_message_id_cache_receivedAt") }) }
        verify { db.execSQL(match { it.contains("index_message_id_cache_sourceNodeId") }) }
    }

    @Test
    fun migration_12_13_handles_duplicate_column_gracefully() {
        val db = mockk<SupportSQLiteDatabase>()
        every { db.execSQL(match { it.contains("lastSeen") }) } throws RuntimeException("duplicate column name: lastSeen")

        // Must not throw an unhandled exception
        LinkerDatabase.MIGRATION_12_13.migrate(db)

        verify { db.execSQL(match { it.contains("lastSeen") }) }
    }

    @Test
    fun migration_13_14_adds_columns_and_handles_duplicates() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)

        LinkerDatabase.MIGRATION_13_14.migrate(db)

        verify { db.execSQL(match { it.contains("replyToNoteJson") }) }
        verify { db.execSQL(match { it.contains("isAiGenerated") }) }
    }

    @Test
    fun migration_14_15_adds_columns_and_handles_duplicates() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)

        LinkerDatabase.MIGRATION_14_15.migrate(db)

        verify { db.execSQL(match { it.contains("isAiGenerated") }) }
        verify { db.execSQL(match { it.contains("mediaFitModes") }) }
    }

    @Test
    fun migration_15_16_adds_column_and_reconciles_indices() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)

        LinkerDatabase.MIGRATION_15_16.migrate(db)

        verify { db.execSQL(match { it.contains("mediaFitModes") }) }
        verify { db.execSQL(match { it.contains("index_ble_nodes_deviceAddress") }) }
        verify { db.execSQL(match { it.contains("index_ble_nodes_rssi") }) }
        verify { db.execSQL(match { it.contains("index_message_id_cache_sourceNodeId") }) }
    }

    @Test
    fun converters_handle_edge_cases_and_fallbacks() {
        val converters = Converters()

        // List<String>
        assertEquals(emptyList<String>(), converters.toStringList(null))
        assertEquals(emptyList<String>(), converters.toStringList(""))
        assertEquals(emptyList<String>(), converters.toStringList("invalid-json"))
        val list = listOf("tag1", "tag2")
        val jsonList = converters.fromStringList(list)
        assertNotNull(jsonList)
        assertEquals(list, converters.toStringList(jsonList))

        // List<Boolean>
        assertEquals(emptyList<Boolean>(), converters.toBooleanList(null))
        assertEquals(emptyList<Boolean>(), converters.toBooleanList("not-json"))
        val bools = listOf(true, false, true)
        val jsonBools = converters.fromBooleanList(bools)
        assertEquals(bools, converters.toBooleanList(jsonBools))

        // Map<String, String>
        assertEquals(emptyMap<String, String>(), converters.toStringMap(null))
        assertEquals(emptyMap<String, String>(), converters.toStringMap("not-json"))
        val map = mapOf("user1" to "❤️", "user2" to "👍")
        val jsonMap = converters.fromStringMap(map)
        assertEquals(map, converters.toStringMap(jsonMap))

        // Enum fallbacks on unknown string
        assertEquals(QueueStatus.PENDING, converters.toQueueStatus("UNKNOWN_STATUS"))
        assertEquals(LinkType.FEED, converters.toLinkType("INVALID_LINK_TYPE"))
        assertEquals(DeliveryMethod.ONLINE, converters.toDeliveryMethod("UNKNOWN_DELIVERY"))
        assertEquals(DeliveryMethod.ONLINE, converters.toDeliveryMethod("INTERNET"))
        assertEquals(DeliveryMethod.ONLINE, converters.toDeliveryMethod("online"))
    }
}
