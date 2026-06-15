package com.linker.app.data.repository

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.linker.app.domain.model.Note
import com.linker.app.domain.model.NoteType
import com.linker.app.domain.model.User
import com.linker.app.core.util.Result
import com.linker.app.core.util.safeCall
import com.linker.app.domain.repository.NoteMediaType
import com.linker.app.domain.repository.NoteRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.linker.app.data.local.dao.UserDao
import com.linker.app.data.local.entity.UserEntity
import com.linker.app.data.local.mapper.toDomain
import com.linker.app.domain.model.NoteAuthor
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val userDao: UserDao
) : NoteRepository {
    private val notesCollection = firestore.collection("notes")
    private val lastNotePostTime = ConcurrentHashMap<String, Long>()

    private val currentUserId: String
        get() = auth.currentUser?.uid ?: ""

    init {
        scheduleExpiredNotesCleanup()
    }

    private fun scheduleExpiredNotesCleanup() {
        val cleanupRequest = PeriodicWorkRequestBuilder<com.linker.app.core.work.ExpiredNotesCleanupWorker>(
            repeatInterval = 6,
            repeatIntervalTimeUnit = TimeUnit.HOURS
        )
            .setConstraints(Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build())
            .build()
        
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "expired_notes_cleanup",
            ExistingPeriodicWorkPolicy.KEEP,
            cleanupRequest
        )
    }

    /** Observe all active (non-expired) notes. */
    override fun observeActiveNotes(): Flow<Result<List<Note>>> = callbackFlow {
        val now = System.currentTimeMillis()
        val listener = notesCollection
            .whereGreaterThan("expiresAt", now)
            .orderBy("expiresAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.Success(emptyList()))
                    return@addSnapshotListener
                }
                
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    val rawNotesData = snapshot?.documents?.mapNotNull { doc ->
                        doc.id to (doc.data ?: return@mapNotNull null)
                    } ?: emptyList()

                    // Extract all unique author IDs
                    val authorIds = rawNotesData.mapNotNull { it.second["authorId"] as? String }.toSet()
                    
                    // Batch fetch authors
                    val usersMap = if (authorIds.isNotEmpty()) {
                        userDao.getUsersByIds(authorIds.toList()).associateBy { it.userId }
                    } else {
                        emptyMap()
                    }

                    // Map to domain models with real user data
                    val notes = rawNotesData.map { (docId, data) ->
                        mapToNoteWithUser(docId, data, usersMap)
                    }
                    
                    trySend(Result.Success(notes))
                }
            }
        awaitClose { listener.remove() }
    }

    override suspend fun refreshNotes(limit: Int): Result<List<Note>> = com.linker.app.core.util.safeCall {
        emptyList()
    }

    /** Post a new text note (expires in 24 hours). */
    override suspend fun loadMoreNotes(beforeTimestamp: Long, limit: Int): Result<List<Note>> = safeCall {
        emptyList()
    }

    override suspend fun postNote(content: String): Result<Note> {
        return try {
            val trimmedContent = content.trim()
            if (trimmedContent.isBlank()) {
                return Result.Error("Note content cannot be empty")
            }
            if (trimmedContent.length > 500) {
                return Result.Error("Note too long (max 500 characters)")
            }

            val now = System.currentTimeMillis()
            val lastPostTime = lastNotePostTime[currentUserId] ?: 0L
            if (now - lastPostTime < 60_000) { // 1 minute cooldown
                return Result.Error("Please wait before posting another note")
            }

            val noteId = UUID.randomUUID().toString()
            val expiresAt = now + com.linker.app.core.util.TimeConstants.DAY_MS

            val noteData = hashMapOf(
                "authorId" to currentUserId,
                "noteType" to "TEXT",
                "content" to trimmedContent,
                "backgroundColor" to null,
                "textColor" to null,
                "createdAt" to now,
                "expiresAt" to expiresAt
            )
            notesCollection.document(noteId).set(noteData).await()
            lastNotePostTime[currentUserId] = now

            val authorStub = NoteAuthor(
                userId = currentUserId,
                username = "",
                displayName = "",
                profileImageUrl = null
            )
            Result.Success(
                Note.Text(
                    noteId = noteId,
                    author = authorStub,
                    content = trimmedContent,
                    backgroundColor = null,
                    textColor = null,
                    createdAt = now,
                    expiresAt = expiresAt
                )
            )
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error", e.toString())
        }
    }

    override suspend fun postMediaNote(
        mediaLocalPath: String,
        mediaType: com.linker.app.domain.repository.NoteMediaType,
        caption: String?
    ): Result<Note> = safeCall {
        // Stub implementation for now
        com.linker.app.domain.model.Note.Text(
            noteId = UUID.randomUUID().toString(),
            author = com.linker.app.domain.model.NoteAuthor(currentUserId ?: "", "", "", null),
            content = caption ?: "",
            backgroundColor = null,
            textColor = null,
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + com.linker.app.core.util.TimeConstants.DAY_MS
        )
    }

    /** Delete a note. */
    override suspend fun deleteNote(noteId: String): Result<Unit> {
        return try {
            val noteDoc = notesCollection.document(noteId).get().await()
            if (!noteDoc.exists()) {
                return Result.Error("Note not found")
            }
            
            val authorId = noteDoc.getString("authorId")
            if (authorId != currentUserId) {
                return Result.Error("You can only delete your own notes")
            }
            
            notesCollection.document(noteId).delete().await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error", e.toString())
        }
    }

    override fun recordView(noteId: String) {}
    override suspend fun getViewCount(noteId: String): Result<Int> = safeCall { 0 }
    override suspend fun getViewers(noteId: String): Result<List<com.linker.app.domain.repository.NoteViewer>> = safeCall { emptyList() }
    override suspend fun toggleLikeNote(noteId: String): Result<Boolean> = safeCall { false }
    override suspend fun reactToNote(noteId: String, emoji: String?): Result<Unit> = safeCall {}
    override suspend fun getNoteReactions(noteId: String): Result<Map<String, String>> = safeCall { emptyMap() }
    override suspend fun replyToNote(noteId: String, content: String): Result<Unit> = safeCall {}

    override suspend fun purgeExpiredNotes(): Result<Unit> = com.linker.app.core.util.safeCall {
        val now = System.currentTimeMillis()
        val snapshot = notesCollection
            .whereLessThan("expiresAt", now)
            .get()
            .await()

        if (!snapshot.isEmpty) {
            val batch = firestore.batch()
            snapshot.documents.forEach { doc ->
                batch.delete(doc.reference)
            }
            batch.commit().await()
        }
    }

    private fun mapToNote(noteId: String, data: Map<String, Any?>): Note {
        val authorId = data["authorId"] as? String ?: ""
        val authorStub = NoteAuthor(
            userId = authorId,
            username = "",
            displayName = "",
            profileImageUrl = null
        )

        val noteTypeStr = data["noteType"] as? String ?: "TEXT"
        val noteType = try { NoteType.valueOf(noteTypeStr) } catch (_: Exception) { NoteType.TEXT }
        
        val content = data["content"] as? String ?: ""
        val backgroundColor = data["backgroundColor"] as? String
        val textColor = data["textColor"] as? String
        val createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L
        val expiresAt = (data["expiresAt"] as? Number)?.toLong() ?: 0L

        return when (noteType) {
            NoteType.TEXT -> Note.Text(
                noteId = noteId,
                author = authorStub,
                content = content,
                backgroundColor = backgroundColor,
                textColor = textColor,
                createdAt = createdAt,
                expiresAt = expiresAt
            )
            NoteType.MUSIC -> Note.Music(
                noteId = noteId,
                author = authorStub,
                content = content,
                musicTrackId = data["musicTrackId"] as? String ?: "",
                musicTrackName = data["musicTrackName"] as? String ?: "",
                musicArtistName = data["musicArtistName"] as? String ?: "",
                musicAlbumArt = data["musicAlbumArt"] as? String,
                backgroundColor = backgroundColor,
                textColor = textColor,
                createdAt = createdAt,
                expiresAt = expiresAt
            )
            NoteType.COUNTDOWN -> Note.Countdown(
                noteId = noteId,
                author = authorStub,
                content = content,
                countdownTargetTime = (data["countdownTargetTime"] as? Number)?.toLong() ?: 0L,
                countdownTitle = data["countdownTitle"] as? String ?: "",
                backgroundColor = backgroundColor,
                textColor = textColor,
                createdAt = createdAt,
                expiresAt = expiresAt
            )
            NoteType.LOCATION -> Note.Location(
                noteId = noteId,
                author = authorStub,
                latitude = (data["latitude"] as? Number)?.toDouble() ?: 0.0,
                longitude = (data["longitude"] as? Number)?.toDouble() ?: 0.0,
                placeName = data["placeName"] as? String ?: "",
                mapPreviewUrl = data["mapPreviewUrl"] as? String,
                backgroundColor = backgroundColor,
                textColor = textColor,
                createdAt = createdAt,
                expiresAt = expiresAt
            )
        }
    }

    private fun mapToNoteWithUser(noteId: String, data: Map<String, Any?>, usersMap: Map<String, UserEntity>): Note {
        val authorId = data["authorId"] as? String ?: ""
        val author = usersMap[authorId]?.toDomain() ?: User.deletedUser(authorId)
        val authorRef = NoteAuthor.from(author)

        val noteTypeStr = data["noteType"] as? String ?: "TEXT"
        val noteType = try { NoteType.valueOf(noteTypeStr) } catch (_: Exception) { NoteType.TEXT }
        
        val content = data["content"] as? String ?: ""
        val backgroundColor = data["backgroundColor"] as? String
        val textColor = data["textColor"] as? String
        val createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L
        val expiresAt = (data["expiresAt"] as? Number)?.toLong() ?: 0L

        return when (noteType) {
            NoteType.TEXT -> Note.Text(
                noteId = noteId,
                author = authorRef,
                content = content,
                backgroundColor = backgroundColor,
                textColor = textColor,
                createdAt = createdAt,
                expiresAt = expiresAt
            )
            NoteType.MUSIC -> Note.Music(
                noteId = noteId,
                author = authorRef,
                content = content,
                musicTrackId = data["musicTrackId"] as? String ?: "",
                musicTrackName = data["musicTrackName"] as? String ?: "",
                musicArtistName = data["musicArtistName"] as? String ?: "",
                musicAlbumArt = data["musicAlbumArt"] as? String,
                backgroundColor = backgroundColor,
                textColor = textColor,
                createdAt = createdAt,
                expiresAt = expiresAt
            )
            NoteType.COUNTDOWN -> Note.Countdown(
                noteId = noteId,
                author = authorRef,
                content = content,
                countdownTargetTime = (data["countdownTargetTime"] as? Number)?.toLong() ?: 0L,
                countdownTitle = data["countdownTitle"] as? String ?: "",
                backgroundColor = backgroundColor,
                textColor = textColor,
                createdAt = createdAt,
                expiresAt = expiresAt
            )
            NoteType.LOCATION -> Note.Location(
                noteId = noteId,
                author = authorRef,
                latitude = (data["latitude"] as? Number)?.toDouble() ?: 0.0,
                longitude = (data["longitude"] as? Number)?.toDouble() ?: 0.0,
                placeName = data["placeName"] as? String ?: "",
                mapPreviewUrl = data["mapPreviewUrl"] as? String,
                backgroundColor = backgroundColor,
                textColor = textColor,
                createdAt = createdAt,
                expiresAt = expiresAt
            )
        }
    }

    override suspend fun postLocationNote(
        latitude: Double,
        longitude: Double,
        placeName: String
    ): Result<Note.Location> {
        return try {
            val now = System.currentTimeMillis()
            val expiresAt = now + com.linker.app.core.util.TimeConstants.DAY_MS
            val noteId = UUID.randomUUID().toString()

            val noteData = hashMapOf(
                "authorId" to currentUserId,
                "noteType" to "LOCATION",
                "content" to "",
                "latitude" to latitude,
                "longitude" to longitude,
                "placeName" to placeName,
                "createdAt" to now,
                "expiresAt" to expiresAt
            )
            notesCollection.document(noteId).set(noteData).await()

            val authorStub = NoteAuthor(userId = currentUserId, username = "", displayName = "", profileImageUrl = null)
            Result.Success(
                Note.Location(
                    noteId = noteId,
                    author = authorStub,
                    latitude = latitude,
                    longitude = longitude,
                    placeName = placeName,
                    mapPreviewUrl = null,
                    backgroundColor = null,
                    textColor = null,
                    createdAt = now,
                    expiresAt = expiresAt
                )
            )
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error", e.toString())
        }
    }

    override suspend fun postCountdownNote(
        title: String,
        targetTime: Long
    ): Result<Note.Countdown> {
        return try {
            val now = System.currentTimeMillis()
            val expiresAt = now + com.linker.app.core.util.TimeConstants.DAY_MS
            val noteId = UUID.randomUUID().toString()

            val noteData = hashMapOf(
                "authorId" to currentUserId,
                "noteType" to "COUNTDOWN",
                "content" to "",
                "countdownTitle" to title,
                "countdownTargetTime" to targetTime,
                "createdAt" to now,
                "expiresAt" to expiresAt
            )
            notesCollection.document(noteId).set(noteData).await()

            val authorStub = NoteAuthor(userId = currentUserId, username = "", displayName = "", profileImageUrl = null)
            Result.Success(
                Note.Countdown(
                    noteId = noteId,
                    author = authorStub,
                    content = "",
                    countdownTitle = title,
                    countdownTargetTime = targetTime,
                    backgroundColor = null,
                    textColor = null,
                    createdAt = now,
                    expiresAt = expiresAt
                )
            )
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error", e.toString())
        }
    }

    override suspend fun postMusicNote(
        trackId: String,
        trackName: String,
        artistName: String,
        albumArtUrl: String?,
        caption: String
    ): Result<Note.Music> {
        return try {
            val now = System.currentTimeMillis()
            val expiresAt = now + com.linker.app.core.util.TimeConstants.DAY_MS
            val noteId = UUID.randomUUID().toString()

            val noteData = hashMapOf(
                "authorId" to currentUserId,
                "noteType" to "MUSIC",
                "content" to caption,
                "musicTrackId" to trackId,
                "musicTrackName" to trackName,
                "musicArtistName" to artistName,
                "musicAlbumArt" to albumArtUrl,
                "createdAt" to now,
                "expiresAt" to expiresAt
            )
            notesCollection.document(noteId).set(noteData).await()

            val authorStub = NoteAuthor(userId = currentUserId, username = "", displayName = "", profileImageUrl = null)
            Result.Success(
                Note.Music(
                    noteId = noteId,
                    author = authorStub,
                    content = caption,
                    musicTrackId = trackId,
                    musicTrackName = trackName,
                    musicArtistName = artistName,
                    musicAlbumArt = albumArtUrl,
                    backgroundColor = null,
                    textColor = null,
                    createdAt = now,
                    expiresAt = expiresAt
                )
            )
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error", e.toString())
        }
    }

    override suspend fun subscribeToCountdown(noteId: String): Result<Unit> = safeCall { throw NotImplementedError() }
    override suspend fun unsubscribeFromCountdown(noteId: String): Result<Unit> = safeCall { throw NotImplementedError() }
    override suspend fun isSubscribedToCountdown(noteId: String): Result<Boolean> = safeCall { throw NotImplementedError() }
}
