package com.linker.app.data.repository

import com.linker.app.core.util.Result
import com.linker.app.core.util.safeCall
import com.linker.app.data.local.dao.LinkDao
import com.linker.app.data.local.dao.UserDao
import com.linker.app.data.local.mapper.toDomain
import com.linker.app.data.local.mapper.toEntity
import com.linker.app.domain.model.Link
import com.linker.app.domain.model.LinkType
import com.linker.app.domain.repository.LinkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LinkRepositoryImpl @Inject constructor(
    private val linkDao: LinkDao,
    private val userDao: UserDao,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context
) : LinkRepository {

    // Helper to resolve a single UserEntity into a domain User
    private suspend fun resolveAuthor(authorId: String) =
        userDao.getUserById(authorId)?.toDomain()
            ?: throw Exception("Author $authorId not found in local cache")

    override fun observeFeed(): Flow<Result<List<Link>>> =
        linkDao.observeAllLinks().map { entities ->
            val links = entities.mapNotNull { entity ->
                runCatching { entity.toDomain(resolveAuthor(entity.authorId)) }.getOrNull()
            }
            Result.Success(links)
        }

    override suspend fun refreshFeed(limit: Int): Result<List<Link>> = safeCall {
        // TODO: fetch from Supabase and upsert locally
        linkDao.getAllLinks(limit, 0).mapNotNull { entity ->
            runCatching { entity.toDomain(resolveAuthor(entity.authorId)) }.getOrNull()
        }
    }

    override suspend fun loadMoreFeed(beforeTimestamp: Long, limit: Int): Result<List<Link>> = safeCall {
        // TODO: implement actual pagination
        emptyList()
    }

    override suspend fun getLinkById(linkId: String): Result<Link> = safeCall {
        val entity = linkDao.getLinkById(linkId) ?: throw Exception("Link $linkId not found")
        entity.toDomain(resolveAuthor(entity.authorId))
    }

    override fun observeLinkById(linkId: String): Flow<Result<Link?>> =
        linkDao.observeLinkById(linkId).map { entity ->
            val link = entity?.let { runCatching { it.toDomain(resolveAuthor(it.authorId)) }.getOrNull() }
            Result.Success(link)
        }

    override fun observeLinksByAuthor(authorId: String): Flow<Result<List<Link>>> =
        linkDao.observeLinksByAuthor(authorId).map { entities ->
            val links = entities.mapNotNull { runCatching { it.toDomain(resolveAuthor(it.authorId)) }.getOrNull() }
            Result.Success(links)
        }

    override fun observeSavedLinks(): Flow<Result<List<Link>>> =
        linkDao.observeSavedLinks().map { entities ->
            val links = entities.mapNotNull { runCatching { it.toDomain(resolveAuthor(it.authorId)) }.getOrNull() }
            Result.Success(links)
        }

    override fun observeRelinkedLinks(): Flow<Result<List<Link>>> =
        linkDao.observeRelinkedLinks().map { entities ->
            val links = entities.mapNotNull { runCatching { it.toDomain(resolveAuthor(it.authorId)) }.getOrNull() }
            Result.Success(links)
        }

    override suspend fun createLink(
        linkType: LinkType,
        description: String?,
        mediaLocalPaths: List<String>,
        location: String?
    ): Result<Link> = safeCall {
        val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            ?: throw IllegalStateException("Not authenticated")
        
        val linkId = java.util.UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        // Local placeholder for immediate UI feedback
        val placeholderMedia = mediaLocalPaths.map { "placeholder://$it" }

        val mappedLinkType = when(linkType) {
            LinkType.FEED -> com.linker.app.data.local.entity.LinkType.FEED
            LinkType.VIDEO -> com.linker.app.data.local.entity.LinkType.VIDEO
            LinkType.REEL -> com.linker.app.data.local.entity.LinkType.REEL
        }

        val entity = com.linker.app.data.local.entity.LinkEntity(
            linkId = linkId,
            authorId = currentUserId,
            linkType = mappedLinkType,
            description = description,
            mediaUrls = placeholderMedia,
            thumbnailUrl = if (mappedLinkType != com.linker.app.data.local.entity.LinkType.FEED) "placeholder" else null,
            videoDuration = if (mappedLinkType != com.linker.app.data.local.entity.LinkType.FEED) 15 else null,
            location = location,
            likesCount = 0,
            commentsCount = 0,
            sharesCount = 0,
            relinksCount = 0,
            savesCount = 0,
            viewsCount = 0,
            isLiked = false,
            isSaved = false,
            isRelinked = false,
            createdAt = now,
            updatedAt = now,
            lastSyncedAt = now
        )

        linkDao.insertLink(entity)

        if (mediaLocalPaths.isNotEmpty()) {
            val workData = androidx.work.workDataOf(
                "targetId" to linkId,
                "targetType" to "LINK",
                "mediaLocalPaths" to mediaLocalPaths.toTypedArray()
            )
            val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.linker.app.core.work.CloudinaryUploadWorker>()
                .setInputData(workData)
                .setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                        .build()
                )
                .build()
            
            androidx.work.WorkManager.getInstance(appContext).enqueue(workRequest)
        }

        entity.toDomain(resolveAuthor(currentUserId))
    }

    override suspend fun deleteLink(linkId: String): Result<Unit> = safeCall {
        linkDao.deleteLinkById(linkId)
        // Remote delete will be implemented with Supabase integration
    }

    override suspend fun deleteLinks(linkIds: List<String>): Result<Int> = safeCall {
        var count = 0
        for (id in linkIds) {
            linkDao.deleteLinkById(id)
            count++
        }
        count
    }

    override suspend fun toggleLike(linkId: String): Result<Boolean> = safeCall {
        val entity = linkDao.getLinkById(linkId) ?: throw Exception("Link not found")
        val newLiked = !entity.isLiked
        val delta = if (newLiked) 1 else -1
        linkDao.updateLikeStatus(linkId, newLiked, delta)
        // TODO: sync to Supabase (enqueue if offline)
        newLiked
    }

    override suspend fun toggleSave(linkId: String): Result<Boolean> = safeCall {
        val entity = linkDao.getLinkById(linkId) ?: throw Exception("Link not found")
        val newSaved = !entity.isSaved
        linkDao.updateSaveStatus(linkId, newSaved)
        newSaved
    }

    override suspend fun toggleRelink(linkId: String): Result<Boolean> = safeCall {
        val entity = linkDao.getLinkById(linkId) ?: throw Exception("Link not found")
        val newRelinked = !entity.isRelinked
        val delta = if (newRelinked) 1 else -1
        linkDao.updateRelinkStatus(linkId, newRelinked, delta)
        newRelinked
    }

    override fun recordView(linkId: String) {
        // Optimistic local update only; batched sync handled by WorkManager
        // TODO: implement view batching
    }
}
