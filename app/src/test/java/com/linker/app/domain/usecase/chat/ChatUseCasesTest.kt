package com.linker.app.domain.usecase.chat

import com.linker.app.core.util.Result
import com.linker.app.domain.model.Chat
import com.linker.app.domain.model.Message
import com.linker.app.domain.model.MessageType
import com.linker.app.domain.repository.ChatRepository
import com.linker.app.domain.repository.MessageRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatUseCasesTest {

    private lateinit var messageRepository: MessageRepository
    private lateinit var chatRepository: ChatRepository

    @Before
    fun setup() {
        messageRepository = mockk(relaxed = true)
        chatRepository = mockk(relaxed = true)
    }

    // --- SendMessageUseCase Tests ---

    @Test
    fun testSendMessage_blankChatId_returnsError() = runTest {
        val useCase = SendMessageUseCase(messageRepository)
        val result = useCase(chatId = "   ", content = "Hello")

        assertTrue(result is Result.Error)
        assertEquals("Chat ID cannot be empty", (result as Result.Error).message)
    }

    @Test
    fun testSendMessage_blankTextContent_returnsError() = runTest {
        val useCase = SendMessageUseCase(messageRepository)
        val result = useCase(chatId = "chat_1", content = "   ", messageType = MessageType.TEXT)

        assertTrue(result is Result.Error)
        assertEquals("Message cannot be empty", (result as Result.Error).message)
    }

    @Test
    fun testSendMessage_tooLongTextContent_returnsError() = runTest {
        val useCase = SendMessageUseCase(messageRepository)
        val veryLongContent = "A".repeat(5001)
        val result = useCase(chatId = "chat_1", content = veryLongContent, messageType = MessageType.TEXT)

        assertTrue(result is Result.Error)
        assertEquals("Message is too long (max 5000 characters)", (result as Result.Error).message)
    }

    @Test
    fun testSendMessage_textSanitizesHtmlTags() = runTest {
        val useCase = SendMessageUseCase(messageRepository)
        val dummyMessage = mockk<Message>(relaxed = true)
        coEvery {
            messageRepository.sendMessage(any(), any(), any(), any(), any())
        } returns Result.Success(dummyMessage)

        val dirtyContent = "<b>Hello</b> <script>alert('xss')</script>Linker!"
        val result = useCase(chatId = "chat_1", content = dirtyContent, messageType = MessageType.TEXT)

        assertTrue(result is Result.Success)
        coVerify(exactly = 1) {
            messageRepository.sendMessage(
                chatId = "chat_1",
                messageType = MessageType.TEXT,
                content = "Hello Linker!",
                mediaLocalPath = null,
                replyToMessageId = null
            )
        }
    }

    @Test
    fun testSendMessage_mediaWithoutPath_returnsError() = runTest {
        val useCase = SendMessageUseCase(messageRepository)
        val result = useCase(
            chatId = "chat_1",
            content = "Caption",
            messageType = MessageType.IMAGE,
            mediaLocalPath = null
        )

        assertTrue(result is Result.Error)
        assertEquals("Media path is required for IMAGE messages", (result as Result.Error).message)
    }

    @Test
    fun testSendMessage_mediaWithPathTraversal_returnsError() = runTest {
        val useCase = SendMessageUseCase(messageRepository)
        val traversalPath = "file:///data/user/0/com.linker/../../etc/passwd"
        val result = useCase(
            chatId = "chat_1",
            content = "Caption",
            messageType = MessageType.IMAGE,
            mediaLocalPath = traversalPath
        )

        assertTrue(result is Result.Error)
        assertEquals("Invalid media path", (result as Result.Error).message)
    }

    @Test
    fun testSendMessage_mediaCaptionTooLong_returnsError() = runTest {
        val useCase = SendMessageUseCase(messageRepository)
        val longCaption = "C".repeat(201)
        val result = useCase(
            chatId = "chat_1",
            content = longCaption,
            messageType = MessageType.IMAGE,
            mediaLocalPath = "content://media/external/images/media/1"
        )

        assertTrue(result is Result.Error)
        assertEquals("Caption is too long", (result as Result.Error).message)
    }

    // --- CreateGroupChatUseCase Tests ---

    @Test
    fun testCreateGroupChat_invalidGroupName_returnsError() = runTest {
        val useCase = CreateGroupChatUseCase(chatRepository)
        val result = useCase(name = "   ", participantIds = listOf("user1", "user2"))

        assertTrue(result is Result.Error)
        assertEquals("Invalid group name", (result as Result.Error).message)
    }

    @Test
    fun testCreateGroupChat_lessThanTwoParticipants_returnsError() = runTest {
        val useCase = CreateGroupChatUseCase(chatRepository)
        val result = useCase(name = "Family", participantIds = listOf("user1"))

        assertTrue(result is Result.Error)
        assertEquals("Participants must be between 2 and 256", (result as Result.Error).message)
    }

    @Test
    fun testCreateGroupChat_duplicateParticipants_returnsError() = runTest {
        val useCase = CreateGroupChatUseCase(chatRepository)
        val result = useCase(name = "Dev Team", participantIds = listOf("alice", "bob", "alice"))

        assertTrue(result is Result.Error)
        assertEquals("Duplicate participants not allowed", (result as Result.Error).message)
    }

    @Test
    fun testCreateGroupChat_invalidPermissionKey_returnsError() = runTest {
        val useCase = CreateGroupChatUseCase(chatRepository)
        val invalidPermissions = mapOf("illegalKey" to true)
        val result = useCase(name = "Dev Team", participantIds = listOf("alice", "bob"), permissions = invalidPermissions)

        assertTrue(result is Result.Error)
        assertEquals("Invalid permission keys", (result as Result.Error).message)
    }

    @Test
    fun testCreateGroupChat_validArguments_callsRepository() = runTest {
        val useCase = CreateGroupChatUseCase(chatRepository)
        val dummyChat = mockk<Chat>(relaxed = true)
        coEvery { chatRepository.createGroupChat(any(), any(), any()) } returns Result.Success(dummyChat)

        val permissions = mapOf("canSendMessages" to true, "canAddMembers" to false)
        val result = useCase(name = "Work Project", participantIds = listOf("alice", "bob", "charlie"), permissions = permissions)

        assertTrue(result is Result.Success)
        coVerify(exactly = 1) {
            chatRepository.createGroupChat("Work Project", listOf("alice", "bob", "charlie"), permissions)
        }
    }

    // --- EditMessageUseCase Tests ---

    @Test
    fun testEditMessage_validatesAndSanitizes() = runTest {
        val useCase = EditMessageUseCase(messageRepository)
        coEvery { messageRepository.editMessage(any(), any()) } returns Result.Success(Unit)

        val resultBlank = useCase(messageId = "", newContent = "test")
        assertTrue(resultBlank is Result.Error)

        val resultEmptyContent = useCase(messageId = "msg_1", newContent = "   ")
        assertTrue(resultEmptyContent is Result.Error)

        val resultSuccess = useCase(messageId = "msg_1", newContent = "<b>Updated</b> message")
        assertTrue(resultSuccess is Result.Success)
        coVerify(exactly = 1) {
            messageRepository.editMessage("msg_1", "Updated message")
        }
    }

    // --- SearchMessagesUseCase Tests ---

    @Test
    fun testSearchMessages_escapesSpecialRegexChars() = runTest {
        val useCase = SearchMessagesUseCase(messageRepository)
        coEvery { messageRepository.searchMessages(any(), any()) } returns Result.Success(emptyList())

        // Blank query returns empty list directly
        val blankResult = useCase(chatId = "c1", query = "   ")
        assertTrue(blankResult is Result.Success)
        assertEquals(0, (blankResult as Result.Success).data.size)

        // Query with regex special chars
        val result = useCase(chatId = "c1", query = "search [test]* (query)+")
        assertTrue(result is Result.Success)

        coVerify(exactly = 1) {
            messageRepository.searchMessages("c1", match { escapedQuery ->
                escapedQuery.contains("\\[test\\]") && escapedQuery.contains("\\(query\\)")
            })
        }
    }
}
