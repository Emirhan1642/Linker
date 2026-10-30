package com.linker.app.domain.usecase.link

import com.linker.app.core.util.Result
import com.linker.app.domain.model.DescriptionVersion
import com.linker.app.domain.model.Link
import com.linker.app.domain.model.ReportReason
import com.linker.app.domain.repository.LinkRepository
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
class LinkInteractionUseCasesTest {

    private lateinit var linkRepository: LinkRepository
    private lateinit var updateLinkDescriptionUseCase: UpdateLinkDescriptionUseCase
    private lateinit var getLinkDescriptionHistoryUseCase: GetLinkDescriptionHistoryUseCase
    private lateinit var sendLinkToDmUseCase: SendLinkToDmUseCase
    private lateinit var reportLinkUseCase: ReportLinkUseCase
    private lateinit var shareLinkExternallyUseCase: ShareLinkExternallyUseCase

    @Before
    fun setUp() {
        linkRepository = mockk(relaxed = true)
        updateLinkDescriptionUseCase = UpdateLinkDescriptionUseCase(linkRepository)
        getLinkDescriptionHistoryUseCase = GetLinkDescriptionHistoryUseCase(linkRepository)
        sendLinkToDmUseCase = SendLinkToDmUseCase(linkRepository)
        reportLinkUseCase = ReportLinkUseCase(linkRepository)
        shareLinkExternallyUseCase = ShareLinkExternallyUseCase(linkRepository)
    }

    // --- UpdateLinkDescriptionUseCase ---

    @Test
    fun testUpdateDescription_blankLinkId_returnsError() = runTest {
        val result = updateLinkDescriptionUseCase("   ", "Valid description")
        assertTrue(result is Result.Error)
        assertEquals("Link ID cannot be empty", (result as Result.Error).message)
    }

    @Test
    fun testUpdateDescription_blankDescription_returnsError() = runTest {
        val result = updateLinkDescriptionUseCase("link_1", "   ")
        assertTrue(result is Result.Error)
        assertEquals("Description cannot be empty", (result as Result.Error).message)
    }

    @Test
    fun testUpdateDescription_exceedsMaxLength_returnsError() = runTest {
        val longText = "A".repeat(Link.MAX_DESCRIPTION_LENGTH + 1)
        val result = updateLinkDescriptionUseCase("link_1", longText)
        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).message.contains("Description exceeds maximum length"))
    }

    @Test
    fun testUpdateDescription_sanitizesHtmlTags() = runTest {
        coEvery { linkRepository.updateLinkDescription(any(), any()) } returns Result.Success(Unit)

        val dirtyDescription = "<p>Check out this link! <script>alert(1)</script></p>"
        val result = updateLinkDescriptionUseCase("link_1", dirtyDescription)

        assertTrue(result is Result.Success)
        coVerify(exactly = 1) {
            linkRepository.updateLinkDescription("link_1", "Check out this link!")
        }
    }

    // --- GetLinkDescriptionHistoryUseCase ---

    @Test
    fun testGetDescriptionHistory_blankLinkId_returnsError() = runTest {
        val result = getLinkDescriptionHistoryUseCase("")
        assertTrue(result is Result.Error)
        assertEquals("Link ID cannot be empty", (result as Result.Error).message)
    }

    @Test
    fun testGetDescriptionHistory_success_sortedNewestFirst() = runTest {
        val v1 = DescriptionVersion(version = 1, content = "Old version", editedAt = 1000L, editedByUserId = "user_1")
        val v2 = DescriptionVersion(version = 2, content = "Middle version", editedAt = 2000L, editedByUserId = "user_1")
        val v3 = DescriptionVersion(version = 3, content = "Latest version", editedAt = 3000L, editedByUserId = "user_1")

        // Return unsorted from repo
        coEvery { linkRepository.getLinkDescriptionHistory("link_1") } returns Result.Success(listOf(v1, v3, v2))

        val result = getLinkDescriptionHistoryUseCase("link_1")
        assertTrue(result is Result.Success)
        val sortedList = (result as Result.Success).data

        assertEquals(3, sortedList.size)
        assertEquals(v3, sortedList[0])
        assertEquals(v2, sortedList[1])
        assertEquals(v1, sortedList[2])
    }

    // --- SendLinkToDmUseCase ---

    @Test
    fun testSendLinkToDm_validatesInputs() = runTest {
        val res1 = sendLinkToDmUseCase("  ", "user_1")
        assertTrue(res1 is Result.Error)
        assertEquals("Link ID cannot be empty", (res1 as Result.Error).message)

        val res2 = sendLinkToDmUseCase("link_1", "   ")
        assertTrue(res2 is Result.Error)
        assertEquals("Recipient user ID cannot be empty", (res2 as Result.Error).message)

        coEvery { linkRepository.sendLinkToDm("link_1", "user_1") } returns Result.Success(Unit)
        val res3 = sendLinkToDmUseCase("link_1", "user_1")
        assertTrue(res3 is Result.Success)
    }

    // --- ReportLinkUseCase & ShareLinkExternallyUseCase ---

    @Test
    fun testReportLink_blankLinkId_returnsError() = runTest {
        val result = reportLinkUseCase("  ", ReportReason.SPAM)
        assertTrue(result is Result.Error)
        assertEquals("Link ID cannot be empty", (result as Result.Error).message)
    }

    @Test
    fun testShareLinkExternally_blankLinkId_returnsError() = runTest {
        val result = shareLinkExternallyUseCase("   ")
        assertTrue(result is Result.Error)
        assertEquals("Link ID cannot be empty", (result as Result.Error).message)
    }

    @Test
    fun testShareLinkExternally_success_returnsUrl() = runTest {
        coEvery { linkRepository.getShareableLink("link_1") } returns Result.Success("https://linker.app/link/1")
        val result = shareLinkExternallyUseCase("link_1")
        assertTrue(result is Result.Success)
        assertEquals("https://linker.app/link/1", (result as Result.Success).data)
    }
}
