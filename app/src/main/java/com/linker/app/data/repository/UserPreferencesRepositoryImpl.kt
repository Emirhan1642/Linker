package com.linker.app.data.repository

import androidx.annotation.Keep
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.linker.app.core.util.Result
import com.linker.app.core.util.RetryUtil
import com.linker.app.domain.model.ReportReason
import com.linker.app.domain.model.ReportableContentType
import com.linker.app.domain.model.UserPreference
import com.linker.app.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore implementation of [UserPreferencesRepository].
 *
 * Firestore Schema:
 *   users/{userId}/preferences/prefs  (single document per user)
 *     blockedUserIds: List<String>
 *     mutedUserIds: List<String>
 *     interests: List<String>
 *     disinterests: List<String>
 *     reportedContentIds: List<String>
 *
 *   reports/{reportId}  (global collection)
 *     reporterId, contentId, contentType, reason, createdAt
 */
@Singleton
class UserPreferencesRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : UserPreferencesRepository {

    private val usersCollection = firestore.collection("users")
    private val reportsCollection = firestore.collection("reports")

    private fun prefsDoc() = usersCollection
        .document(requireUserId())
        .collection("preferences")
        .document("prefs")

    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")

    // ── Observe ────────────────────────────────────────────────────────────

    override fun observePreferences(): Flow<Result<UserPreference>> = callbackFlow {
        val listener = prefsDoc().addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(Result.Success(UserPreference.EMPTY))
                return@addSnapshotListener
            }
            val prefs = snapshot?.toObject(PrefsDocument::class.java)?.toDomain()
                ?: UserPreference.EMPTY
            trySend(Result.Success(prefs))
        }
        awaitClose { listener.remove() }
    }

    override suspend fun getPreferences(): Result<UserPreference> = RetryUtil.retrySafeCall {
        val doc = prefsDoc().get().await()
        doc.toObject(PrefsDocument::class.java)?.toDomain() ?: UserPreference.EMPTY
    }

    // ── Blocking ───────────────────────────────────────────────────────────

    override suspend fun blockUser(userId: String): Result<Unit> = RetryUtil.retrySafeCall {
        prefsDoc().update(
            mapOf(
                "blockedUserIds" to FieldValue.arrayUnion(userId),
                "mutedUserIds" to FieldValue.arrayRemove(userId) // blocked overrides muted
            )
        ).await()
    }

    override suspend fun unblockUser(userId: String): Result<Unit> = RetryUtil.retrySafeCall {
        prefsDoc().update("blockedUserIds", FieldValue.arrayRemove(userId)).await()
    }

    override suspend fun getBlockedUsers(): Result<List<String>> = RetryUtil.retrySafeCall {
        val doc = prefsDoc().get().await()
        @Suppress("UNCHECKED_CAST")
        (doc.get("blockedUserIds") as? List<String>) ?: emptyList()
    }

    // ── Muting ─────────────────────────────────────────────────────────────

    override suspend fun muteUser(userId: String): Result<Unit> = RetryUtil.retrySafeCall {
        prefsDoc().update("mutedUserIds", FieldValue.arrayUnion(userId)).await()
    }

    override suspend fun unmuteUser(userId: String): Result<Unit> = RetryUtil.retrySafeCall {
        prefsDoc().update("mutedUserIds", FieldValue.arrayRemove(userId)).await()
    }

    override suspend fun getMutedUsers(): Result<List<String>> = RetryUtil.retrySafeCall {
        val doc = prefsDoc().get().await()
        @Suppress("UNCHECKED_CAST")
        (doc.get("mutedUserIds") as? List<String>) ?: emptyList()
    }

    // ── Algorithm Signals ──────────────────────────────────────────────────

    override suspend fun markInterest(contentId: String): Result<Unit> = RetryUtil.retrySafeCall {
        prefsDoc().update(
            mapOf(
                "interests" to FieldValue.arrayUnion(contentId),
                "disinterests" to FieldValue.arrayRemove(contentId)
            )
        ).await()
    }

    override suspend fun markDisinterest(contentId: String): Result<Unit> = RetryUtil.retrySafeCall {
        prefsDoc().update(
            mapOf(
                "disinterests" to FieldValue.arrayUnion(contentId),
                "interests" to FieldValue.arrayRemove(contentId)
            )
        ).await()
    }

    // ── Reporting ──────────────────────────────────────────────────────────

    override suspend fun reportContent(
        contentId: String,
        contentType: ReportableContentType,
        reason: ReportReason
    ): Result<Unit> = RetryUtil.retrySafeCall {
        val currentUserId = requireUserId()
        val reportId = "${currentUserId}_${contentId}"

        // Check for duplicate report
        val existing = reportsCollection.document(reportId).get().await()
        if (existing.exists()) {
            throw IllegalStateException("ALREADY_REPORTED")
        }

        val batch = firestore.batch()

        // Write to global reports collection
        batch.set(reportsCollection.document(reportId), mapOf(
            "reporterId" to currentUserId,
            "contentId" to contentId,
            "contentType" to contentType.firestoreKey,
            "reason" to reason.name,
            "createdAt" to System.currentTimeMillis()
        ))

        // Track in user's preferences so we can prevent duplicate reports in UI
        batch.update(prefsDoc(), "reportedContentIds", FieldValue.arrayUnion(contentId))

        batch.commit().await()
    }

    // ── Data Transfer Objects ──────────────────────────────────────────────

    @Keep
    private data class PrefsDocument(
        val blockedUserIds: List<String> = emptyList(),
        val mutedUserIds: List<String> = emptyList(),
        val interests: List<String> = emptyList(),
        val disinterests: List<String> = emptyList(),
        val reportedContentIds: List<String> = emptyList()
    ) {
        fun toDomain() = UserPreference(
            blockedUserIds = blockedUserIds.toSet(),
            mutedUserIds = mutedUserIds.toSet(),
            interests = interests,
            disinterests = disinterests,
            reportedContentIds = reportedContentIds.toSet()
        )
    }
}
