package com.linker.app.domain.model

/**
 * Domain model for user content preferences and moderation settings.
 *
 * Stores the current user's algorithm signals and social safety controls.
 * This is a per-user document stored in Firestore under
 * `users/{userId}/preferences/{userId}`.
 *
 * @property blockedUserIds Set of user IDs the current user has blocked.
 *   Blocked users cannot see this user's content, and this user cannot
 *   see blocked users' content.
 * @property mutedUserIds Set of user IDs whose content is silenced.
 *   The current user still follows muted users, but their Stories and
 *   Links are hidden from feeds and the Story grid.
 * @property interests Content tags/topics the user has expressed interest in.
 *   Used as positive signals for the recommendation algorithm.
 * @property disinterests Content tags/topics the user is not interested in.
 *   Used as negative signals for the recommendation algorithm.
 * @property reportedContentIds Set of content IDs already reported by this user.
 *   Prevents duplicate reports on the same content.
 */
data class UserPreference(
    val blockedUserIds: Set<String> = emptySet(),
    val mutedUserIds: Set<String> = emptySet(),
    val interests: List<String> = emptyList(),
    val disinterests: List<String> = emptyList(),
    val reportedContentIds: Set<String> = emptySet()
) {
    init {
        // Blocked and muted sets should not overlap for clarity
        // (blocked takes precedence; no need to also mute)
        require(blockedUserIds.none { it.isBlank() }) { "blockedUserIds must not contain blank IDs" }
        require(mutedUserIds.none { it.isBlank() }) { "mutedUserIds must not contain blank IDs" }
    }

    /** Whether a given user is blocked. */
    fun isBlocked(userId: String): Boolean = userId in blockedUserIds

    /** Whether a given user's content is muted (silenced). */
    fun isMuted(userId: String): Boolean = userId in mutedUserIds

    /** Whether a given content item has already been reported. */
    fun isReported(contentId: String): Boolean = contentId in reportedContentIds

    /** Returns a copy with the given user added to the blocked set. */
    fun withBlocked(userId: String): UserPreference = copy(
        blockedUserIds = blockedUserIds + userId,
        // Also remove from muted if present — blocked takes precedence
        mutedUserIds = mutedUserIds - userId
    )

    /** Returns a copy with the given user removed from the blocked set. */
    fun withUnblocked(userId: String): UserPreference = copy(
        blockedUserIds = blockedUserIds - userId
    )

    /** Returns a copy with the given user's content muted. */
    fun withMuted(userId: String): UserPreference = copy(
        mutedUserIds = mutedUserIds + userId
    )

    /** Returns a copy with the given user's content unmuted. */
    fun withUnmuted(userId: String): UserPreference = copy(
        mutedUserIds = mutedUserIds - userId
    )

    /** Returns a copy with an interest signal added. */
    fun withInterest(tag: String): UserPreference = copy(
        interests = (interests + tag).distinct().take(MAX_SIGNALS),
        disinterests = disinterests - tag
    )

    /** Returns a copy with a disinterest signal added. */
    fun withDisinterest(tag: String): UserPreference = copy(
        disinterests = (disinterests + tag).distinct().take(MAX_SIGNALS),
        interests = interests - tag
    )

    /** Returns a copy with the given content ID marked as reported. */
    fun withReported(contentId: String): UserPreference = copy(
        reportedContentIds = reportedContentIds + contentId
    )

    companion object {
        /** Maximum number of interest/disinterest signals stored. */
        const val MAX_SIGNALS = 100

        /** An empty preference set (new user defaults). */
        val EMPTY = UserPreference()
    }
}

/**
 * Supported content report reasons.
 *
 * @property displayName User-facing label shown in the report sheet.
 */
enum class ReportReason(val displayName: String) {
    SPAM("Spam veya yanıltıcı"),
    INAPPROPRIATE("Uygunsuz içerik"),
    HARASSMENT("Taciz veya zorbalık"),
    HATE_SPEECH("Nefret söylemi"),
    MISINFORMATION("Yanlış bilgi"),
    COPYRIGHT("Telif hakkı ihlali"),
    VIOLENCE("Şiddet veya tehlike"),
    OTHER("Diğer")
}

/**
 * Content types that can be reported.
 */
enum class ReportableContentType(val firestoreKey: String) {
    STORY("story"),
    LINK("link"),
    COMMENT("comment"),
    NOTE("note"),
    USER("user")
}
