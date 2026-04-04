package com.linker.app.presentation.screens.chat


import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.emoji2.emojipicker.EmojiPickerView
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.firebase.auth.FirebaseAuth
import com.linker.app.R
import com.linker.app.domain.model.MessageStatus
import com.linker.app.presentation.components.LinkerAvatar
import com.linker.app.presentation.theme.AccentGreen
import com.linker.app.presentation.theme.LinkerAngularGradient
import com.linker.app.presentation.theme.TextHint
import com.linker.app.presentation.theme.TextPrimary
import com.linker.app.presentation.theme.TextSecondary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
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
    var highlightMessageId by remember { mutableStateOf<String?>(null) }
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    val sessionId = remember { UUID.randomUUID().toString() }

    var showContextMenu by remember { mutableStateOf(false) }
    var selectedMessage by remember { mutableStateOf<MessageUiModel?>(null) }
    var selectedMessageBounds by remember { mutableStateOf<Rect?>(null) }
    val messageBounds = remember { mutableStateMapOf<String, Rect>() }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showMessageInfo by remember { mutableStateOf(false) }
    var showReactionsSheet by remember { mutableStateOf(false) }
    var reactionsMessageId by remember { mutableStateOf<String?>(null) }
    var replyToMessage by remember { mutableStateOf<MessageUiModel?>(null) }
    var quickReactions by rememberSaveable {
        mutableStateOf(listOf("\uD83D\uDC4D", "\u2764\uFE0F", "\uD83D\uDE02", "\uD83D\uDE2E", "\uD83D\uDE22", "\uD83D\uDE4F"))
    }

    val messageInfoState by viewModel.messageInfoState.collectAsState()
    val messageReactionsState by viewModel.messageReactionsState.collectAsState()

    LaunchedEffect(chatId) {
        viewModel.openChat(chatId)
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            val targetIndex = uiState.messages.size
            try {
                listState.animateScrollToItem(targetIndex)
            } catch (_: Exception) { }
        }
    }

    LaunchedEffect(replyToMessage?.messageId) {
        if (replyToMessage != null) {
            try {
                inputFocusRequester.requestFocus()
            } catch (_: Exception) { }
        }
    }

    if (showMessageInfo) {
        ModalBottomSheet(
            onDismissRequest = { showMessageInfo = false },
            containerColor = Color(0xFF1C1C20)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Message Info", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)

                if (messageInfoState.isLoading) {
                    CircularProgressIndicator(color = TextSecondary, modifier = Modifier.size(20.dp))
                } else {
                    Column {
                        if (!messageInfoState.replyToMessageId.isNullOrBlank()) {
                            val replied = uiState.messages.firstOrNull { it.messageId == messageInfoState.replyToMessageId }
                            if (replied != null) {
                                ReplyPreviewHologram(
                                    preview = ReplyPreview(
                                        senderName = if (replied.isSelf) "You" else if (uiState.isGroupChat) "User" else uiState.recipientName,
                                        previewText = replied.content ?: "[Media]",
                                        isSelf = replied.isSelf
                                    ),
                                    alignEnd = messageInfoState.isSelf,
                                    alpha = 0.7f
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }

                        val infoMessageItem = MessageItem(
                            text = messageInfoState.content,
                            isSelf = messageInfoState.isSelf,
                            sessionId = ""
                        )
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = if (messageInfoState.isSelf) Alignment.CenterEnd else Alignment.CenterStart
                        ) {
                            MessageBubbleContent(message = infoMessageItem)
                        }
                    }

                    if (messageInfoState.reactions.isNotEmpty()) {
                        val emojiCounts = messageInfoState.reactions.groupingBy { it.emoji }.eachCount()
                        val summary = emojiCounts.entries.sortedByDescending { it.value }.map { entry ->
                            if (entry.value > 1) "${entry.key} ${entry.value}" else entry.key
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        ReactionSummaryRow(emojis = summary)
                    }

                    if (uiState.isGroupChat && !messageInfoState.replyToMessageId.isNullOrBlank()) {
                        InfoRow("From → To", messageInfoState.fromTo)
                    }
                    InfoRow("Sent", messageInfoState.sentAt?.let { formatRelativeTimeEn(it) } ?: "-", R.drawable.ic_forward_outline)
                    if (messageInfoState.deliveredAt != null) {
                        InfoRow("Delivered", formatRelativeTimeEn(messageInfoState.deliveredAt!!), R.drawable.ic_box_search_outline)
                    }
                    if (messageInfoState.readAt != null) {
                        InfoRow("Seen", formatRelativeTimeEn(messageInfoState.readAt!!), R.drawable.ic_forward_bold)
                    }
                    if (messageInfoState.failedAt != null) {
                        InfoRow("Failed", formatRelativeTimeEn(messageInfoState.failedAt!!), R.drawable.ic_cloud_cross_outline)
                    }

                    if (uiState.isGroupChat && messageInfoState.readReceipts.isNotEmpty()) {
                        Text("Seen By", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        messageInfoState.readReceipts.forEach { rr ->
                            InfoRow(rr.userName, formatRelativeTimeEn(rr.readAt))
                        }
                    }

                    if (messageInfoState.replies.isNotEmpty()) {
                        Text("Replies", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        messageInfoState.replies.forEach { reply ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val idx = uiState.messages.indexOfFirst { it.messageId == reply.messageId }
                                        if (idx >= 0) {
                                            coroutineScope.launch {
                                                try {
                                                    listState.animateScrollToItem(idx)
                                                } catch (_: Exception) { }
                                            }
                                            highlightMessageId = reply.messageId
                                            coroutineScope.launch {
                                                delay(1200)
                                                if (highlightMessageId == reply.messageId) {
                                                    highlightMessageId = null
                                                }
                                            }
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                LinkerAvatar(
                                    imageUrl = reply.avatarUrl,
                                    size = 40.dp,
                                    hasStory = false,
                                    onClick = {
                                        if (reply.senderId.isNotBlank()) {
                                            onNavigateToUserProfile(reply.senderId)
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(reply.preview, color = TextSecondary, fontSize = 13.sp, maxLines = 2)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

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
                    val sortedReactions = messageReactionsState.reactions.sortedWith(
                        compareByDescending<ReactionUserInfo> { it.userId == currentUserId }
                            .thenBy { it.userName.lowercase() }
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(sortedReactions.size) { idx ->
                            val reaction = sortedReactions[idx]
                            ReactionAvatarItem(
                                userName = reaction.userName,
                                avatarUrl = reaction.avatarUrl,
                                emoji = reaction.emoji,
                                onClick = {
                                    if (reaction.userId == currentUserId) {
                                        viewModel.reactToMessage(messageReactionsState.messageId, null)
                                        showReactionsSheet = false
                                    } else if (reaction.userId.isNotBlank()) {
                                        onNavigateToUserProfile(reaction.userId)
                                    }
                                }
                            )
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
                ChatInputBar(
                    text = messageText,
                    onTextChange = { messageText = it },
                    replyPreview = replyToMessage?.let { msg ->
                        val senderName = if (msg.isSelf) {
                            "You"
                        } else {
                            if (uiState.isGroupChat) "User" else uiState.recipientName
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
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(painterResource(id = R.drawable.ic_arrow_left_01_outline), contentDescription = "Back", tint = TextPrimary, modifier = Modifier.size(30.dp))
                }

                Row(
                    modifier = Modifier
                        .weight(0.9f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF262626))
                        .clickable { onNavigateToInfo() }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LinkerAvatar(
                        imageUrl = uiState.recipientImageUrl,
                        size = 36.dp,
                        hasStory = false,
                        onClick = {}
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = uiState.recipientName,
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

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
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            LinkerAvatar(
                                imageUrl = uiState.recipientImageUrl,
                                size = 120.dp,
                                hasStory = false
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(uiState.recipientName, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            if (uiState.recipientUsername.isNotBlank()) {
                                Text("@${uiState.recipientUsername}", color = TextSecondary, fontSize = 14.sp)
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
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
                        val repliedIndex = msg.replyToMessageId?.let { id ->
                            uiState.messages.indexOfFirst { it.messageId == id }
                        } ?: -1

                        if (repliedIndex >= 0) {
                            val replied = uiState.messages[repliedIndex]
                            val repliedName = if (replied.isSelf) {
                                "You"
                            } else {
                                if (uiState.isGroupChat) "User" else uiState.recipientName
                            }
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = if (msg.isSelf) Alignment.CenterEnd else Alignment.CenterStart
                            ) {
                                Box(
                                    modifier = Modifier.clickable {
                                        coroutineScope.launch {
                                            try {
                                                listState.animateScrollToItem(repliedIndex)
                                            } catch (_: Exception) { }
                                        }
                                        highlightMessageId = replied.messageId
                                        coroutineScope.launch {
                                            delay(1200)
                                            if (highlightMessageId == replied.messageId) {
                                                highlightMessageId = null
                                            }
                                        }
                                    }
                                ) {
                                    ReplyPreviewHologram(
                                        preview = ReplyPreview(
                                            senderName = repliedName,
                                            previewText = replied.content ?: "[Media]",
                                            isSelf = replied.isSelf
                                        ),
                                        alignEnd = msg.isSelf,
                                        alpha = 0.7f
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        ChatBubble(
                            message = MessageItem(
                                text = msg.content ?: "",
                                isSelf = msg.isSelf,
                                status = msg.status,
                                sessionId = sessionId,
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
                            }
                            ,
                            isHighlighted = highlightMessageId == msg.messageId,
                            onHaptic = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        )

                        val reactionSummary = buildReactionSummary(msg.reactions)
                        if (reactionSummary.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(),
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

                        val isLastSelfMessage = msg.isSelf && uiState.messages.drop(index + 1).none { it.isSelf }
                        if (isLastSelfMessage && msg.readAt != null) {
                            Text(
                                text = "Seen ${formatRelativeTimeEn(msg.readAt)}",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 12.dp, top = 2.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.End
                            )
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

        if (showContextMenu && selectedMessage != null && selectedMessageBounds != null) {
            val density = LocalDensity.current
            val bounds = selectedMessageBounds!!
            val message = selectedMessage!!
            val actions = if (message.isSelf) {
                listOf("Reply", "Copy", "Forward", "Message Info", "Delete")
            } else {
                listOf("Reply", "Copy", "Forward", "Message Info")
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val screenWidth = constraints.maxWidth.toFloat()
                val screenHeight = constraints.maxHeight.toFloat()
                val paddingPx = with(density) { 8.dp.toPx() }
                val menuWidthPx = with(density) { 180.dp.toPx() }
                val menuHeightPx = with(density) { (actions.size * 48).dp.toPx() + 16.dp.toPx() }
                val emojiBarWidthPx = with(density) { 280.dp.toPx() }
                val emojiBarHeightPx = with(density) { 52.dp.toPx() }

                val menuYSide = bounds.top + bounds.height / 2f - menuHeightPx / 2f
                val sideXUnclamped = if (message.isSelf) bounds.left - menuWidthPx - paddingPx else bounds.right + paddingPx
                val sideX = sideXUnclamped.coerceIn(paddingPx, screenWidth - menuWidthPx - paddingPx)

                val belowY = bounds.bottom + paddingPx
                val aboveY = bounds.top - menuHeightPx - paddingPx
                val canBelow = belowY + menuHeightPx <= screenHeight - paddingPx
                val canAbove = aboveY >= paddingPx

                val menuY = when {
                    canBelow -> belowY
                    canAbove -> aboveY
                    else -> menuYSide.coerceIn(paddingPx, screenHeight - menuHeightPx - paddingPx)
                }
                val menuX = when {
                    canBelow || canAbove -> (bounds.left + (bounds.width - menuWidthPx) / 2f)
                        .coerceIn(paddingPx, screenWidth - menuWidthPx - paddingPx)
                    else -> sideX
                }

                val emojiYAbove = bounds.top - emojiBarHeightPx - paddingPx
                val emojiYBelow = bounds.bottom + paddingPx
                var emojiY = if (menuY == belowY) emojiYAbove else if (menuY == aboveY) emojiYBelow else {
                    if (emojiYAbove < paddingPx) emojiYBelow else emojiYAbove
                }
                val emojiXBase = if (message.isSelf) bounds.right - emojiBarWidthPx else bounds.left
                val emojiX = emojiXBase.coerceIn(paddingPx, screenWidth - emojiBarWidthPx - paddingPx)

                val menuRect = Rect(menuX, menuY, menuX + menuWidthPx, menuY + menuHeightPx)
                val emojiRect = Rect(emojiX, emojiY, emojiX + emojiBarWidthPx, emojiY + emojiBarHeightPx)
                val overlaps = !(emojiRect.right < menuRect.left || emojiRect.left > menuRect.right || emojiRect.bottom < menuRect.top || emojiRect.top > menuRect.bottom)
                if (overlaps) {
                    emojiY = if (emojiY == emojiYAbove) emojiYBelow else emojiYAbove
                }
                emojiY = emojiY.coerceIn(paddingPx, screenHeight - emojiBarHeightPx - paddingPx)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x77000000))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            showContextMenu = false
                            showEmojiPicker = false
                        }
                )

                Box(
                    modifier = Modifier.offset {
                        IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt())
                    }
                ) {
                    MessageBubbleContent(
                        message = MessageItem(
                            text = message.content ?: "",
                            isSelf = message.isSelf,
                            status = message.status,
                            sessionId = sessionId,
                            prevIsSelf = false,
                            nextIsSelf = false
                        )
                    )
                }

                Row(
                    modifier = Modifier
                        .offset { IntOffset(emojiX.roundToInt(), emojiY.roundToInt()) }
                        .width(with(density) { emojiBarWidthPx.toDp() })
                        .height(with(density) { emojiBarHeightPx.toDp() })
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color(0xFF1F1F23))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    quickReactions.forEach { emoji ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.reactToMessage(message.messageId, emoji)
                                    showContextMenu = false
                                    showEmojiPicker = false
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            EmojiText(text = emoji, fontSize = 28.sp)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(Color(0xFF2E2E32))
                            .clickable {
                                showEmojiPicker = !showEmojiPicker
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+", color = TextSecondary, fontSize = 20.sp)
                    }
                }

                Column(
                    modifier = Modifier
                        .offset { IntOffset(menuX.roundToInt(), menuY.roundToInt()) }
                        .width(with(density) { menuWidthPx.toDp() })
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1F1F23))
                        .padding(vertical = 8.dp)
                ) {
                    actions.forEach { action ->
                        val isDelete = action == "Delete"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    when (action) {
                                        "Reply" -> {
                                            replyToMessage = message
                                            showContextMenu = false
                                        }
                                        "Copy" -> {
                                            showContextMenu = false
                                        }
                                        "Forward" -> {
                                            showContextMenu = false
                                        }
                                        "Message Info" -> {
                                            viewModel.loadMessageInfo(message.messageId)
                                            showMessageInfo = true
                                            showContextMenu = false
                                        }
                                        "Delete" -> {
                                            viewModel.deleteMessage(message.messageId)
                                            showContextMenu = false
                                        }
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = action,
                                color = if (isDelete) Color(0xFFFF4B4B) else TextPrimary,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                if (showEmojiPicker) {
                    val pickerHeight = 360.dp
                    val pickerY = (emojiY + emojiBarHeightPx + paddingPx).coerceAtMost(screenHeight - with(density) { pickerHeight.toPx() } - paddingPx)
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(emojiX.roundToInt(), pickerY.roundToInt()) }
                            .width(with(density) { emojiBarWidthPx.toDp() })
                            .height(pickerHeight)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF1C1C20))
                    ) {
                        AndroidView(
                            factory = { context ->
                                EmojiPickerView(context).apply {
                                    emojiGridColumns = 7
                                    setOnEmojiPickedListener { emojiItem ->
                                        viewModel.reactToMessage(message.messageId, emojiItem.emoji)
                                        showContextMenu = false
                                        showEmojiPicker = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
data class MessageItem(
    val text: String,
    val isSelf: Boolean,
    val status: MessageStatus = MessageStatus.SENT,
    val sessionId: String = "",
    val prevIsSelf: Boolean = false,
    val nextIsSelf: Boolean = false
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubbleContent(
    message: MessageItem,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false
) {
    val density = LocalDensity.current
    val radius = with(density) { (20.sp.toDp() + 24.dp) / 2f }
    val shape = RoundedCornerShape(radius)

    val backgroundColor = if (message.isSelf) Color(0xFF007E8E) else Color(0xFF2E2E32)

    Box(
        modifier = modifier
            .widthIn(max = 280.dp)
            .clip(shape)
    ) {
        if (highlighted) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(AccentGreen.copy(alpha = 0.25f))
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(backgroundColor)
        )
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = message.text,
                color = Color.White,
                fontSize = 15.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun ChatBubble(
    message: MessageItem,
    coroutineScope: CoroutineScope,
    onBubblePositioned: (Rect) -> Unit = {},
    onLongPress: () -> Unit = {},
    onSwipeReply: () -> Unit = {},
    isHighlighted: Boolean = false,
    onHaptic: () -> Unit = {}
) {
    val align = if (message.isSelf) Alignment.CenterEnd else Alignment.CenterStart
    val offsetX = remember { Animatable(0f) }
    val maxDrag = 96f
    val trigger = 64f
    val progress = (abs(offsetX.value) / maxDrag).coerceIn(0f, 1f)
    var didHaptic by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = align
    ) {
        if (progress > 0.05f) {
            val iconAlign = if (message.isSelf) Alignment.CenterStart else Alignment.CenterEnd
            Box(
                modifier = Modifier
                    .align(iconAlign)
                    .graphicsLayer(
                        alpha = progress,
                        scaleX = 0.8f + 0.2f * progress,
                        scaleY = 0.8f + 0.2f * progress
                    )
                    .size(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1F1F23)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_forward_outline),
                    contentDescription = "Reply",
                    tint = AccentGreen,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        MessageBubbleContent(
            message = message,
            highlighted = isHighlighted,
            modifier = Modifier
                .onGloballyPositioned { coordinates ->
                    onBubblePositioned(coordinates.boundsInRoot())
                }
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(message.isSelf) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val shouldTrigger = abs(offsetX.value) >= trigger
                            if (shouldTrigger) {
                                onSwipeReply()
                            }
                            didHaptic = false
                            coroutineScope.launch {
                                offsetX.animateTo(0f, tween(150))
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            val next = if (message.isSelf) {
                                (offsetX.value + dragAmount).coerceIn(-maxDrag, 0f)
                            } else {
                                (offsetX.value + dragAmount).coerceIn(0f, maxDrag)
                            }
                            coroutineScope.launch { offsetX.snapTo(next) }
                            if (!didHaptic && abs(next) >= trigger) {
                                didHaptic = true
                                onHaptic()
                            }
                        }
                    )
                }
                .combinedClickable(
                    onClick = {},
                    onLongClick = onLongPress
                )
        )
    }
}


data class ReplyPreview(
    val senderName: String,
    val previewText: String,
    val isSelf: Boolean
)

@Composable
private fun ReplyPreviewBubbleContent(
    preview: ReplyPreview,
    alpha: Float = 0.75f,
    modifier: Modifier = Modifier
) {
    val background = if (preview.isSelf) Color(0xFF007E8E) else Color(0xFF2E2E32)
    val textColor = Color.White
    val density = LocalDensity.current
    val radius = with(density) { (12.sp.toDp() + 12.dp) / 2f }
    Box(
        modifier = modifier
            .widthIn(max = 260.dp)
            .clip(RoundedCornerShape(radius))
            .background(background.copy(alpha = alpha))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(preview.previewText, color = textColor, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun ReplyPreviewHologram(
    preview: ReplyPreview,
    alignEnd: Boolean,
    alpha: Float = 0.75f,
    modifier: Modifier = Modifier
) {
    val label = if (preview.senderName == "You") "Replying To You" else "Replying To ${preview.senderName}"
    val alignment = if (alignEnd) Alignment.TopEnd else Alignment.TopStart
    val textAlign = if (alignEnd) Alignment.End else Alignment.Start
    Box(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.align(alignment)) {
            Text(label, color = TextSecondary, fontSize = 10.sp, modifier = Modifier.align(textAlign))
            Spacer(modifier = Modifier.height(4.dp))
            ReplyPreviewBubbleContent(preview = preview, alpha = alpha, modifier = Modifier.align(textAlign))
        }
    }
}

@Composable
private fun ReplyPreviewBar(preview: ReplyPreview, onCancel: () -> Unit) {
    val background = Color(0xFF000000)//if (preview.isSelf) Color(0xFF007E8E) else Color(0xFF2E2E32)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            //.background(background.copy(alpha = 0.75f))
            //.padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                val label = if (preview.senderName == "You") "Replying To You" else "Replying To ${preview.senderName}"
                Text(label, color = TextSecondary, fontSize = 10.sp)
                Text(preview.senderName, color = AccentGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(preview.previewText, color = Color.White, fontSize = 12.sp, maxLines = 1)
            }
            Icon(
                painter = painterResource(id = R.drawable.ic_close_circle_bold),
                contentDescription = "Cancel Reply",
                tint = TextSecondary,
                modifier = Modifier
                    .size(18.dp)
                    .clickable { onCancel() }
            )
        }
    }
}

@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    replyPreview: ReplyPreview? = null,
    onCancelReply: () -> Unit = {},
    onSend: () -> Unit = {},
    isSending: Boolean = false,
    focusRequester: FocusRequester? = null
) {
    val containerColor = Color(0xFF202020)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (replyPreview != null) containerColor else Color(0x00FFFFFF))
            .imePadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (replyPreview != null) {
                ReplyPreviewBar(preview = replyPreview, onCancel = onCancelReply)
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(containerColor)
                    .border(2.dp, LinkerAngularGradient, RoundedCornerShape(28.dp))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_arrow_down_02_bold),
                        contentDescription = "Attach",
                        tint = Color(0xFFFFFFFF),
                        modifier = Modifier.size(24.dp).rotate(180f)
                    )
                }

                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    textStyle = Typography().bodyLarge.copy(color = TextPrimary),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
                    enabled = !isSending,
                    decorationBox = { innerTextField ->
                        if (text.isEmpty()) {
                            Text("Send a message...", color = TextHint, fontSize = 16.sp)
                        }
                        innerTextField()
                    }
                )

                IconButton(
                    onClick = onSend,
                    enabled = !isSending && text.isNotBlank()
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color(0xFFC73EE7),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_ai_send_message_outline),
                            contentDescription = "Send",
                            tint = Color(0xFFFFFFFF),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }

}

private fun formatMessageTimestamp(timestamp: Long): String {
    if (timestamp == 0L) return ""
    return SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(timestamp))
}

@Composable
private fun InfoRow(label: String, value: String, iconRes: Int? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (iconRes != null) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = label,
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(label, color = TextSecondary, fontSize = 13.sp)
        }
        Text(value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

private fun formatRelativeTimeEn(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val minutes = diff / 60000
    val hours = diff / 3600000
    val days = diff / 86400000
    return when {
        diff < 60000 -> "Just now"
        minutes < 60 -> "$minutes minutes ago"
        hours < 24 -> "$hours hours ago"
        days == 1L -> "Seen yesterday"
        else -> SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}

@Composable
private fun InfoBubble(text: String, isSelf: Boolean) {
    val background = if (isSelf) Color(0xFF007E8E) else Color(0xFF2E2E32)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = if (isSelf) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(background)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(text = text, color = Color.White, fontSize = 14.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun ReactionSummaryRow(emojis: List<String>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Color(0xFF1F1F23), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        emojis.forEach { emoji ->
            EmojiText(text = emoji, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ReactionAvatarItem(
    userName: String,
    avatarUrl: String?,
    emoji: String,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Box(contentAlignment = Alignment.BottomEnd) {
            LinkerAvatar(
                imageUrl = avatarUrl,
                size = 64.dp,
                hasStory = false,
                onClick = onClick
            )
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(Color(0xFF1F1F23), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                EmojiText(text = emoji, fontSize = 11.sp)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(userName, color = TextSecondary, fontSize = 12.sp, maxLines = 1)
    }
}

private fun buildReactionSummary(reactions: Map<String, String>): List<String> {
    if (reactions.isEmpty()) return emptyList()
    val ordered = reactions.values.groupingBy { it }.eachCount().entries
        .sortedByDescending { it.value }
    val top = ordered.take(3).map { entry ->
        if (entry.value > 1) "${entry.key} ${entry.value}" else entry.key
    }.toMutableList()
    if (ordered.size > 3) {
        top.add("+")
    }
    return top
}

private val EmojiFontFamily = FontFamily(Font(R.font.noto_color_emoji_regular))

@Composable
private fun EmojiText(text: String, fontSize: androidx.compose.ui.unit.TextUnit) {
    Text(text = text, fontSize = fontSize, fontFamily = EmojiFontFamily)
}
