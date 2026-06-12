package com.linker.app.domain.repository

import com.linker.app.domain.model.Link
import com.linker.app.domain.model.LinkType
import com.linker.app.core.util.Result
import kotlinx.coroutines.flow.Flow

interface LinkRepository {

    /** 
     * Home feed for the current user (from followed accounts). Offline-first.
     * Note: Returns cached results immediately, then fetches from network.
     */
    fun observeFeed(): Flow<Result<List<Link>>>

    /** 
     * Fetches a paginated feed page (pulls from network and stores locally).
     * Refreshes the feed from the beginning.
     */
    suspend fun refreshFeed(limit: Int = 20): Result<List<Link>>

    /**
     * Loads older items for the feed using cursor-based pagination.
     */
    suspend fun loadMoreFeed(beforeTimestamp: Long, limit: Int = 20): Result<List<Link>>

    /** Gets a single link by ID. */
    suspend fun getLinkById(linkId: String): Result<Link>

    /** Observes a single link (e.g. for real-time like-count updates). */
    fun observeLinkById(linkId: String): Flow<Result<Link?>>

    /** Returns all links by a specific author. */
    fun observeLinksByAuthor(authorId: String): Flow<Result<List<Link>>>

    /** Returns saved (bookmarked) links for the current user. */
    fun observeSavedLinks(): Flow<Result<List<Link>>>

    /** Returns relinked links for the current user. */
    fun observeRelinkedLinks(): Flow<Result<List<Link>>>

    /** 
     * Creates a new Link post. Media upload is handled via WorkManager.
     * 
     * Security:
     * - Media files are scanned for malware before public access.
     * - Size limit: 50MB for videos, 5MB for images.
     * - EXIF data (including GPS) is stripped from uploaded media.
     * 
     * @param mediaLocalPaths List of local file paths. Must not exceed 4 items.
     */
    suspend fun createLink(
        linkType: LinkType,
        description: String?,
        mediaLocalPaths: List<String>,
        location: String?
    ): Result<Link>

    /** Deletes a Link. */
    suspend fun deleteLink(linkId: String): Result<Unit>
    
    /** Deletes multiple Links in a bulk operation. */
    suspend fun deleteLinks(linkIds: List<String>): Result<Int>

    /** 
     * Toggles like on a Link.
     * @return Result containing true if liked, false if unliked.
     */
    suspend fun toggleLike(linkId: String): Result<Boolean>

    /** 
     * Toggles save (bookmark) on a Link.
     * @return Result containing true if saved, false if unsaved.
     */
    suspend fun toggleSave(linkId: String): Result<Boolean>

    /** 
     * Relinks (reposts) a Link.
     * @return Result containing true if relinked, false if unrelinked.
     */
    suspend fun toggleRelink(linkId: String): Result<Boolean>

    /** 
     * Increments or records a view.
     * This is a fire-and-forget operation optimized for performance.
     */
    fun recordView(linkId: String)
}
