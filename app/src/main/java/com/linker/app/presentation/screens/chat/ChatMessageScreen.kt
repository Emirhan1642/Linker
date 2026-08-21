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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.linker.app.R
import com.linker.app.presentation.components.LinkerAvatar
import com.linker.app.presentation.components.StoryState
import com.linker.app.presentation.screens.chat.components.ChatBubble
import com.linker.app.presentation.screens.chat.components.ChatHeader
import com.linker.app.presentation.screens.chat.components.ChatInputBar
import com.linker.app.presentation.screens.chat.components.ChatProfileHeader
import com.linker.app.presentation.screens.chat.components.MessageContextMenu
import com.linker.app.presentation.screens.chat.components.MessageInfoBottomSheet
import com.linker.app.presentation.screens.chat.components.ReplyPreviewHologram
import com.linker.app.presentation.screens.chat.components.ReactionSummaryRow
import com.linker.app.presentation.screens.chat.components.ReactionsBottomSheet
import com.linker.app.presentation.screens.chat.components.SeenByBottomSheet
import com.linker.app.presentation.theme.TextPrimary
import com.linker.app.presentation.theme.TextSecondary
import com.linker.app.core.util.formatRelativeTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatMessageScreen(
    chatId: String,
    onNavigateBack: () -> Unit,
    onNavigateToInfo: () -> Unit,
    onNavigateToUserProfile: (String) -> Unit,
    onNavigateToNoteLocationMap: (Double, Double, String) -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    var messageText by remember { mutableStateOf("") }
    val uiState by viewModel.messageState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val inputFocusRequester = remember { FocusRequester() }
    val haptic = LocalHapticFeedback.current
    val clipboardManager = LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current

    // UI States
    var showContextMenu by remember { mutableStateOf(false) }
    var selectedMessage by remember { mutableStateOf<MessageUiModel?>(null) }
    var selectedMessageBounds by remember { mutableStateOf<Rect?>(null) }
    val messageBounds = remember { mutableMapOf<String, Rect>() }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var selectedNoteRef by remember { mutableStateOf<com.linker.app.domain.model.NoteReference?>(null) }
    var showMessageInfo by remember { mutableStateOf(false) }
    var showReactionsSheet by remember { mutableStateOf(false) }
    var showSeenBySheet by remember { mutableStateOf(false) }
    var reactionsMessageId by remember { mutableStateOf<String?>(null) }
    var replyToMessage by remember { mutableStateOf<MessageUiModel?>(null) }
    var highlightMessageId by remember { mutableStateOf<String?>(null) }
    var seenByUsersForSheet by remember { mutableStateOf<List<SeenByUserUi>>(emptyList()) }
    var quickReactions by rememberSaveable {
        mutableStateOf(listOf("\uD83D\uDC4D", "\u2764\uFE0F", "\uD83D\uDE02", "\uD83D\uDE2E", "\uD83D\uDE22", "\uD83D\uDE4F"))
    }

    val messageInfoState by viewModel.messageInfoState.collectAsStateWithLifecycle()
    val messageReactionsState by viewModel.messageReactionsState.collectAsStateWithLifecycle()

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
        } catch (e: Exception) {
            android.util.Log.w("ChatMessageScreen", "Failed to scroll to bottom: ${e.message}")
        }
    }

    LaunchedEffect(replyToMessage?.messageId) {
        if (replyToMessage != null) {
            try {
                inputFocusRequester.requestFocus()
            } catch (e: Exception) {
                android.util.Log.w("ChatMessageScreen", "Failed to request focus on reply: ${e.message}")
            }
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
                        } catch (e: Exception) {
                            android.util.Log.w("ChatMessageScreen", "Failed to scroll to reply message: ${e.message}")
                        }
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
        ReactionsBottomSheet(
            state = messageReactionsState,
            onDismiss = { showReactionsSheet = false },
            onNavigateToUserProfile = onNavigateToUserProfile
        )
    }

    // SeenBy Sheet
    if (showSeenBySheet) {
        SeenByBottomSheet(
            seenByUsers = seenByUsersForSheet,
            onDismiss = { showSeenBySheet = false }
        )
    }

    val noteRef = selectedNoteRef
    if (noteRef != null) {
        val fakeAuthor = com.linker.app.domain.model.NoteAuthor(
            userId = noteRef.authorId.ifBlank { "unknown" },
            username = noteRef.authorName.ifBlank { "User" },
            displayName = noteRef.authorName.ifBlank { "User" },
            profileImageUrl = null
        )
        val fakeCreatedAt = (noteRef.expiresAt - 86400000L).coerceAtLeast(1L)
        val fakeNote = when (noteRef.noteType) {
            "MUSIC" -> com.linker.app.domain.model.Note.Music(
                noteId = noteRef.noteId.ifBlank { "unknown" },
                author = fakeAuthor,
                content = noteRef.content ?: "",
                musicTrackId = "unknown",
                musicTrackName = noteRef.musicTrackName?.ifBlank { "Bilinmeyen" } ?: "Bilinmeyen",
                musicArtistName = noteRef.musicArtistName?.ifBlank { "Bilinmeyen" } ?: "Bilinmeyen",
                musicAlbumArt = noteRef.musicAlbumArt,
                previewUrl = null,
                clipStartTime = 0L,
                clipEndTime = 30000L,
                backgroundColor = noteRef.backgroundColor,
                textColor = noteRef.textColor,
                createdAt = fakeCreatedAt,
                expiresAt = noteRef.expiresAt.coerceAtLeast(fakeCreatedAt + 1L)
            )
            "COUNTDOWN" -> com.linker.app.domain.model.Note.Countdown(
                noteId = noteRef.noteId.ifBlank { "unknown" },
                author = fakeAuthor,
                content = "Süre",
                countdownTitle = noteRef.content?.ifBlank { "Süre" } ?: "Süre",
                countdownTargetTime = noteRef.expiresAt,
                backgroundColor = noteRef.backgroundColor,
                textColor = noteRef.textColor,
                createdAt = fakeCreatedAt,
                expiresAt = noteRef.expiresAt.coerceAtLeast(fakeCreatedAt + 1L)
            )
            "LOCATION" -> com.linker.app.domain.model.Note.Location(
                noteId = noteRef.noteId.ifBlank { "unknown" },
                author = fakeAuthor,
                latitude = noteRef.latitude ?: 0.0,
                longitude = noteRef.longitude ?: 0.0,
                placeName = noteRef.content?.ifBlank { "Konum" } ?: "Konum",
                mapPreviewUrl = null,
                backgroundColor = noteRef.backgroundColor,
                textColor = noteRef.textColor,
                createdAt = fakeCreatedAt,
                expiresAt = noteRef.expiresAt.coerceAtLeast(fakeCreatedAt + 1L)
            )
            "GIF" -> com.linker.app.domain.model.Note.Gif(
                noteId = noteRef.noteId.ifBlank { "unknown" },
                author = fakeAuthor,
                gifUrl = "unknown",
                aspectRatio = 1f,
                content = noteRef.content ?: "",
                backgroundColor = noteRef.backgroundColor,
                textColor = noteRef.textColor,
                createdAt = fakeCreatedAt,
                expiresAt = noteRef.expiresAt.coerceAtLeast(fakeCreatedAt + 1L)
            )
            else -> com.linker.app.domain.model.Note.Text(
                noteId = noteRef.noteId.ifBlank { "unknown" },
                author = fakeAuthor,
                content = noteRef.content?.ifBlank { "Durum" } ?: "Durum",
                backgroundColor = noteRef.backgroundColor,
                textColor = noteRef.textColor,
                createdAt = fakeCreatedAt,
                expiresAt = noteRef.expiresAt.coerceAtLeast(fakeCreatedAt + 1L)
            )
        }
        
        NoteDetailBottomSheet(
            note = fakeNote,
            onDismiss = { selectedNoteRef = null },
            onNavigateToNoteLocationMap = onNavigateToNoteLocationMap
        )
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
                            text = stringResource(R.string.chat_msg_admins_only),
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
                            val senderName = if (msg.isSelf) {
                                stringResource(R.string.chat_msg_you)
                            } else {
                                if (uiState.isGroupChat) msg.senderDisplayName else uiState.recipientName
                            }
                            ReplyPreview(
                                senderName = senderName,
                                previewText = msg.content ?: stringResource(R.string.chat_msg_media_placeholder),
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
                                    Text(
                                        stringResource(R.string.chat_msg_empty_state),
                                        color = TextSecondary,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }

                        val visibleMessages = uiState.messages

                        itemsIndexed(visibleMessages) { index, msg ->
                            val repliedIndex = msg.replyToMessageId?.let { id ->
                                visibleMessages.indexOfFirst { it.messageId == id }
                            } ?: -1

                            if (!msg.replyToMessageId.isNullOrBlank()) {
                                val preview = if (repliedIndex >= 0) {
                                    val replied = visibleMessages[repliedIndex]
                                    val repliedName = if (replied.isSelf) {
                                        stringResource(R.string.chat_msg_you)
                                    } else {
                                        if (uiState.isGroupChat) replied.senderDisplayName else uiState.recipientName
                                    }
                                    ReplyPreview(
                                        senderName = repliedName,
                                        previewText = replied.content ?: stringResource(R.string.chat_msg_media_placeholder),
                                        isSelf = replied.isSelf
                                    )
                                } else {
                                    ReplyPreview(
                                        senderName = stringResource(R.string.chat_msg_replied_title),
                                        previewText = stringResource(R.string.chat_msg_previous_msg),
                                        isSelf = false
                                    )
                                }
                                ReplyPreviewHologram(
                                    preview = preview,
                                    alignEnd = msg.isSelf,
                                    alpha = 0.7f,
                                    onClick = if (repliedIndex >= 0) {
                                        {
                                            coroutineScope.launch {
                                                listState.animateScrollToItem(repliedIndex)
                                                highlightMessageId = msg.replyToMessageId
                                                kotlinx.coroutines.delay(2000)
                                                highlightMessageId = null
                                            }
                                        }
                                    } else null
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (msg.isSelf) Arrangement.End else Arrangement.Start,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                if (!msg.isSelf && uiState.isGroupChat) {
                                    LinkerAvatar(
                                        imageUrl = msg.senderAvatarUrl,
                                        size = 36.dp,
                                        storyState = StoryState.NONE,
                                        onClick = {
                                            if (msg.senderId.isNotBlank()) {
                                                onNavigateToUserProfile(msg.senderId)
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }

                                ChatBubble(
                                    message = MessageItem(
                                        text = msg.displayContent,
                                        isSelf = msg.isSelf,
                                        status = msg.status,
                                        prevIsSelf = msg.prevIsSelf,
                                        nextIsSelf = msg.nextIsSelf,
                                        isDeleted = msg.isDeleted,
                                        replyToNote = msg.replyToNote
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
                                    },
                                    onNoteReplyClick = { _ ->
                                        selectedNoteRef = msg.replyToNote
                                    }
                                )
                            }

                            if (msg.formattedReactions.isNotEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = if (msg.isSelf) Alignment.CenterEnd else Alignment.CenterStart
                                ) {
                                    ReactionSummaryRow(
                                        emojis = msg.formattedReactions,
                                        modifier = Modifier.clickable {
                                            reactionsMessageId = msg.messageId
                                            viewModel.loadMessageReactions(msg.messageId, msg.reactions)
                                            showReactionsSheet = true
                                        }
                                    )
                                }
                            }

                            val isLastSelfMessage = msg.isSelf && visibleMessages.drop(index + 1).none { it.isSelf }
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
                                                    storyState = StoryState.NONE
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

                        if (uiState.sendError != null) {
                            item {
                                uiState.sendError?.let { error ->
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = error,
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
        }

        if (showContextMenu) {
            val message = selectedMessage
            val bounds = selectedMessageBounds
            val forwardComingSoon = stringResource(R.string.chat_msg_forward_coming_soon)
            if (message != null && bounds != null) {
                val constraints = this.constraints
                MessageContextMenu(
                    message = message,
                    messageBounds = bounds,
                    screenWidth = constraints.maxWidth.toFloat(),
                    screenHeight = constraints.maxHeight.toFloat(),
                    quickReactions = quickReactions,
                    onDismiss = {
                        showContextMenu = false
                        showEmojiPicker = false
                    },
                    onReply = {
                        replyToMessage = message
                        showContextMenu = false
                    },
                    onCopy = { 
                        message.content?.let { text ->
                            clipboardManager.setText(AnnotatedString(text))
                        }
                        showContextMenu = false 
                    },
                    onForward = {
                        Toast.makeText(context, forwardComingSoon, Toast.LENGTH_SHORT).show()
                        showContextMenu = false 
                    },
                    onInfo = {
                        viewModel.loadMessageInfo(message.messageId)
                        showMessageInfo = true
                        showContextMenu = false
                    },
                    onDelete = {
                        viewModel.deleteMessage(message.messageId, forEveryone = false)
                        showContextMenu = false
                    },
                    onDeleteForEveryone = if (message.isSelf) {
                        {
                            viewModel.deleteMessage(message.messageId, forEveryone = true)
                            showContextMenu = false
                        }
                    } else null,
                    onReaction = { emoji ->
                        viewModel.reactToMessage(message.messageId, emoji)
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
}

private fun formatSeenLabel(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val days = diff / 86_400_000L
    return when {
        diff < 60_000L -> "Just seen"
        days == 1L -> "Seen yesterday"
        days > 1L -> "Seen ${days}d ago"
        else -> "Seen ${formatRelativeTime(timestamp)}"
    }
}
