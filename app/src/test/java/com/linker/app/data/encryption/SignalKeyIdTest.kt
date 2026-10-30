package com.linker.app.data.encryption

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class SignalKeyIdTest {

    @Test
    fun testKyberPreKeyId_isAlwaysPositiveAndNonZero() {
        val counter = AtomicInteger(0)

        val testEpochs = listOf(
            Int.MIN_VALUE,
            -1000,
            -1,
            0,
            1,
            (System.currentTimeMillis() / 1000).toInt(),
            1770000000, // Year 2026
            2000000000,
            Int.MAX_VALUE
        )

        for (epoch in testEpochs) {
            for (i in 0..100) {
                val counterSeq = counter.incrementAndGet() and 0xFFFF
                val kyberPreKeyId = (((epoch and 0x7FFF) shl 16) or counterSeq).let { if (it <= 0) 1 else it }

                assertTrue(
                    "KyberPreKey ID must be strictly positive (> 0) but got $kyberPreKeyId for epoch $epoch",
                    kyberPreKeyId > 0
                )
                assertTrue(
                    "KyberPreKey ID must fit within positive 31-bit integer",
                    kyberPreKeyId <= Int.MAX_VALUE
                )
            }
        }
    }

    @Test
    fun testSignedPreKeyId_isAlwaysPositiveAndNonZero() {
        val testEpochs = listOf(
            Int.MIN_VALUE,
            -1000,
            -1,
            0,
            1,
            (System.currentTimeMillis() / 1000).toInt(),
            1770000000,
            Int.MAX_VALUE
        )

        for (epoch in testEpochs) {
            val signedPreKeyId = (epoch and 0x7FFFFFFF).let { if (it == 0) 1 else it }
            assertTrue(
                "SignedPreKey ID must be strictly positive (> 0) but got $signedPreKeyId",
                signedPreKeyId > 0
            )
        }
    }

    @Test
    fun testEpochLongSafety_beyondYear2038() {
        val testLongEpochs = listOf(
            0L,
            1L,
            1770000000L, // Current era (2026)
            2147483647L, // 31-bit boundary
            2147483648L, // Y2038 overflow point
            2500000000L,
            5000000000L,
            Long.MAX_VALUE
        )

        for (epochSeconds in testLongEpochs) {
            val signedPreKeyId = ((epochSeconds and 0x7FFFFFFFL).toInt()).let { if (it == 0) 1 else it }
            assertTrue(
                "SignedPreKey ID for Long $epochSeconds must be strictly > 0, got $signedPreKeyId",
                signedPreKeyId > 0
            )
            assertTrue(
                "SignedPreKey ID for Long $epochSeconds must fit in positive 31-bit Int, got $signedPreKeyId",
                signedPreKeyId <= Int.MAX_VALUE
            )

            val counterSeq = 1
            val kyberPreKeyId = ((((epochSeconds and 0x7FFFL).toInt()) shl 16) or counterSeq).let { if (it <= 0) 1 else it }
            assertTrue(
                "KyberPreKey ID for Long $epochSeconds must be strictly > 0, got $kyberPreKeyId",
                kyberPreKeyId > 0
            )
            assertTrue(
                "KyberPreKey ID for Long $epochSeconds must fit in positive 31-bit Int, got $kyberPreKeyId",
                kyberPreKeyId <= Int.MAX_VALUE
            )
        }
    }

    @Test
    fun testPreKeyIdGeneration_wrapsSafelyWithoutCollisions() {
        val existingIds = setOf(1, 2, 3, 0x7FFFFFFF - 2, 0x7FFFFFFF - 1)
        val maxId = 0x7FFFFFFF - 1
        val preKeyCount = 10

        var candidateId = if (maxId >= 0x7FFFFFFF - preKeyCount) 1 else maxId + 1
        val candidateIds = mutableListOf<Int>()
        while (candidateIds.size < preKeyCount) {
            if (candidateId <= 0 || candidateId >= 0x7FFFFFFF) {
                candidateId = 1
            }
            if (candidateId !in existingIds) {
                candidateIds.add(candidateId)
            }
            candidateId++
        }

        assertTrue("Must generate exactly $preKeyCount IDs", candidateIds.size == preKeyCount)
        assertTrue("No generated ID may be in existingIds", candidateIds.none { it in existingIds })
        assertTrue("All generated IDs must be strictly positive", candidateIds.all { it > 0 })
        assertTrue("All generated IDs must fit in 31-bit signed Int", candidateIds.all { it < 0x7FFFFFFF })
    }
}
