package com.linker.app.domain.model

/**
 * State management for message list with diff optimization
 * Reduces recompositions by tracking only changed messages
 */
data class MessageListState(
    val messages: List<MessageUiModel> = emptyList(),
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isInitialLoading: Boolean = true
) {
    /**
     * Update a specific message in the list
     */
    fun updateMessage(
        messageId: String,
        update: MessageUiModel.() -> MessageUiModel
    ): MessageListState {
        val index = messages.indexOfFirst { it.messageId == messageId }
        if (index == -1) return this

        val updatedMessages = messages.toMutableList().apply {
            this[index] = this[index].update()
        }

        return copy(messages = updatedMessages)
    }

    /**
     * Add a new message to the list
     */
    fun addMessage(message: MessageUiModel): MessageListState {
        // Check if message already exists
        if (messages.any { it.messageId == message.messageId }) {
            return updateMessage(message.messageId) { message }
        }
        return copy(messages = messages + message)
    }

    /**
     * Remove a message from the list
     */
    fun removeMessage(messageId: String): MessageListState {
        return copy(messages = messages.filter { it.messageId != messageId })
    }

    /**
     * Merge new messages with existing list, preserving UI state
     */
    fun mergeMessages(newMessages: List<MessageUiModel>): MessageListState {
        val currentMap = messages.associateBy { it.messageId }

        val merged = newMessages.map { newMsg ->
            val existing = currentMap[newMsg.messageId]
            if (existing != null) {
                // Preserve UI-specific fields like animation state
                newMsg.copy(
                    isAnimated = existing.isAnimated || newMsg.isAnimated
                )
            } else {
                newMsg
            }
        }

        return copy(messages = merged, isInitialLoading = false)
    }
}

/**
 * UI model for message display
 */
data class MessageUiModel(
    val messageId: String,
    val chatId: String,
    val senderId: String,
    val senderName: String,
    val senderAvatarUrl: String?,
    val content: String?,
    val messageType: MessageType,
    val mediaUrl: String?,
    val thumbnailUrl: String?,
    val replyToMessage: MessagePreview?,
    val reactions: Map<String, String>, // userId -> emoji
    val isSelf: Boolean,
    val isEdited: Boolean,
    val isDeleted: Boolean,
    val timestamp: Long,
    val status: MessageStatus,
    val isAnimated: Boolean = false,
    val canDelete: Boolean = true,
    val canEdit: Boolean = false,
    val canReact: Boolean = true
)

/**
 * Preview of a message for reply display
 */
data class MessagePreview(
    val messageId: String,
    val senderName: String,
    val content: String?,
    val messageType: MessageType
)
