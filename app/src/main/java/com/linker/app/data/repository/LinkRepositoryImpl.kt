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
    private val userDao: UserDao
) : LinkRepository {

    // Helper to resolve a single UserEntity into a domain User
    private suspend fun resolveAuthor(authorId: String) =
        userDao.getUserById(authorId)?.toDomain()
            ?: throw Exception("Author $authorId not found in local cache")

    override fun observeFeed(limit: Int): Flow<List<Link>> =
        linkDao.observeAllLinks().map { entities ->
            entities.mapNotNull { entity ->
                runCatching { entity.toDomain(resolveAuthor(entity.authorId)) }.getOrNull()
            }
        }

    override suspend fun refreshFeed(limit: Int, offset: Int): Result<List<Link>> = safeCall {
        // TODO: fetch from Supabase and upsert locally
        linkDao.getAllLinks(limit, offset).mapNotNull { entity ->
            runCatching { entity.toDomain(resolveAuthor(entity.authorId)) }.getOrNull()
        }
    }

    override suspend fun getLinkById(linkId: String): Result<Link> = safeCall {
        val entity = linkDao.getLinkById(linkId) ?: throw Exception("Link $linkId not found")
        entity.toDomain(resolveAuthor(entity.authorId))
    }

    override fun observeLinkById(linkId: String): Flow<Link?> =
        linkDao.observeLinkById(linkId).map { entity ->
            entity?.let { runCatching { it.toDomain(resolveAuthor(it.authorId)) }.getOrNull() }
        }

    override fun observeLinksByAuthor(authorId: String): Flow<List<Link>> =
        linkDao.observeLinksByAuthor(authorId).map { entities ->
            entities.mapNotNull { runCatching { it.toDomain(resolveAuthor(it.authorId)) }.getOrNull() }
        }

    override fun observeSavedLinks(): Flow<List<Link>> =
        linkDao.observeSavedLinks().map { entities ->
            entities.mapNotNull { runCatching { it.toDomain(resolveAuthor(it.authorId)) }.getOrNull() }
        }

    override fun observeRelinkedLinks(): Flow<List<Link>> =
        linkDao.observeRelinkedLinks().map { entities ->
            entities.mapNotNull { runCatching { it.toDomain(resolveAuthor(it.authorId)) }.getOrNull() }
        }

    override suspend fun createLink(
        linkType: LinkType,
        description: String?,
        mediaLocalPaths: List<String>,
        location: String?
    ): Result<Link> = safeCall {
        // TODO: upload media to Cloudinary via WorkManager, then POST to Supabase
        throw NotImplementedError("Media upload pipeline not yet implemented")
    }

    override suspend fun deleteLink(linkId: String): Result<Unit> = safeCall {
        linkDao.deleteLinkById(linkId)
        // TODO: remote delete via Supabase
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

    override suspend fun recordView(linkId: String): Result<Unit> = safeCall {
        // Optimistic local update only; batched sync handled by WorkManager
        // TODO: implement view batching
    }
}
