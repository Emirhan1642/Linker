package com.linker.app.data.ble

import com.linker.app.data.local.dao.BleNodeDao
import com.linker.app.data.local.entity.BleNodeEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BLERoutingTableTest {

    private lateinit var bleNodeDao: BleNodeDao
    private lateinit var routingTable: BLERoutingTable

    @Before
    fun setup() {
        bleNodeDao = mockk(relaxed = true)
        routingTable = BLERoutingTable(bleNodeDao)
    }

    @Test
    fun testCalculateRouteQuality_weightsAndBounds() {
        // MAX_RSSI = -30, MIN_RSSI = -90
        // Quality = 0.7 * normalizedRssi + 0.3 * (1 / hopCount)

        // Best signal (-30 dBm) and direct hop (1) -> 0.7 * 1.0 + 0.3 * 1.0 = 1.0
        val bestQuality = routingTable.calculateRouteQuality(rssi = -30, hopCount = 1)
        assertEquals(1.0f, bestQuality, 0.001f)

        // Weakest signal (-90 dBm) and 1 hop -> 0.7 * 0.0 + 0.3 * 1.0 = 0.3
        val weakQuality = routingTable.calculateRouteQuality(rssi = -90, hopCount = 1)
        assertEquals(0.3f, weakQuality, 0.001f)

        // Mid signal (-60 dBm) -> normalized = (-60 - (-90)) / 60 = 0.5
        // 2 hops -> penalty = 0.5 -> 0.7 * 0.5 + 0.3 * 0.5 = 0.35 + 0.15 = 0.5
        val midQuality = routingTable.calculateRouteQuality(rssi = -60, hopCount = 2)
        assertEquals(0.5f, midQuality, 0.001f)

        // Out of bounds RSSI should be clamped
        val outOfBoundsHigh = routingTable.calculateRouteQuality(rssi = -10, hopCount = 1)
        assertEquals(1.0f, outOfBoundsHigh, 0.001f)

        val outOfBoundsLow = routingTable.calculateRouteQuality(rssi = -120, hopCount = 1)
        assertEquals(0.3f, outOfBoundsLow, 0.001f)
    }

    @Test
    fun testAddRoute_insertsNewRouteAndCaches() = runTest {
        val nodeId = "user_node_1"
        val mac = "AA:BB:CC:DD:EE:01"
        val now = System.currentTimeMillis()

        coEvery { bleNodeDao.getNodeById(nodeId) } returns null
        coEvery { bleNodeDao.getNodeByAddress(mac) } returns null

        routingTable.addRoute(
            nodeId = nodeId,
            deviceAddress = mac,
            rssi = -50,
            hopCount = 1,
            timestamp = now
        )

        // Verify inserted into database
        coVerify(exactly = 1) {
            bleNodeDao.insertNode(match {
                it.nodeId == nodeId && it.deviceAddress == mac && it.rssi == -50
            })
        }

        // Verify retrieval returns from in-memory cache without hitting DB again
        val route = routingTable.getRoute(nodeId)
        assertNotNull(route)
        assertEquals(nodeId, route?.nodeId)
        assertEquals(mac, route?.deviceAddress)
        assertEquals(-50, route?.rssi)
    }

    @Test
    fun testGetRoute_returnsNullWhenStale() = runTest {
        val nodeId = "user_stale"
        val mac = "AA:BB:CC:DD:EE:02"
        val staleTimestamp = System.currentTimeMillis() - 70_000L // > 60s threshold

        val staleEntity = BleNodeEntity(
            nodeId = nodeId,
            deviceAddress = mac,
            deviceName = "OldDevice",
            rssi = -70,
            lastSeen = staleTimestamp,
            isConnected = false,
            hopCount = 1,
            routeQuality = 0.5f,
            createdAt = staleTimestamp,
            updatedAt = staleTimestamp
        )

        coEvery { bleNodeDao.getNodeById(nodeId) } returns staleEntity

        val route = routingTable.getRoute(nodeId)
        assertNull("Stale route should return null", route)
    }

    @Test
    fun testAddRoute_rpaAddressChange_cleansUpTempNode() = runTest {
        val nodeId = "user_rpa_test"
        val oldMac = "11:22:33:44:55:01"
        val newMac = "11:22:33:44:55:02"
        val now = System.currentTimeMillis()

        val existingNode = BleNodeEntity(
            nodeId = nodeId,
            deviceAddress = oldMac,
            deviceName = "Alice",
            rssi = -70,
            lastSeen = now - 5000L,
            isConnected = false,
            hopCount = 1,
            routeQuality = 0.5f,
            createdAt = now - 10000L,
            updatedAt = now - 5000L
        )

        val tempNodeWithNewMac = BleNodeEntity(
            nodeId = newMac,
            deviceAddress = newMac,
            deviceName = null,
            rssi = -60,
            lastSeen = now,
            isConnected = false,
            hopCount = 1,
            routeQuality = 0.6f,
            createdAt = now,
            updatedAt = now
        )

        coEvery { bleNodeDao.getNodeById(nodeId) } returns existingNode
        coEvery { bleNodeDao.getNodeById(newMac) } returns tempNodeWithNewMac

        routingTable.addRoute(
            nodeId = nodeId,
            deviceAddress = newMac,
            rssi = -55,
            hopCount = 1,
            timestamp = now
        )

        // Verify temporary node with newMac as nodeId is deleted to avoid UNIQUE constraint crash
        coVerify(atLeast = 1) { bleNodeDao.deleteNode(tempNodeWithNewMac) }
        coVerify(exactly = 1) { bleNodeDao.updateNode(match { it.deviceAddress == newMac && it.nodeId == nodeId }) }

        val route = routingTable.getRoute(nodeId)
        assertEquals(newMac, route?.deviceAddress)
    }

    @Test
    fun testRemoveStaleRoutes_callsDaoAndEvictsMemoryCache() = runTest {
        val now = System.currentTimeMillis()
        val nodeId1 = "active_node"
        val nodeId2 = "stale_node"
        val mac1 = "AA:BB:CC:DD:EE:10"
        val mac2 = "AA:BB:CC:DD:EE:20"

        coEvery { bleNodeDao.getNodeById(any()) } returns null
        coEvery { bleNodeDao.getNodeByAddress(any()) } returns null
        coEvery { bleNodeDao.deleteStaleNodes(any()) } returns 1

        // Add active route
        routingTable.addRoute(nodeId1, mac1, -50, 1, timestamp = now)
        // Add stale route directly by mocking timestamp in the past
        routingTable.addRoute(nodeId2, mac2, -60, 1, timestamp = now - 80_000L)

        val removedCount = routingTable.removeStaleRoutes()
        assertEquals(1, removedCount)

        // Active node should still be available in cache
        assertNotNull(routingTable.getRoute(nodeId1))
        // Stale node should be evicted
        assertNull(routingTable.getRoute(nodeId2))
    }
}
