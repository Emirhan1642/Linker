package com.linker.app.data.ble

import com.linker.app.data.connectivity.ConnectivityMonitor
import com.linker.app.data.connectivity.ConnectivityState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class MessageBatcherTest {

    private lateinit var connectivityMonitor: ConnectivityMonitor
    private lateinit var connectivityFlow: MutableStateFlow<ConnectivityState>
    private lateinit var batcher: MessageBatcher

    @Before
    fun setup() {
        connectivityMonitor = mockk(relaxed = true)
        connectivityFlow = MutableStateFlow(ConnectivityState.Offline())
        every { connectivityMonitor.observeConnectivityState() } returns connectivityFlow
        batcher = MessageBatcher(connectivityMonitor)
    }

    @After
    fun tearDown() {
        batcher.shutdown()
    }

    private fun createDummyPacket(id: String = UUID.randomUUID().toString()): BLEPacket {
        return BLEPacket.create(
            messageId = id,
            senderId = "sender_1",
            recipientId = "recipient_1",
            ttl = 3,
            hopCount = 0,
            fragmentIndex = 0,
            totalFragments = 1,
            encryptedPayload = "Test Payload".toByteArray()
        )
    }

    @Test
    fun testOfflineState_flushesWhenBatchSize5Reached() = runTest {
        val receivedBatches = mutableListOf<List<BLEPacket>>()
        batcher.setOnBatchReady { receivedBatches.add(it) }

        // In offline state, batch size is 5
        for (i in 1..4) {
            batcher.addMessage(createDummyPacket("msg_$i"))
            assertEquals("Should not flush before batch size 5", 0, receivedBatches.size)
            assertEquals(i, batcher.getBatchSize())
        }

        // 5th message triggers flush
        batcher.addMessage(createDummyPacket("msg_5"))
        assertEquals(1, receivedBatches.size)
        assertEquals(5, receivedBatches[0].size)
        assertEquals(0, batcher.getBatchSize())
    }

    @Test
    fun testOnlineState_adaptiveBatchSize_flushesAtSize3() = runTest {
        // Change network state to Online
        connectivityFlow.value = ConnectivityState.Online()
        // Allow state adjustment in coroutine
        kotlinx.coroutines.delay(50)

        val receivedBatches = mutableListOf<List<BLEPacket>>()
        batcher.setOnBatchReady { receivedBatches.add(it) }

        // In online state, batch size is 3
        batcher.addMessage(createDummyPacket("msg_1"))
        batcher.addMessage(createDummyPacket("msg_2"))
        assertEquals(0, receivedBatches.size)
        assertEquals(2, batcher.getBatchSize())

        // 3rd message triggers flush
        batcher.addMessage(createDummyPacket("msg_3"))
        assertEquals(1, receivedBatches.size)
        assertEquals(3, receivedBatches[0].size)
        assertEquals(0, batcher.getBatchSize())
    }

    @Test
    fun testFlushBatch_whenCallbackNull_restoresMessagesToQueue() {
        // Add 2 messages without setting callback
        batcher.addMessage(createDummyPacket("msg_1"))
        batcher.addMessage(createDummyPacket("msg_2"))

        batcher.flushBatch()

        // Since callback was null, messages must be preserved in queue
        assertEquals(2, batcher.getBatchSize())

        // Now attach callback and flush
        val delivered = mutableListOf<List<BLEPacket>>()
        batcher.setOnBatchReady { delivered.add(it) }
        batcher.flushBatch()

        assertEquals(1, delivered.size)
        assertEquals(2, delivered[0].size)
        assertEquals(0, batcher.getBatchSize())
    }

    @Test
    fun testShutdown_clearsQueue() {
        batcher.addMessage(createDummyPacket("msg_1"))
        batcher.addMessage(createDummyPacket("msg_2"))
        assertEquals(2, batcher.getBatchSize())

        batcher.shutdown()
        assertEquals(0, batcher.getBatchSize())
    }
}
