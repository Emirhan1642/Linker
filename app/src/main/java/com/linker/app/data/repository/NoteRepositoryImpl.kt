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
import com.linker.app.domain.repository.NoteRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.linker.app.data.local.dao.UserDao
import com.linker.app.data.local.entity.UserEntity
import com.linker.app.data.local.mapper.toDomain
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
    override fun observeActiveNotes(): Flow<List<Note>> = callbackFlow {
        val now = System.currentTimeMillis()
        val listener = notesCollection
            .whereGreaterThan("expiresAt", now)
            .orderBy("expiresAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
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
                    
                    trySend(notes)
                }
            }
        awaitClose { listener.remove() }
    }

    /** Post a new text note (expires in 24 hours). */
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

            val authorStub = User(
                userId = currentUserId, username = "", displayName = "",
                email = null, phoneNumber = null, bio = null,
                profileImageUrl = null, coverImageUrl = null,
                isVerified = false, followersCount = 0, followingCount = 0,
                likesCount = 0, isFollowing = false, isFollowedBy = false,
                isBlocked = false, isMuted = false,
                createdAt = 0L, updatedAt = 0L
            )
            Result.Success(
                Note(
                    noteId = noteId,
                    author = authorStub,
                    noteType = NoteType.TEXT,
                    content = trimmedContent,
                    musicTrackId = null, musicTrackName = null,
                    musicArtistName = null, musicAlbumArt = null,
                    countdownTargetTime = null, countdownTitle = null,
                    backgroundColor = null, textColor = null,
                    createdAt = now, expiresAt = expiresAt
                )
            )
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error", e.toString())
        }
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
        val authorStub = User(
            userId = authorId, username = "", displayName = "",
            email = null, phoneNumber = null, bio = null,
            profileImageUrl = null, coverImageUrl = null,
            isVerified = false, followersCount = 0, followingCount = 0,
            likesCount = 0, isFollowing = false, isFollowedBy = false,
            isBlocked = false, isMuted = false,
            createdAt = 0L, updatedAt = 0L
        )

        val noteTypeStr = data["noteType"] as? String ?: "TEXT"
        val noteType = try { NoteType.valueOf(noteTypeStr) } catch (_: Exception) { NoteType.TEXT }

        return Note(
            noteId = noteId,
            author = authorStub,
            noteType = noteType,
            content = data["content"] as? String ?: "",
            musicTrackId = data["musicTrackId"] as? String,
            musicTrackName = data["musicTrackName"] as? String,
            musicArtistName = data["musicArtistName"] as? String,
            musicAlbumArt = data["musicAlbumArt"] as? String,
            countdownTargetTime = (data["countdownTargetTime"] as? Number)?.toLong(),
            countdownTitle = data["countdownTitle"] as? String,
            backgroundColor = data["backgroundColor"] as? String,
            textColor = data["textColor"] as? String,
            createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
            expiresAt = (data["expiresAt"] as? Number)?.toLong() ?: 0L
        )
    }

    private fun mapToNoteWithUser(noteId: String, data: Map<String, Any?>, usersMap: Map<String, UserEntity>): Note {
        val authorId = data["authorId"] as? String ?: ""
        val author = usersMap[authorId]?.toDomain() ?: User(
            userId = authorId, username = "Bilinmeyen Kullanıcı", displayName = "Bilinmeyen",
            createdAt = 0L, updatedAt = 0L
        )

        val noteTypeStr = data["noteType"] as? String ?: "TEXT"
        val noteType = try { NoteType.valueOf(noteTypeStr) } catch (_: Exception) { NoteType.TEXT }

        return Note(
            noteId = noteId,
            author = author,
            noteType = noteType,
            content = data["content"] as? String ?: "",
            musicTrackId = data["musicTrackId"] as? String,
            musicTrackName = data["musicTrackName"] as? String,
            musicArtistName = data["musicArtistName"] as? String,
            musicAlbumArt = data["musicAlbumArt"] as? String,
            countdownTargetTime = (data["countdownTargetTime"] as? Number)?.toLong(),
            countdownTitle = data["countdownTitle"] as? String,
            backgroundColor = data["backgroundColor"] as? String,
            textColor = data["textColor"] as? String,
            createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
            expiresAt = (data["expiresAt"] as? Number)?.toLong() ?: 0L
        )
    }
}
