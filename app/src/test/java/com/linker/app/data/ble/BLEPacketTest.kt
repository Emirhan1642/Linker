package com.linker.app.data.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class BLEPacketTest {

    @Test
    fun testSerializeAndDeserialize_matchesOriginal() {
        val messageId = UUID.randomUUID().toString()
        val senderId = "sender_alice_123"
        val recipientId = "recipient_bob_456"
        val payload = "Hello Linker Offline BLE Mesh!".toByteArray(Charsets.UTF_8)

        val packet = BLEPacket.create(
            messageId = messageId,
            senderId = senderId,
            recipientId = recipientId,
            ttl = 5,
            hopCount = 1,
            fragmentIndex = 0,
            totalFragments = 1,
            encryptedPayload = payload
        )

        val serialized = BLEPacket.serialize(packet)
        assertNotNull(serialized)
        assertTrue(serialized.size >= BLEPacket.HEADER_SIZE)

        val deserialized = BLEPacket.deserialize(serialized)
        assertEquals(packet.version, deserialized.version)
        assertEquals(packet.messageId, deserialized.messageId)
        assertEquals(packet.senderId, deserialized.senderId)
        assertEquals(packet.recipientId, deserialized.recipientId)
        assertEquals(packet.ttl, deserialized.ttl)
        assertEquals(packet.hopCount, deserialized.hopCount)
        assertEquals(packet.fragmentIndex, deserialized.fragmentIndex)
        assertEquals(packet.totalFragments, deserialized.totalFragments)
        assertEquals(packet.payloadLength, deserialized.payloadLength)
        assertArrayEquals(packet.encryptedPayload, deserialized.encryptedPayload)
        assertEquals(packet.checksum, deserialized.checksum)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testDeserialize_corruptedChecksum_throwsException() {
        val packet = BLEPacket.create(
            messageId = UUID.randomUUID().toString(),
            senderId = "sender_1",
            recipientId = "recipient_2",
            ttl = 3,
            hopCount = 0,
            fragmentIndex = 0,
            totalFragments = 1,
            encryptedPayload = "Test Payload".toByteArray(Charsets.UTF_8)
        )

        val serialized = BLEPacket.serialize(packet)
        // Corrupt a byte in the payload
        serialized[BLEPacket.HEADER_SIZE + 2] = (serialized[BLEPacket.HEADER_SIZE + 2] + 1).toByte()

        // Deserializing corrupted data must fail checksum validation
        BLEPacket.deserialize(serialized)
    }

    @Test
    fun testEncodeIdToBytes_multiByteUtf8_preventsCharacterSplitting() {
        // String that has multibyte Turkish characters right around the 36-byte mark:
        // "kullanıcı_özel_şifreli_bağlantı_kimliği_12345"
        val longMultiByteId = "kullanıcı_özel_şifreli_bağlantı_kimliği_12345"
        
        val packet = BLEPacket.create(
            messageId = UUID.randomUUID().toString(),
            senderId = longMultiByteId,
            recipientId = "recipient_test",
            ttl = 4,
            hopCount = 0,
            fragmentIndex = 0,
            totalFragments = 1,
            encryptedPayload = "Hello".toByteArray()
        )

        val serialized = BLEPacket.serialize(packet)
        val deserialized = BLEPacket.deserialize(serialized)

        // Decoded ID must not contain corrupted replacement character '\uFFFD'
        assertFalse("Decoded senderId must not contain replacement character", deserialized.senderId.contains('\uFFFD'))
        assertTrue(deserialized.senderId.isNotEmpty())
    }

    @Test
    fun testCopyAndRecalculateChecksum_updatesChecksumCorrectly() {
        val originalPacket = BLEPacket.create(
            messageId = UUID.randomUUID().toString(),
            senderId = "sender_alice",
            recipientId = "recipient_bob",
            ttl = 5,
            hopCount = 0,
            encryptedPayload = "Test Payload For Checksum".toByteArray(Charsets.UTF_8)
        )

        // Increment hopCount and decrement TTL as in mesh packet forwarding
        val forwardedPacket = originalPacket.copyAndRecalculateChecksum(
            ttl = 4,
            hopCount = 1
        )

        // Checksum must be valid for the forwarded packet
        assertTrue("Forwarded packet must have valid recalculated checksum", BLEPacket.validateChecksum(forwardedPacket))
        
        // Serialize and deserialize must succeed without checksum error
        val serialized = BLEPacket.serialize(forwardedPacket)
        val deserialized = BLEPacket.deserialize(serialized)
        assertEquals(4.toByte(), deserialized.ttl)
        assertEquals(1.toByte(), deserialized.hopCount)
    }

    @Test
    fun testPacketFragmenter_largePayload_fragmentsAndReassemblesCorrectly() {
        val fragmenter = PacketFragmenter()
        val largePayload = ByteArray(1200) { (it % 256).toByte() }
        val messageId = UUID.randomUUID().toString()

        val fragments = fragmenter.fragment(
            messageId = messageId,
            senderId = "sender_large",
            recipientId = "recipient_large",
            ttl = 3,
            encryptedPayload = largePayload,
            hopCount = 1
        )

        assertEquals(4, fragments.size) // 1200 / 391 = 3 + 1 = 4 fragments
        assertTrue("Fragments set must be complete", fragmenter.isComplete(fragments))

        // Serialize and deserialize all fragments
        val processedFragments = fragments.map { fragment ->
            val serialized = BLEPacket.serialize(fragment)
            BLEPacket.deserialize(serialized)
        }

        val reassembled = fragmenter.reassemble(processedFragments)
        assertArrayEquals("Reassembled payload must exactly match original large payload", largePayload, reassembled)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testDeserialize_invalidTotalFragments_throwsException() {
        val packet = BLEPacket.create(
            messageId = UUID.randomUUID().toString(),
            senderId = "sender_1",
            recipientId = "recipient_2",
            ttl = 3,
            hopCount = 0,
            fragmentIndex = 0,
            totalFragments = 1,
            encryptedPayload = "Payload".toByteArray(Charsets.UTF_8)
        )
        val serialized = BLEPacket.serialize(packet)
        // Set totalFragments (at offset 1 + 36 + 36 + 36 + 1 + 1 + 2 = 113) to 0
        serialized[113] = 0
        serialized[114] = 0
        BLEPacket.deserialize(serialized)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testDeserialize_fragmentIndexExceedsTotalFragments_throwsException() {
        val packet = BLEPacket.create(
            messageId = UUID.randomUUID().toString(),
            senderId = "sender_1",
            recipientId = "recipient_2",
            ttl = 3,
            hopCount = 0,
            fragmentIndex = 0,
            totalFragments = 1,
            encryptedPayload = "Payload".toByteArray(Charsets.UTF_8)
        )
        val serialized = BLEPacket.serialize(packet)
        // Set fragmentIndex (at offset 111) to 5 while totalFragments is 1
        serialized[111] = 0
        serialized[112] = 5
        BLEPacket.deserialize(serialized)
    }
}
