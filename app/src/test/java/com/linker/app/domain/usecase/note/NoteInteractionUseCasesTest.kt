package com.linker.app.domain.usecase.note

import com.linker.app.core.util.Result
import com.linker.app.domain.model.Note
import com.linker.app.domain.repository.NoteRepository
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
class NoteInteractionUseCasesTest {

    private lateinit var noteRepository: NoteRepository
    private lateinit var postNoteUseCase: PostNoteUseCase
    private lateinit var postLocationNoteUseCase: PostLocationNoteUseCase
    private lateinit var postCountdownNoteUseCase: PostCountdownNoteUseCase
    private lateinit var postMusicNoteUseCase: PostMusicNoteUseCase
    private lateinit var replyToNoteUseCase: ReplyToNoteUseCase

    @Before
    fun setUp() {
        noteRepository = mockk(relaxed = true)
        postNoteUseCase = PostNoteUseCase(noteRepository)
        postLocationNoteUseCase = PostLocationNoteUseCase(noteRepository)
        postCountdownNoteUseCase = PostCountdownNoteUseCase(noteRepository)
        postMusicNoteUseCase = PostMusicNoteUseCase(noteRepository)
        replyToNoteUseCase = ReplyToNoteUseCase(noteRepository)
    }

    // --- PostNoteUseCase ---

    @Test
    fun testPostNote_blankContent_returnsError() = runTest {
        val result = postNoteUseCase("   ")
        assertTrue(result is Result.Error)
        assertEquals("Note content cannot be empty", (result as Result.Error).message)
    }

    @Test
    fun testPostNote_tooLong_returnsError() = runTest {
        val longContent = "A".repeat(Note.Text.MAX_TEXT_CONTENT_LENGTH + 1)
        val result = postNoteUseCase(longContent)
        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).message.contains("Note content too long"))
    }

    @Test
    fun testPostNote_sanitizesHtmlEntities() = runTest {
        coEvery { noteRepository.postNote(any(), any(), any()) } returns Result.Success(mockk(relaxed = true))

        val dirtyContent = " <Hello World> "
        val result = postNoteUseCase(dirtyContent)

        assertTrue(result is Result.Success)
        coVerify(exactly = 1) {
            noteRepository.postNote("&lt;Hello World&gt;", null, null)
        }
    }

    // --- PostLocationNoteUseCase ---

    @Test
    fun testPostLocationNote_invalidLatitude_returnsError() = runTest {
        val result = postLocationNoteUseCase(
            latitude = 95.0,
            longitude = 30.0,
            placeName = "Istanbul"
        )
        assertTrue(result is Result.Error)
        assertEquals("Latitude must be between -90 and 90", (result as Result.Error).message)
    }

    @Test
    fun testPostLocationNote_invalidLongitude_returnsError() = runTest {
        val result = postLocationNoteUseCase(
            latitude = 41.0,
            longitude = 200.0,
            placeName = "Istanbul"
        )
        assertTrue(result is Result.Error)
        assertEquals("Longitude must be between -180 and 180", (result as Result.Error).message)
    }

    @Test
    fun testPostLocationNote_blankPlaceName_returnsError() = runTest {
        val result = postLocationNoteUseCase(
            latitude = 41.0,
            longitude = 29.0,
            placeName = "   "
        )
        assertTrue(result is Result.Error)
        assertEquals("Place name cannot be empty", (result as Result.Error).message)
    }

    // --- PostCountdownNoteUseCase ---

    @Test
    fun testPostCountdownNote_blankTitle_returnsError() = runTest {
        val futureTime = System.currentTimeMillis() + 100000L
        val result = postCountdownNoteUseCase(
            title = "   ",
            targetTime = futureTime,
            content = "Excited!"
        )
        assertTrue(result is Result.Error)
        assertEquals("Title cannot be empty", (result as Result.Error).message)
    }

    @Test
    fun testPostCountdownNote_pastTargetTime_returnsError() = runTest {
        val pastTime = System.currentTimeMillis() - 10000L
        val result = postCountdownNoteUseCase(
            title = "New Year Party",
            targetTime = pastTime,
            content = "Party"
        )
        assertTrue(result is Result.Error)
        assertEquals("Target time must be in the future", (result as Result.Error).message)
    }

    // --- PostMusicNoteUseCase ---

    @Test
    fun testPostMusicNote_blankMetadata_returnsError() = runTest {
        val result = postMusicNoteUseCase(
            trackId = "",
            trackName = "Song",
            artistName = "Artist",
            albumArtUrl = null,
            previewUrl = null,
            clipStartMs = 0,
            clipEndMs = 30000
        )
        assertTrue(result is Result.Error)
        assertEquals("Track ID, Name, and Artist cannot be blank", (result as Result.Error).message)
    }

    // --- ReplyToNoteUseCase ---

    @Test
    fun testReplyToNote_validations() = runTest {
        val res1 = replyToNoteUseCase("  ", "Hello")
        assertTrue(res1 is Result.Error)
        assertEquals("Note ID cannot be empty", (res1 as Result.Error).message)

        val res2 = replyToNoteUseCase("note_1", "   ")
        assertTrue(res2 is Result.Error)
        assertEquals("Reply content cannot be empty", (res2 as Result.Error).message)

        val res3 = replyToNoteUseCase("note_1", "A".repeat(501))
        assertTrue(res3 is Result.Error)
        assertEquals("Reply is too long (max 500 characters)", (res3 as Result.Error).message)

        coEvery { noteRepository.replyToNote("note_1", "Nice note!") } returns Result.Success(Unit)
        val res4 = replyToNoteUseCase("note_1", "Nice note!")
        assertTrue(res4 is Result.Success)
    }
}
