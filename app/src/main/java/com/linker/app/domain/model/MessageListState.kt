package com.linker.app.domain.model

/**
 * State management for message list with diff optimization.
 *
 * Uses map-based internal storage for O(1) message lookups and updates.
 * Reduces recompositions by tracking only changed messages.
 * Supports pagination, error states, and message-level updates.
 *
 * @property messagesById Internal map of messages by ID (for fast lookups).
 * @property messageOrder Ordered list of message IDs (for display order).
 * @property hasMore Whether there are older messages to load.
 * @property isLoadingMore Whether a pagination load is in progress.
 * @property isInitialLoading Whether the initial message load is in progress.
 * @property error Error message from the last failed operation (null if no error).
 * @property lastLoadedTimestamp Timestamp of the oldest loaded message (for cursor pagination).
 */
data class MessageListState(
    private val messagesById: Map<String, MessageUiModel> = emptyMap(),
    private val messageOrder: List<String> = emptyList(),
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isInitialLoading: Boolean = true,
    val error: String? = null,
    val lastLoadedTimestamp: Long? = null
) {
    /** Public accessor for messages as a list (in display order). */
    val messages: List<MessageUiModel>
        get() = messageOrder.mapNotNull { messagesById[it] }

    /**
     * Get a specific message by ID (O(1) lookup).
     */
    fun getMessage(messageId: String): MessageUiModel? = messagesById[messageId]

    /**
     * Update a specific message in the list (O(1) operation).
     */
    fun updateMessage(
        messageId: String,
        update: MessageUiModel.() -> MessageUiModel
    ): MessageListState {
        val message = messagesById[messageId] ?: return this
        val updatedMessage = message.update()

        return copy(
            messagesById = messagesById + (messageId to updatedMessage)
        )
    }

    /**
     * Add a new message to the list.
     */
    fun addMessage(message: MessageUiModel): MessageListState {
        // Check if message already exists
        if (messagesById.containsKey(message.messageId)) {
            return updateMessage(message.messageId) { message }
        }

        return copy(
            messagesById = messagesById + (message.messageId to message),
            messageOrder = messageOrder + message.messageId
        )
    }

    /**
     * Remove a message from the list (O(1) map removal + O(n) list filter).
     */
    fun removeMessage(messageId: String): MessageListState {
        return copy(
            messagesById = messagesById - messageId,
            messageOrder = messageOrder.filter { it != messageId }
        )
    }

    /**
     * Merge new messages with existing list, preserving UI state.
     */
    fun mergeMessages(newMessages: List<MessageUiModel>): MessageListState {
        val newMessagesMap = newMessages.associateBy { it.messageId }

        val mergedMap = newMessagesMap.mapValues { (id, newMsg) ->
            val existing = messagesById[id]
            if (existing != null) {
                // Preserve UI-specific fields like animation state
                newMsg.copy(
                    isAnimated = existing.isAnimated || newMsg.isAnimated
                )
            } else {
                newMsg
            }
        }

        val newOrder = newMessages.map { it.messageId }

        return copy(
            messagesById = messagesById + mergedMap,
            messageOrder = newOrder,
            isInitialLoading = false
        )
    }

    /**
     * Whether the list should attempt to load more messages.
     * Returns true when there are more messages, no error, and not already loading.
     */
    fun shouldLoadMore(): Boolean = hasMore && !isLoadingMore && error == null

    /**
     * Returns a copy in the "loading more" state.
     */
    fun startLoadingMore(): MessageListState = copy(isLoadingMore = true, error = null)

    /**
     * Returns a copy after a successful load-more operation.
     * @param newMessages The newly loaded messages to prepend.
     * @param hasMore Whether there are still more messages to load.
     */
    fun completeLoadingMore(
        newMessages: List<MessageUiModel>,
        hasMore: Boolean
    ): MessageListState {
        val newMessagesMap = newMessages.associateBy { it.messageId }
        val newOrder = newMessages.map { it.messageId }

        val oldestTimestamp = (newMessages + messages).minOfOrNull { it.timestamp }

        return copy(
            messagesById = messagesById + newMessagesMap,
            messageOrder = newOrder + messageOrder,
            isLoadingMore = false,
            hasMore = hasMore,
            lastLoadedTimestamp = oldestTimestamp
        )
    }

    /**
     * Returns a copy in the error state.
     */
    fun setError(errorMessage: String): MessageListState = copy(
        isLoadingMore = false,
        error = errorMessage
    )

    /**
     * Clears the error and allows retry.
     */
    fun retry(): MessageListState = copy(error = null)

    /**
     * Returns the timestamp of the oldest message, or null if the list is empty.
     */
    fun getOldestTimestamp(): Long? = messages.minOfOrNull { it.timestamp }

    /**
     * Returns the timestamp of the newest message, or null if the list is empty.
     */
    fun getNewestTimestamp(): Long? = messages.maxOfOrNull { it.timestamp }

    /**
     * Returns the number of messages in the list.
     */
    val size: Int
        get() = messageOrder.size

    /**
     * Whether the message list is empty.
     */
    val isEmpty: Boolean
        get() = messageOrder.isEmpty()
}

/**
 * UI model for message display in the chat screen.
 *
 * @property messageId Unique message identifier.
 * @property chatId ID of the chat this message belongs to.
 * @property senderId User ID of the sender.
 * @property senderName Display name of the sender.
 * @property senderAvatarUrl Sender's profile image URL.
 * @property content Text content of the message.
 * @property messageType Type of message (TEXT, IMAGE, etc.).
 * @property mediaUrl URL to attached media.
 * @property thumbnailUrl Thumbnail preview for media.
 * @property replyToMessage Preview of the message being replied to.
 * @property reactions User reactions map (userId → emoji).
 * @property isSelf Whether the current user sent this message.
 * @property isEdited Whether the message has been edited.
 * @property isDeleted Whether the message has been deleted.
 * @property timestamp Message creation timestamp (epoch ms).
 * @property status Current delivery status.
 * @property isAnimated Whether the message entry animation has played.
 * @property canDelete Whether the current user can delete this message.
 * @property canEdit Whether the current user can edit this message.
 * @property canReact Whether reactions are enabled for this message.
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
    val reactions: Map<String, String>, // userId → emoji
    val isSelf: Boolean,
    val isEdited: Boolean,
    val isDeleted: Boolean,
    val timestamp: Long,
    val status: MessageStatus,
    val isAnimated: Boolean = false,
    val canDelete: Boolean = true,
    val canEdit: Boolean = false,
    val canReact: Boolean = true
) {
    init {
        require(messageId.isNotBlank()) { "messageId cannot be blank" }
        require(chatId.isNotBlank()) { "chatId cannot be blank" }
        require(senderId.isNotBlank()) { "senderId cannot be blank" }
        require(senderName.isNotBlank()) { "senderName cannot be blank" }
        require(timestamp > 0) { "timestamp must be positive" }
    }
}

/**
 * Preview of a message for reply display.
 *
 * @property messageId ID of the original message.
 * @property senderName Display name of the original sender.
 * @property content Text content of the original message.
 * @property messageType Type of the original message.
 */
data class MessagePreview(
    val messageId: String,
    val senderName: String,
    val content: String?,
    val messageType: MessageType
)
