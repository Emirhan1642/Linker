package com.linker.app.data.queue

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class RetryStrategyTest {

    @Test
    fun testShouldRetry_boundaryConditions() {
        assertTrue(RetryStrategy.shouldRetry(0))
        assertTrue(RetryStrategy.shouldRetry(1))
        assertTrue(RetryStrategy.shouldRetry(2))
        assertFalse(RetryStrategy.shouldRetry(3))
        assertFalse(RetryStrategy.shouldRetry(4))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testShouldRetry_negativeValue_throwsException() {
        RetryStrategy.shouldRetry(-1)
    }

    @Test
    fun testCalculateDelay_withoutJitter() {
        // baseDelay = 5000 * 3.0^retryCount
        assertEquals(5000L, RetryStrategy.calculateDelay(0, useJitter = false))
        assertEquals(15000L, RetryStrategy.calculateDelay(1, useJitter = false))
        assertEquals(45000L, RetryStrategy.calculateDelay(2, useJitter = false))
        // Max retries reached or exceeded:
        assertEquals(0L, RetryStrategy.calculateDelay(3, useJitter = false))
        assertEquals(0L, RetryStrategy.calculateDelay(4, useJitter = false))
        assertEquals(0L, RetryStrategy.calculateDelay(-1, useJitter = false))
    }

    @Test
    fun testCalculateDelay_withJitter_respectsBounds() {
        for (retryCount in 0..2) {
            val baseDelay = (5000L * Math.pow(3.0, retryCount.toDouble())).toLong()
            val minExpected = (baseDelay / 2).coerceIn(0L, RetryStrategy.MAX_DELAY)
            val maxExpected = (baseDelay + baseDelay / 2).coerceIn(0L, RetryStrategy.MAX_DELAY)

            for (i in 0..50) {
                val delay = RetryStrategy.calculateDelay(retryCount, useJitter = true)
                assertTrue("Delay $delay should be >= $minExpected", delay >= minExpected)
                assertTrue("Delay $delay should be <= $maxExpected", delay <= maxExpected)
                assertTrue("Delay $delay must never exceed MAX_DELAY", delay <= RetryStrategy.MAX_DELAY)
            }
        }
    }

    @Test
    fun testRetryWithBackoff_succeedsImmediately() = runTest {
        var callCount = 0
        val result = RetryStrategy.retryWithBackoff(maxRetries = 3) { attempt ->
            callCount++
            "success_$attempt"
        }

        assertEquals("success_0", result)
        assertEquals(1, callCount)
    }

    @Test
    fun testRetryWithBackoff_succeedsAfterTransientFailure() = runTest {
        var callCount = 0
        val retriedAttempts = mutableListOf<Int>()

        val result = RetryStrategy.retryWithBackoff(
            maxRetries = 3,
            onRetry = { attempt, _, _ -> retriedAttempts.add(attempt) }
        ) { attempt ->
            callCount++
            if (attempt < 2) {
                throw IOException("Network glitch attempt $attempt")
            }
            "recovered"
        }

        assertEquals("recovered", result)
        assertEquals(3, callCount)
        assertEquals(listOf(0, 1), retriedAttempts)
    }

    @Test
    fun testRetryWithBackoff_nonRetryableException_abortsImmediately() = runTest {
        var callCount = 0
        try {
            RetryStrategy.retryWithBackoff(maxRetries = 3) { attempt ->
                callCount++
                throw IllegalArgumentException("Non-retryable argument error")
            }
            fail("Expected IllegalArgumentException was not thrown")
        } catch (e: IllegalArgumentException) {
            assertEquals("Non-retryable argument error", e.message)
            assertEquals(1, callCount) // Did not retry
        }
    }

    @Test
    fun testRetryWithBackoff_exhaustsAllRetries_throwsLastException() = runTest {
        var callCount = 0
        try {
            RetryStrategy.retryWithBackoff(maxRetries = 2) { attempt ->
                callCount++
                throw IOException("Persistent error $attempt")
            }
            fail("Expected IOException was not thrown")
        } catch (e: IOException) {
            assertEquals("Persistent error 2", e.message)
            assertEquals(3, callCount) // attempt 0, 1, 2
        }
    }
}
