package com.linker.app.data.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class PacketFragmenterTest {

    private val fragmenter = PacketFragmenter()

    @Test
    fun testFragmentAndReassemble_withVaryingTtlAcrossMesh() {
        val messageId = UUID.randomUUID().toString()
        val senderId = "sender_user_123"
        val recipientId = "recipient_user_456"

        // Generate 800-byte test payload (requires 3 fragments: 391 + 391 + 18)
        val originalPayload = ByteArray(800) { (it % 256).toByte() }

        val fragments = fragmenter.fragment(
            messageId = messageId,
            senderId = senderId,
            recipientId = recipientId,
            ttl = 5,
            encryptedPayload = originalPayload
        )

        assertEquals(3, fragments.size)
        assertTrue(fragmenter.isComplete(fragments))

        // Simulate fragments traversing different mesh relay paths with varying remaining TTLs
        val routedFragments = listOf(
            fragments[0].copy(ttl = 4, hopCount = 1),
            fragments[1].copy(ttl = 2, hopCount = 3),
            fragments[2].copy(ttl = 3, hopCount = 2)
        )

        val reassembled = fragmenter.reassemble(routedFragments)
        assertArrayEquals(originalPayload, reassembled)
    }
}
