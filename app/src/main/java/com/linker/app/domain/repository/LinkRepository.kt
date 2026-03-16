package com.linker.app.domain.repository

import com.linker.app.domain.model.Link
import com.linker.app.domain.model.LinkType
import com.linker.app.core.util.Result
import kotlinx.coroutines.flow.Flow

interface LinkRepository {

    /** Home feed for the current user (from followed accounts). Offline-first. */
    fun observeFeed(limit: Int = 20): Flow<List<Link>>

    /** Fetches a paginated feed page (pulls from network and stores locally). */
    suspend fun refreshFeed(limit: Int = 20, offset: Int = 0): Result<List<Link>>

    /** Gets a single link by ID. */
    suspend fun getLinkById(linkId: String): Result<Link>

    /** Observes a single link (e.g. for real-time like-count updates). */
    fun observeLinkById(linkId: String): Flow<Link?>

    /** Returns all links by a specific author. */
    fun observeLinksByAuthor(authorId: String): Flow<List<Link>>

    /** Returns saved (bookmarked) links for the current user. */
    fun observeSavedLinks(): Flow<List<Link>>

    /** Returns relinked links for the current user. */
    fun observeRelinkedLinks(): Flow<List<Link>>

    /** Creates a new Link post. Media upload is handled via WorkManager. */
    suspend fun createLink(
        linkType: LinkType,
        description: String?,
        mediaLocalPaths: List<String>,
        location: String?
    ): Result<Link>

    /** Deletes a Link. */
    suspend fun deleteLink(linkId: String): Result<Unit>

    /** Toggles like on a Link. */
    suspend fun toggleLike(linkId: String): Result<Boolean>

    /** Toggles save (bookmark) on a Link. */
    suspend fun toggleSave(linkId: String): Result<Boolean>

    /** Relinks (reposts) a Link. */
    suspend fun toggleRelink(linkId: String): Result<Boolean>

    /** Increments or records a view. */
    suspend fun recordView(linkId: String): Result<Unit>
}
