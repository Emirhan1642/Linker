package com.linker.app.domain.repository

import com.linker.app.core.util.Result

/**
 * Update parameters for chat settings
 */
data class ChatSettingsUpdate(
    val chatName: String? = null,
    val chatImageUrl: String? = null,
    val permissions: Map<String, Any>? = null
)

/**
 * Repository for chat settings and group management.
 * 
 * ## Permissions
 * - Group profile updates: Requires Admin permission.
 * - Add participants: Requires Member permission (or Admin if restricted).
 * - Remove participants: Requires Admin permission.
 * - Promote/Demote/Transfer: Requires Owner permission.
 */
interface ChatSettingsRepository {

    /** Update chat settings like name, image, permissions. */
    suspend fun updateChatSettings(
        chatId: String,
        update: ChatSettingsUpdate
    ): Result<Unit>

    /** Archive/unarchive chat for current user. */
    suspend fun setArchived(chatId: String, isArchived: Boolean): Result<Unit>

    /** Pin/unpin chat for current user. */
    suspend fun setPinned(chatId: String, isPinned: Boolean): Result<Unit>

    /** Mute/unmute chat notifications. */
    suspend fun setMuted(chatId: String, isMuted: Boolean): Result<Unit>

    /** Block/unblock chat. */
    suspend fun setBlocked(chatId: String, isBlocked: Boolean): Result<Unit>

    /** Add participants to group chat. */
    suspend fun addParticipants(chatId: String, userIds: List<String>): Result<Unit>

    /** Remove participant from group chat. */
    suspend fun removeParticipant(chatId: String, userId: String): Result<Unit>
    
    /** Remove multiple participants from group chat. */
    suspend fun removeParticipants(chatId: String, userIds: List<String>): Result<Unit>

    /** Leave group chat. */
    suspend fun leaveGroupChat(chatId: String): Result<Unit>

    /** Transfer group ownership. Requires Owner permission. */
    suspend fun transferGroupOwnership(chatId: String, newOwnerId: String): Result<Unit>

    /** Promote participant to admin. Requires Admin/Owner permission. */
    suspend fun promoteToAdmin(chatId: String, userId: String): Result<Unit>
    
    /** Demote participant from admin. Requires Admin/Owner permission. */
    suspend fun demoteAdmin(chatId: String, userId: String): Result<Unit>
    
    /** Update group profile (name/image). Requires Admin permission. */
    suspend fun updateGroupProfile(chatId: String, name: String?, imageUrl: String?): Result<Unit>
}
