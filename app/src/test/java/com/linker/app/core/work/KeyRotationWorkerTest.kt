package com.linker.app.core.work

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.linker.app.data.encryption.EncryptionManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.security.NoSuchAlgorithmException

@OptIn(ExperimentalCoroutinesApi::class)
class KeyRotationWorkerTest {

    private lateinit var context: Context
    private lateinit var params: WorkerParameters
    private lateinit var encryptionManager: EncryptionManager

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        params = mockk(relaxed = true)
        encryptionManager = mockk(relaxed = true)
        every { params.runAttemptCount } returns 0
    }

    @Test
    fun testDoWork_success() = runTest {
        coEvery { encryptionManager.rotateKeys() } returns Unit

        val worker = KeyRotationWorker(context, params, encryptionManager)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun testDoWork_cryptoError_returnsFailureImmediately() = runTest {
        coEvery { encryptionManager.rotateKeys() } throws NoSuchAlgorithmException("Unsupported cipher algorithm")

        val worker = KeyRotationWorker(context, params, encryptionManager)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun testDoWork_networkError_returnsRetryOnFirstAttempt() = runTest {
        every { params.runAttemptCount } returns 0
        coEvery { encryptionManager.rotateKeys() } throws IOException("Peer network unavailable")

        val worker = KeyRotationWorker(context, params, encryptionManager)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }
}
