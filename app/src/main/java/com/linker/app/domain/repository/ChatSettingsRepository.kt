package com.linker.app.domain.repository

import com.linker.app.core.util.Result

/**
 * Repository for chat settings and group management
 */
interface ChatSettingsRepository {

    /** Update chat settings like name, image, permissions */
    suspend fun updateChatSettings(
        chatId: String,
        chatName: String? = null,
        chatImageUrl: String? = null,
        permissions: Map<String, Any>? = null
    ): Result<Unit>

    /** Archive/unarchive chat for current user */
    suspend fun archiveChat(chatId: String, archive: Boolean = true): Result<Unit>

    /** Pin/unpin chat for current user */
    suspend fun pinChat(chatId: String, pin: Boolean = true): Result<Unit>

    /** Mute/unmute chat notifications */
    suspend fun muteChat(chatId: String, mute: Boolean = true): Result<Unit>

    /** Block/unblock chat */
    suspend fun blockChat(chatId: String, block: Boolean = true): Result<Unit>

    /** Add participants to group chat */
    suspend fun addParticipants(chatId: String, userIds: List<String>): Result<Unit>

    /** Remove participant from group chat */
    suspend fun removeParticipant(chatId: String, userId: String): Result<Unit>

    /** Leave group chat */
    suspend fun leaveGroupChat(chatId: String): Result<Unit>

    /** Transfer group ownership */
    suspend fun transferGroupOwnership(chatId: String, newOwnerId: String): Result<Unit>

    /** Promote participant to admin */
    suspend fun promoteToAdmin(chatId: String, userId: String): Result<Unit>
}
