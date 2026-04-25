package com.linker.app.presentation.screens.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.linker.app.R
import com.linker.app.domain.model.MessageStatus
import com.linker.app.presentation.components.LinkerAvatar
import com.linker.app.presentation.screens.chat.components.ChatBubble
import com.linker.app.presentation.screens.chat.components.ChatHeader
import com.linker.app.presentation.screens.chat.components.ChatInputBar
import com.linker.app.presentation.screens.chat.components.ChatProfileHeader
import com.linker.app.presentation.screens.chat.components.MessageContextMenu
import com.linker.app.presentation.screens.chat.components.MessageInfoBottomSheet
import com.linker.app.presentation.screens.chat.components.ReplyPreviewHologram
import com.linker.app.presentation.theme.TextPrimary
import com.linker.app.presentation.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.widget.Toast

// Data classes imported directly from ChatViewModel
import com.linker.app.presentation.screens.chat.MessageUiModel
import com.linker.app.presentation.screens.chat.ReplyInfo
import com.linker.app.presentation.screens.chat.ReactionUserInfo
import com.linker.app.presentation.screens.chat.MessageInfoState
import com.linker.app.presentation.screens.chat.ReadReceiptInfo as ReadReceipt
import com.linker.app.presentation.screens.chat.ParticipantReceiptInfo as DeliveryReceipt
import com.linker.app.presentation.screens.chat.ReplyPreview
import com.linker.app.presentation.screens.chat.MessageItem

/**
 * Main chat message screen - Refactored to use smaller components
 * Original file was 1313 lines, now simplified using component separation
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatMessageScreen(
    chatId: String,
    onNavigateBack: () -> Unit,
    onNavigateToInfo: () -> Unit,
    onNavigateToUserProfile: (String) -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    var messageText by remember { mutableStateOf("") }
    val uiState by viewModel.messageState.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val inputFocusRequester = remember { FocusRequester() }
    val haptic = LocalHapticFeedback.current
    val clipboardManager = LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var highlightMessageId by remember { mutableStateOf<String?>(null) }

    // UI States
    var showContextMenu by remember { mutableStateOf(false) }
    var selectedMessage by remember { mutableStateOf<MessageUiModel?>(null) }
    var selectedMessageBounds by remember { mutableStateOf<Rect?>(null) }
    val messageBounds = remember { mutableMapOf<String, Rect>() }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showMessageInfo by remember { mutableStateOf(false) }
    var showReactionsSheet by remember { mutableStateOf(false) }
    var showSeenBySheet by remember { mutableStateOf(false) }
    var reactionsMessageId by remember { mutableStateOf<String?>(null) }
    var replyToMessage by remember { mutableStateOf<MessageUiModel?>(null) }
    var seenByUsersForSheet by remember { mutableStateOf<List<SeenByUserUi>>(emptyList()) }
    var quickReactions by rememberSaveable {
        mutableStateOf(listOf("\uD83D\uDC4D", "\u2764\uFE0F", "\uD83D\uDE02", "\uD83D\uDE2E", "\uD83D\uDE22", "\uD83D\uDE4F"))
    }

    val messageInfoState by viewModel.messageInfoState.collectAsState()
    val messageReactionsState by viewModel.messageReactionsState.collectAsState()

    // Effects
    LaunchedEffect(chatId) {
        viewModel.openChat(chatId)
    }

    LaunchedEffect(uiState.messages.size, uiState.isSending) {
        val headerCount = 1
        val emptyMessageCount = if (uiState.messages.isEmpty()) 1 else 0
        val sendingCount = if (uiState.isSending) 1 else 0
        val errorCount = if (uiState.sendError != null) 1 else 0
        val totalItems = headerCount + emptyMessageCount + uiState.messages.size + sendingCount + errorCount
        val targetIndex = if (totalItems > 0) totalItems - 1 else 0
        try {
            listState.animateScrollToItem(targetIndex)
        } catch (_: Exception) { }
    }

    LaunchedEffect(replyToMessage?.messageId) {
        if (replyToMessage != null) {
            try {
                inputFocusRequester.requestFocus()
            } catch (_: Exception) { }
        }
    }

    // Message Info Bottom Sheet
    if (showMessageInfo) {
        MessageInfoBottomSheet(
            state = messageInfoState,
            isGroupChat = uiState.isGroupChat,
            onDismiss = { showMessageInfo = false },
            onNavigateToUserProfile = onNavigateToUserProfile,
            onScrollToReply = { replyId ->
                val idx = uiState.messages.indexOfFirst { it.messageId == replyId }
                if (idx >= 0) {
                    coroutineScope.launch {
                        try {
                            listState.animateScrollToItem(idx)
                        } catch (_: Exception) { }
                    }
                    highlightMessageId = replyId
                    coroutineScope.launch {
                        delay(1200)
                        if (highlightMessageId == replyId) {
                            highlightMessageId = null
                        }
                    }
                }
            }
        )
    }

    // Reactions Sheet
    if (showReactionsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showReactionsSheet = false },
            containerColor = Color(0xFF1C1C20)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Reactions", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                if (messageReactionsState.isLoading) {
                    CircularProgressIndicator(color = TextSecondary, modifier = Modifier.size(20.dp))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().height(250.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(messageReactionsState.reactions.size) { index ->
                            val reaction = messageReactionsState.reactions[index]
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                LinkerAvatar(
                                    imageUrl = reaction.avatarUrl,
                                    size = 40.dp,
                                    hasStory = false,
                                    onClick = { 
                                        if (reaction.userId.isNotBlank()) {
                                            onNavigateToUserProfile(reaction.userId)
                                            showReactionsSheet = false
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = reaction.userName,
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = reaction.emoji,
                                    fontSize = 24.sp
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    if (showSeenBySheet) {
        ModalBottomSheet(
            onDismissRequest = { showSeenBySheet = false },
            containerColor = Color(0xFF1C1C20)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Seen by",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (seenByUsersForSheet.isEmpty()) {
                    Text(
                        text = "No viewers yet",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(seenByUsersForSheet) { viewer ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                LinkerAvatar(
                                    imageUrl = viewer.avatarUrl,
                                    size = 40.dp,
                                    hasStory = false
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = viewer.displayName,
                                        color = TextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = formatSeenLabel(viewer.seenAt),
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isDark = isSystemInDarkTheme()
        val doodleRes = if (isDark) R.drawable.bg_doodle_dark else R.drawable.bg_doodle_light
        val doodlePainter = painterResource(id = doodleRes)
        val containerRatio = maxWidth.value / maxHeight.value
        val intrinsic = doodlePainter.intrinsicSize
        val imageRatio = if (intrinsic.isSpecified && intrinsic.height != 0f) {
            intrinsic.width / intrinsic.height
        } else {
            containerRatio
        }
        val contentScale = if (imageRatio > containerRatio) ContentScale.FillHeight else ContentScale.FillWidth

        Image(
            painter = doodlePainter,
            contentDescription = null,
            modifier = Modifier.matchParentSize().alpha(0.1f),
            contentScale = contentScale,
        )

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .then(if (showContextMenu) Modifier.blur(14.dp) else Modifier),
            containerColor = Color.Transparent,
            bottomBar = {
                if (!uiState.canSendMessages) {
                    // Group restriction: only admins can send messages
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1C1C20))
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .navigationBarsPadding()
                            .imePadding(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Only admins can send messages",
                            color = TextSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    ChatInputBar(
                        text = messageText,
                        onTextChange = { messageText = it },
                        replyPreview = replyToMessage?.let { msg ->
                            val senderName = if (msg.isSelf) "You" else {
                                if (uiState.isGroupChat) msg.senderDisplayName else uiState.recipientName
                            }
                            ReplyPreview(
                                senderName = senderName,
                                previewText = msg.content ?: "[Media]",
                                isSelf = msg.isSelf
                            )
                        },
                        onCancelReply = { replyToMessage = null },
                        onSend = {
                            if (messageText.isNotBlank() && !uiState.isSending) {
                                viewModel.sendMessage(messageText, replyToMessage?.messageId)
                                replyToMessage = null
                                messageText = ""
                            }
                        },
                        isSending = uiState.isSending,
                        focusRequester = inputFocusRequester
                    )
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Header
                ChatHeader(
                    recipientName = uiState.recipientName,
                    recipientUsername = uiState.recipientUsername.takeIf { it.isNotBlank() },
                    recipientImageUrl = uiState.recipientImageUrl,
                    onNavigateBack = onNavigateBack,
                    onNavigateToInfo = onNavigateToInfo
                )

                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = TextSecondary)
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item {
                            ChatProfileHeader(
                                recipientName = uiState.recipientName,
                                recipientUsername = uiState.recipientUsername.takeIf { it.isNotBlank() },
                                recipientImageUrl = uiState.recipientImageUrl
                            )
                        }

                        if (uiState.messages.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(100.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Send a message to start the conversation!", color = TextSecondary, fontSize = 14.sp)
                                }
                            }
                        }

                        itemsIndexed(uiState.messages) { index, msg ->
                            val prevIsSelf = if (index > 0) uiState.messages[index - 1].isSelf else !msg.isSelf
                            val nextIsSelf = if (index < uiState.messages.size - 1) uiState.messages[index + 1].isSelf else !msg.isSelf

                            // Reply preview if exists
                            val repliedIndex = msg.replyToMessageId?.let { id ->
                                uiState.messages.indexOfFirst { it.messageId == id }
                            } ?: -1

                            if (!msg.replyToMessageId.isNullOrBlank()) {
                                val preview = if (repliedIndex >= 0) {
                                    val replied = uiState.messages[repliedIndex]
                                    val repliedName = if (replied.isSelf) {
                                        "You"
                                    } else {
                                        if (uiState.isGroupChat) replied.senderDisplayName else uiState.recipientName
                                    }
                                    ReplyPreview(
                                        senderName = repliedName,
                                        previewText = replied.content ?: "[Media]",
                                        isSelf = replied.isSelf
                                    )
                                } else {
                                    ReplyPreview(
                                        senderName = "Replied message",
                                        previewText = "[Previous message]",
                                        isSelf = false
                                    )
                                }
                                ReplyPreviewHologram(
                                    preview = preview,
                                    alignEnd = msg.isSelf,
                                    alpha = 0.7f
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            // Message row with avatar for group chat
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (msg.isSelf) Arrangement.End else Arrangement.Start,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                if (!msg.isSelf && uiState.isGroupChat) {
                                    LinkerAvatar(
                                        imageUrl = msg.senderAvatarUrl,
                                        size = 36.dp,
                                        hasStory = false,
                                        onClick = {
                                            if (msg.senderId.isNotBlank()) {
                                                onNavigateToUserProfile(msg.senderId)
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }

                                // Chat Bubble
                                ChatBubble(
                                    message = MessageItem(
                                        text = msg.content ?: "",
                                        isSelf = msg.isSelf,
                                        status = msg.status,
                                        prevIsSelf = prevIsSelf,
                                        nextIsSelf = nextIsSelf
                                    ),
                                    coroutineScope = coroutineScope,
                                    onBubblePositioned = { bounds ->
                                        messageBounds[msg.messageId] = bounds
                                    },
                                    onLongPress = {
                                        selectedMessage = msg
                                        selectedMessageBounds = messageBounds[msg.messageId]
                                        showEmojiPicker = false
                                        showContextMenu = true
                                    },
                                    onSwipeReply = {
                                        replyToMessage = msg
                                        showContextMenu = false
                                        showEmojiPicker = false
                                    },
                                    isHighlighted = highlightMessageId == msg.messageId,
                                    onHaptic = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                )
                            }

                            // Reaction summary
                            val reactionSummary = buildReactionSummary(msg.reactions)
                            if (reactionSummary.isNotEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = if (msg.isSelf) Alignment.CenterEnd else Alignment.CenterStart
                                ) {
                                    ReactionSummaryRow(
                                        emojis = reactionSummary,
                                        modifier = Modifier.clickable {
                                            reactionsMessageId = msg.messageId
                                            viewModel.loadMessageReactions(msg.messageId, msg.reactions)
                                            showReactionsSheet = true
                                        }
                                    )
                                }
                            }

                            // Seen indicator for last self message
                            val isLastSelfMessage = msg.isSelf && uiState.messages.drop(index + 1).none { it.isSelf }
                            if (isLastSelfMessage && msg.readAt != null) {
                                if (uiState.isGroupChat && msg.seenByUsers.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        msg.seenByUsers.take(5).forEachIndexed { viewerIndex, viewer ->
                                            Box(
                                                modifier = Modifier
                                                    .offset(x = if (viewerIndex == 0) 0.dp else (-8).dp)
                                                    .clickable {
                                                        seenByUsersForSheet = msg.seenByUsers
                                                        showSeenBySheet = true
                                                    }
                                            ) {
                                                LinkerAvatar(
                                                    imageUrl = viewer.avatarUrl,
                                                    size = 18.dp,
                                                    hasStory = false
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Text(
                                        text = formatSeenLabel(msg.readAt),
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(end = 12.dp, top = 2.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                                    )
                                }
                            }
                        }

                        // Sending indicator
                        if (uiState.isSending) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color(0xFF007E8E),
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                        }

                        // Error message
                        if (uiState.sendError != null) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = uiState.sendError!!,
                                        color = Color(0xFFFF4B4B),
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Context Menu
        if (showContextMenu && selectedMessage != null && selectedMessageBounds != null) {
            val constraints = this.constraints
            MessageContextMenu(
                message = selectedMessage!!,
                messageBounds = selectedMessageBounds!!,
                screenWidth = constraints.maxWidth.toFloat(),
                screenHeight = constraints.maxHeight.toFloat(),
                quickReactions = quickReactions,
                onDismiss = {
                    showContextMenu = false
                    showEmojiPicker = false
                },
                onReply = {
                    replyToMessage = selectedMessage
                    showContextMenu = false
                },
                onCopy = { 
                    selectedMessage?.content?.let { text ->
                        clipboardManager.setText(AnnotatedString(text))
                    }
                    showContextMenu = false 
                },
                onForward = {
                    Toast.makeText(context, "Forward feature coming soon!", Toast.LENGTH_SHORT).show()
                    showContextMenu = false 
                },
                onInfo = {
                    viewModel.loadMessageInfo(selectedMessage!!.messageId)
                    showMessageInfo = true
                    showContextMenu = false
                },
                onDelete = if (selectedMessage!!.isSelf) {
                    {
                        viewModel.deleteMessage(selectedMessage!!.messageId)
                        showContextMenu = false
                    }
                } else null,
                onReaction = { emoji ->
                    viewModel.reactToMessage(selectedMessage!!.messageId, emoji)
                    showContextMenu = false
                    showEmojiPicker = false
                },
                onShowMoreReactions = {
                    showEmojiPicker = true
                },
                showEmojiPicker = showEmojiPicker
            )
        }
    }
}

private fun buildReactionSummary(reactions: Map<String, String>): List<String> {
    return reactions.values.groupBy { it }
        .map { (emoji, list) -> if (list.size > 1) "$emoji ${list.size}" else emoji }
        .take(3)
}

@Composable
fun ReactionSummaryRow(
    emojis: List<String>,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF2C2C2E)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            emojis.forEach { emoji ->
                Text(
                    text = emoji,
                    fontSize = 14.sp
                )
            }
        }
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60000 -> "just now"
        diff < 3600000 -> "${diff / 60000}m ago"
        diff < 86400000 -> "${diff / 3600000}h ago"
        diff < 604800000 -> "${diff / 86400000}d ago"
        else -> "${diff / 604800000}w ago"
    }
}

private fun formatSeenLabel(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val days = diff / 86_400_000L
    return when {
        diff < 60_000L -> "Just seen"
        days == 1L -> "Seen yesterday"
        days > 1L -> "Seen ${days}d ago"
        else -> "Seen ${formatRelativeTime(timestamp)}"
    }
}
