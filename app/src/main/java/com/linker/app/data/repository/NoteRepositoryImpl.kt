package com.linker.app.data.repository

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
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : NoteRepository {
    private val notesCollection = firestore.collection("notes")

    private val currentUserId: String
        get() = auth.currentUser?.uid ?: ""

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
                val notes = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { mapToNote(doc.id, it) }
                } ?: emptyList()
                trySend(notes)
            }
        awaitClose { listener.remove() }
    }

    /** Post a new text note (expires in 24 hours). */
    override suspend fun postNote(content: String): Result<Note> {
        return try {
            val noteId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val expiresAt = now + 24 * 60 * 60 * 1000 // 24 hours

            val noteData = hashMapOf(
                "authorId" to currentUserId,
                "noteType" to "TEXT",
                "content" to content,
                "backgroundColor" to null,
                "textColor" to null,
                "createdAt" to now,
                "expiresAt" to expiresAt
            )
            notesCollection.document(noteId).set(noteData).await()

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
                    content = content,
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
            notesCollection.document(noteId).delete().await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error", e.toString())
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
}
