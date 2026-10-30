package com.linker.app.data.preferences

import androidx.datastore.core.CorruptionException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineMessagingPreferencesSerializerTest {

    private val serializer = OfflineMessagingPreferencesSerializer

    @Test
    fun testDefaultValue_hasExpectedDefaults() {
        val defaults = serializer.defaultValue
        assertTrue(defaults.isBleEnabled)
        assertTrue(defaults.isWifiDirectEnabled)
        assertEquals(5, defaults.maxTtl)
        assertTrue(defaults.showNotification)
        assertFalse(defaults.enableLogging)
        assertEquals("INFO", defaults.logLevel)
        assertEquals(0L, defaults.lastUpdatedAt)
    }

    @Test
    fun testReadFrom_emptyStream_returnsDefaultValue() = runTest {
        val emptyStream = ByteArrayInputStream(ByteArray(0))
        val result = serializer.readFrom(emptyStream)
        assertEquals(serializer.defaultValue, result)
    }

    @Test
    fun testReadFrom_blankStringStream_returnsDefaultValue() = runTest {
        val blankStream = ByteArrayInputStream("   \n\t  ".toByteArray())
        val result = serializer.readFrom(blankStream)
        assertEquals(serializer.defaultValue, result)
    }

    @Test
    fun testRoundTrip_serializationAndDeserialization() = runTest {
        val original = OfflineMessagingPreferences(
            isBleEnabled = false,
            maxTtl = 8,
            isWifiDirectEnabled = true,
            showNotification = false,
            enableLogging = true,
            logLevel = "DEBUG",
            lastUpdatedAt = 1770000000000L
        )

        val outputStream = ByteArrayOutputStream()
        serializer.writeTo(original, outputStream)

        val inputStream = ByteArrayInputStream(outputStream.toByteArray())
        val deserialized = serializer.readFrom(inputStream)

        assertEquals(original, deserialized)
    }

    @Test
    fun testReadFrom_corruptedJson_throwsCorruptionException() = runTest {
        val invalidJson = "{ corrupted json: [invalid "
        val inputStream = ByteArrayInputStream(invalidJson.toByteArray())

        try {
            serializer.readFrom(inputStream)
            fail("Expected CorruptionException was not thrown")
        } catch (e: CorruptionException) {
            assertTrue(e.message?.contains("preferences") == true)
        }
    }

    @Test
    fun testCreateSafe_clampsTtlAndValidatesLogLevel() {
        val clampedLow = OfflineMessagingPreferences.createSafe(maxTtl = -5, logLevel = "INVALID_LEVEL")
        assertEquals(1, clampedLow.maxTtl)
        assertEquals("INFO", clampedLow.logLevel)

        val clampedHigh = OfflineMessagingPreferences.createSafe(maxTtl = 99, logLevel = "ERROR")
        assertEquals(10, clampedHigh.maxTtl)
        assertEquals("ERROR", clampedHigh.logLevel)
    }
}
