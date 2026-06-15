package com.linker.app.domain.repository

import com.linker.app.domain.model.ReportReason
import com.linker.app.domain.model.ReportableContentType
import com.linker.app.domain.model.UserPreference
import com.linker.app.core.util.Result
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for user content preferences and moderation controls.
 *
 * Covers:
 * - Blocking users (bidirectional content hiding)
 * - Muting users (hide content, keep following)
 * - Content interest/disinterest signals for the feed algorithm
 * - Reporting content or users for policy violations
 */
interface UserPreferencesRepository {

    /**
     * Observes the current user's preferences in real-time.
     * Emits whenever any preference field changes (block, mute, interests, etc.).
     */
    fun observePreferences(): Flow<Result<UserPreference>>

    /**
     * Returns the current user's preferences as a one-shot read.
     */
    suspend fun getPreferences(): Result<UserPreference>

    // ── Blocking ──────────────────────────────────────────────────────────

    /**
     * Blocks a user.
     *
     * Effects:
     * - The blocked user's Stories and Links disappear from the current user's feeds.
     * - The current user's content is hidden from the blocked user.
     * - Any existing follow relationship is removed.
     * - The blocked user cannot send DMs to the current user.
     */
    suspend fun blockUser(userId: String): Result<Unit>

    /**
     * Unblocks a previously blocked user.
     * Previous follow relationships are NOT automatically restored.
     */
    suspend fun unblockUser(userId: String): Result<Unit>

    /**
     * Returns IDs of all users blocked by the current user.
     */
    suspend fun getBlockedUsers(): Result<List<String>>

    // ── Muting ────────────────────────────────────────────────────────────

    /**
     * Mutes a user's content.
     *
     * Effects:
     * - The muted user's Stories are hidden from the Story grid.
     * - The muted user's Links are hidden from the feed.
     * - The follow relationship is preserved — the user appears in the
     *   following list and their profile is still accessible.
     * - Unlike blocking, the muted user is unaware they are muted.
     */
    suspend fun muteUser(userId: String): Result<Unit>

    /**
     * Unmutes a previously muted user, restoring their content to feeds.
     */
    suspend fun unmuteUser(userId: String): Result<Unit>

    /**
     * Returns IDs of all users muted by the current user.
     */
    suspend fun getMutedUsers(): Result<List<String>>

    // ── Algorithm Signals ─────────────────────────────────────────────────

    /**
     * Marks a content item as interesting.
     * Stores the content's tag(s) as positive signals for the algorithm.
     *
     * @param contentId ID of the Story or Link the user is interested in.
     */
    suspend fun markInterest(contentId: String): Result<Unit>

    /**
     * Marks a content item as uninteresting.
     * Stores the content's tag(s) as negative signals for the algorithm.
     * The content and similar content will appear less frequently in the feed.
     *
     * @param contentId ID of the Story or Link the user is not interested in.
     */
    suspend fun markDisinterest(contentId: String): Result<Unit>

    // ── Content Reporting ─────────────────────────────────────────────────

    /**
     * Reports a piece of content for policy violations.
     *
     * @param contentId ID of the Story, Link, Comment, Note, or User.
     * @param contentType Type discriminator for Firestore routing.
     * @param reason The user-selected report reason.
     *
     * Note: A user can only report the same content once. Duplicate reports
     * return [Result.Error] with code "ALREADY_REPORTED".
     */
    suspend fun reportContent(
        contentId: String,
        contentType: ReportableContentType,
        reason: ReportReason
    ): Result<Unit>
}
