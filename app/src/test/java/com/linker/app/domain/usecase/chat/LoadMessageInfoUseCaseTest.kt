package com.linker.app.domain.usecase.chat

import com.linker.app.core.util.Result
import com.linker.app.domain.model.Message
import com.linker.app.domain.repository.MessageRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoadMessageInfoUseCaseTest {

    private lateinit var messageRepository: MessageRepository
    private lateinit var useCase: LoadMessageInfoUseCase

    @Before
    fun setUp() {
        messageRepository = mockk(relaxed = true)
        useCase = LoadMessageInfoUseCase(messageRepository)
    }

    @Test
    fun testInvoke_blankMessageId_returnsError() = runTest {
        val result = useCase("   ")
        assertTrue(result is Result.Error)
        assertEquals("Message ID cannot be empty", (result as Result.Error).message)
    }

    @Test
    fun testInvoke_messageNotFound_returnsError() = runTest {
        coEvery { messageRepository.getMessageById("msg_999") } returns Result.Error("Message not found in DB")

        val result = useCase("msg_999")
        assertTrue(result is Result.Error)
        assertEquals("Message not found", (result as Result.Error).message)
    }

    @Test
    fun testInvoke_success_combinesReactionsAndReceipts() = runTest {
        val message = mockk<Message>(relaxed = true)
        val reactions = mapOf("user_1" to "👍", "user_2" to "❤️")
        val readReceipts = mapOf("user_2" to 1700000000000L)
        val deliveryReceipts = mapOf("user_2" to 1699999990000L)

        coEvery { messageRepository.getMessageById("msg_123") } returns Result.Success(message)
        coEvery { messageRepository.getMessageReactions("msg_123") } returns Result.Success(reactions)
        coEvery { messageRepository.getReadReceipts("msg_123") } returns Result.Success(readReceipts)
        coEvery { messageRepository.getDeliveryReceipts("msg_123") } returns Result.Success(deliveryReceipts)

        val result = useCase("msg_123")
        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data

        assertEquals(message, data.message)
        assertEquals(reactions, data.reactions)
        assertEquals(readReceipts, data.readReceipts)
        assertEquals(deliveryReceipts, data.deliveryReceipts)
    }

    @Test
    fun testInvoke_reactionsFailure_fallsBackToEmptyMap() = runTest {
        val message = mockk<Message>(relaxed = true)

        coEvery { messageRepository.getMessageById("msg_123") } returns Result.Success(message)
        coEvery { messageRepository.getMessageReactions("msg_123") } throws RuntimeException("Network down")
        coEvery { messageRepository.getReadReceipts("msg_123") } returns Result.Success(emptyMap())
        coEvery { messageRepository.getDeliveryReceipts("msg_123") } returns Result.Success(emptyMap())

        val result = useCase("msg_123")
        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data
        assertTrue(data.reactions.isEmpty())
    }
}
