package com.linker.app.data.ble

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class FragmentManagerTest {

    private lateinit var scope: CoroutineScope
    private lateinit var fragmentManager: FragmentManager

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        fragmentManager = FragmentManager(
            fragmentTimeout = 1000L,
            coroutineScope = scope
        )
    }

    @After
    fun tearDown() {
        fragmentManager.shutdown()
        scope.cancel()
    }

    @Test
    fun testAddSingleFragment_returnsPayloadImmediately() = runTest {
        val messageId = UUID.randomUUID().toString()
        val originalPayload = "Single fragment message payload".toByteArray(Charsets.UTF_8)

        val fragment = BLEPacket.create(
            messageId = messageId,
            senderId = "sender_1",
            recipientId = "recipient_1",
            ttl = 3,
            hopCount = 0,
            fragmentIndex = 0,
            totalFragments = 1,
            encryptedPayload = originalPayload
        )

        val result = fragmentManager.addFragment(fragment)

        assertNotNull(result)
        assertArrayEquals(originalPayload, result)
        // Once complete, message is removed from incomplete store
        assertFalse(fragmentManager.hasFragments(messageId))
        assertEquals(0, fragmentManager.getIncompleteMessageCount())
    }

    @Test
    fun testAddMultipleFragments_inOrder_reassemblesOnLastFragment() = runTest {
        val messageId = UUID.randomUUID().toString()
        val part1 = "Hello ".toByteArray(Charsets.UTF_8)
        val part2 = "Linker ".toByteArray(Charsets.UTF_8)
        val part3 = "Mesh!".toByteArray(Charsets.UTF_8)
        val expectedFull = "Hello Linker Mesh!".toByteArray(Charsets.UTF_8)

        val f0 = BLEPacket.create(
            messageId = messageId,
            senderId = "sender_alice",
            recipientId = "recipient_bob",
            ttl = 5,
            fragmentIndex = 0,
            totalFragments = 3,
            encryptedPayload = part1
        )
        val f1 = BLEPacket.create(
            messageId = messageId,
            senderId = "sender_alice",
            recipientId = "recipient_bob",
            ttl = 5,
            fragmentIndex = 1,
            totalFragments = 3,
            encryptedPayload = part2
        )
        val f2 = BLEPacket.create(
            messageId = messageId,
            senderId = "sender_alice",
            recipientId = "recipient_bob",
            ttl = 5,
            fragmentIndex = 2,
            totalFragments = 3,
            encryptedPayload = part3
        )

        // First fragment: incomplete
        val res0 = fragmentManager.addFragment(f0)
        assertNull(res0)
        assertTrue(fragmentManager.hasFragments(messageId))
        assertEquals(1, fragmentManager.getFragmentCount(messageId))

        // Second fragment: incomplete
        val res1 = fragmentManager.addFragment(f1)
        assertNull(res1)
        assertEquals(2, fragmentManager.getFragmentCount(messageId))

        // Third fragment: complete
        val res2 = fragmentManager.addFragment(f2)
        assertNotNull(res2)
        assertArrayEquals(expectedFull, res2)

        // Store cleaned up
        assertFalse(fragmentManager.hasFragments(messageId))
        assertEquals(0, fragmentManager.getIncompleteMessageCount())
    }

    @Test
    fun testAddMultipleFragments_outOfOrder_reassemblesCorrectly() = runTest {
        val messageId = UUID.randomUUID().toString()
        val part1 = "AAA".toByteArray(Charsets.UTF_8)
        val part2 = "BBB".toByteArray(Charsets.UTF_8)
        val part3 = "CCC".toByteArray(Charsets.UTF_8)
        val expected = "AAABBBCCC".toByteArray(Charsets.UTF_8)

        val f0 = BLEPacket.create(messageId = messageId, senderId = "s", recipientId = "r", ttl = 3, fragmentIndex = 0, totalFragments = 3, encryptedPayload = part1)
        val f1 = BLEPacket.create(messageId = messageId, senderId = "s", recipientId = "r", ttl = 3, fragmentIndex = 1, totalFragments = 3, encryptedPayload = part2)
        val f2 = BLEPacket.create(messageId = messageId, senderId = "s", recipientId = "r", ttl = 3, fragmentIndex = 2, totalFragments = 3, encryptedPayload = part3)

        // Arrive in order: 2, 0, 1
        assertNull(fragmentManager.addFragment(f2))
        assertNull(fragmentManager.addFragment(f0))
        assertEquals(2, fragmentManager.getFragmentCount(messageId))

        val result = fragmentManager.addFragment(f1)
        assertNotNull(result)
        assertArrayEquals(expected, result)
        assertFalse(fragmentManager.hasFragments(messageId))
    }

    @Test
    fun testAddDuplicateFragment_doesNotDuplicate() = runTest {
        val messageId = UUID.randomUUID().toString()
        val fragment = BLEPacket.create(
            messageId = messageId,
            senderId = "s",
            recipientId = "r",
            ttl = 3,
            fragmentIndex = 0,
            totalFragments = 2,
            encryptedPayload = "Part 1".toByteArray()
        )

        fragmentManager.addFragment(fragment)
        assertEquals(1, fragmentManager.getFragmentCount(messageId))

        // Duplicate
        fragmentManager.addFragment(fragment)
        assertEquals(1, fragmentManager.getFragmentCount(messageId))
    }

    @Test
    fun testClearAll_clearsIncompleteFragments() = runTest {
        val id1 = UUID.randomUUID().toString()
        val id2 = UUID.randomUUID().toString()

        val f1 = BLEPacket.create(messageId = id1, senderId = "s", recipientId = "r", ttl = 3, fragmentIndex = 0, totalFragments = 2, encryptedPayload = "P1".toByteArray())
        val f2 = BLEPacket.create(messageId = id2, senderId = "s", recipientId = "r", ttl = 3, fragmentIndex = 0, totalFragments = 2, encryptedPayload = "P2".toByteArray())

        fragmentManager.addFragment(f1)
        fragmentManager.addFragment(f2)
        assertEquals(2, fragmentManager.getIncompleteMessageCount())

        fragmentManager.clearAll()
        assertEquals(0, fragmentManager.getIncompleteMessageCount())
        assertFalse(fragmentManager.hasFragments(id1))
        assertFalse(fragmentManager.hasFragments(id2))
    }

    @Test
    fun testEviction_whenMaxConcurrentMessagesReached() = runTest {
        // Add 100 incomplete messages
        val firstMessageId = "msg_000"
        val firstFragment = BLEPacket.create(
            messageId = firstMessageId,
            senderId = "s",
            recipientId = "r",
            ttl = 3,
            fragmentIndex = 0,
            totalFragments = 2,
            encryptedPayload = "first".toByteArray()
        )
        fragmentManager.addFragment(firstFragment)

        for (i in 1 until 100) {
            val f = BLEPacket.create(
                messageId = "msg_${i.toString().padStart(3, '0')}",
                senderId = "s",
                recipientId = "r",
                ttl = 3,
                fragmentIndex = 0,
                totalFragments = 2,
                encryptedPayload = "payload".toByteArray()
            )
            fragmentManager.addFragment(f)
        }
        assertEquals(100, fragmentManager.getIncompleteMessageCount())
        assertTrue(fragmentManager.hasFragments(firstMessageId))

        // Adding 101st message should trigger eviction of the oldest
        val overflowMessageId = "msg_overflow"
        val overflowFragment = BLEPacket.create(
            messageId = overflowMessageId,
            senderId = "s",
            recipientId = "r",
            ttl = 3,
            fragmentIndex = 0,
            totalFragments = 2,
            encryptedPayload = "overflow".toByteArray()
        )
        fragmentManager.addFragment(overflowFragment)

        // Store size should remain capped at 100
        assertEquals(100, fragmentManager.getIncompleteMessageCount())
        assertTrue(fragmentManager.hasFragments(overflowMessageId))
        // Exactly one of the initial 100 messages must have been evicted
        val remainingInitial = (0 until 100).count {
            fragmentManager.hasFragments("msg_${it.toString().padStart(3, '0')}")
        }
        assertEquals("Exactly one message must be evicted to keep capacity at 100", 99, remainingInitial)
    }
}
