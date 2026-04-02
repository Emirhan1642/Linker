package com.linker.app.presentation.screens.chat


import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.linker.app.R
import com.linker.app.domain.model.MessageStatus
import com.linker.app.presentation.components.LinkerAvatar
import com.linker.app.presentation.theme.Black
import com.linker.app.presentation.theme.LinkerAngularGradient
import com.linker.app.presentation.theme.TextHint
import com.linker.app.presentation.theme.TextPrimary
import com.linker.app.presentation.theme.TextSecondary
import com.linker.app.presentation.theme.AccentGreen
import java.util.UUID
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatMessageScreen(
    chatId: String,
    onNavigateBack: () -> Unit,
    onNavigateToInfo: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    var messageText by remember { mutableStateOf("") }
    val uiState by viewModel.messageState.collectAsState()
    val listState = rememberLazyListState()

    val sessionId = remember { UUID.randomUUID().toString() }

    var showBottomSheet by remember { mutableStateOf(false) }
    var selectedMessage by remember { mutableStateOf<MessageUiModel?>(null) }
    var showMessageInfo by remember { mutableStateOf(false) }
    var infoMessage by remember { mutableStateOf<MessageUiModel?>(null) }

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

    if (showBottomSheet && selectedMessage != null) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            containerColor = Color(0xFF1C1C20),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(TextSecondary)
                )
            }
        ) {
            MessageActionSheet(
                message = selectedMessage!!,
                onCopy = { showBottomSheet = false },
                onForward = { showBottomSheet = false },
                onDelete = {
                    viewModel.deleteMessage(selectedMessage!!.messageId)
                    showBottomSheet = false
                },
                onReact = { emoji ->
                    viewModel.reactToMessage(selectedMessage!!.messageId, emoji)
                    showBottomSheet = false
                },
                onMessageInfo = {
                    infoMessage = selectedMessage
                    showMessageInfo = true
                    showBottomSheet = false
                }
            )
        }
    }

    if (showMessageInfo && infoMessage != null) {
        val messageInfo = infoMessage!!
        AlertDialog(
            onDismissRequest = { showMessageInfo = false },
            confirmButton = {
                TextButton(onClick = { showMessageInfo = false }) {
                    Text("OK", color = AccentGreen)
                }
            },
            title = {
                Text("Message Info", color = TextPrimary, fontWeight = FontWeight.SemiBold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Status: ${messageInfo.status.name}",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Time: ${formatMessageTimestamp(messageInfo.timestamp)}",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                    if (!messageInfo.content.isNullOrBlank()) {
                        Text(
                            text = "Message: ${messageInfo.content}",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
            },
            containerColor = Color(0xFF1C1C20)
        )
    }

    Scaffold(
        containerColor = Black,
        bottomBar = {
            ChatInputBar(
                text = messageText,
                onTextChange = { messageText = it },
                onSend = {
                    if (messageText.isNotBlank() && !uiState.isSending) {
                        viewModel.sendMessage(messageText)
                        messageText = ""
                    }
                },
                isSending = uiState.isSending
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
                        .weight(1f)
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

                        ChatBubble(
                            message = MessageItem(
                                text = msg.content ?: "",
                                isSelf = msg.isSelf,
                                status = msg.status,
                                sessionId = sessionId,
                                prevIsSelf = prevIsSelf,
                                nextIsSelf = nextIsSelf
                            ),
                            onLongPress = {
                                selectedMessage = msg
                                showBottomSheet = true
                            }
                        )
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
}

@Composable
fun MessageActionSheet(
    message: MessageUiModel,
    onCopy: () -> Unit,
    onForward: () -> Unit,
    onDelete: () -> Unit,
    onReact: (String) -> Unit,
    onMessageInfo: () -> Unit
) {
    Column(modifier = Modifier.padding(bottom = 32.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf("\uD83D\uDC4D", "\u2764\uFE0F", "\uD83D\uDE02", "\uD83D\uDE2E", "\uD83D\uDE22", "\uD83D\uDE4F").forEach { emoji ->
                Text(
                    text = emoji,
                    fontSize = 28.sp,
                    modifier = Modifier.clickable { onReact(emoji) }
                )
            }
        }

        HorizontalDivider(color = Color(0xFF353434))

        SheetOption(icon = R.drawable.ic_ai_homepage_outline, title = "Copy", onClick = onCopy)
        SheetOption(icon = R.drawable.ic_forward_outline, title = "Forward", onClick = onForward)
        SheetOption(icon = R.drawable.ic_search_status_1_outline, title = "Message Info", onClick = onMessageInfo)

        if (message.isSelf) {
            SheetOption(icon = R.drawable.ic_close_circle_bold, title = "Delete", onClick = onDelete, color = Color(0xFFFF4B4B))
        }
    }
}

@Composable
fun SheetOption(icon: Int, title: String, onClick: () -> Unit, color: Color = TextPrimary) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = title,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = title, color = color, fontSize = 16.sp, fontWeight = FontWeight.Medium)
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
fun ChatBubble(message: MessageItem, onLongPress: () -> Unit = {}) {
    val align = if (message.isSelf) Alignment.CenterEnd else Alignment.CenterStart

    val shape = if (message.isSelf) {
        val topRight = if (message.prevIsSelf && message.sessionId.isNotBlank()) 4.dp else 16.dp
        val bottomRight = if (message.nextIsSelf && message.sessionId.isNotBlank()) 4.dp else 16.dp
        RoundedCornerShape(
            topStart = 16.dp,
            topEnd = topRight,
            bottomStart = 16.dp,
            bottomEnd = bottomRight
        )
    } else {
        RoundedCornerShape(16.dp)
    }

    val backgroundColor = if (message.isSelf) Color(0xFF007E8E) else Color(0xFF2E2E32)

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = align
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(shape)
                .background(backgroundColor)
                .combinedClickable(
                    onClick = {},
                    onLongClick = onLongPress
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
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
fun ChatInputBar(text: String, onTextChange: (String) -> Unit, onSend: () -> Unit = {}, isSending: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .imePadding()
            .navigationBarsPadding(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF262626))
                .border(2.dp, LinkerAngularGradient, RoundedCornerShape(28.dp))
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { }) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_ai_send_message_bold),
                    contentDescription = "Attach",
                    tint = Color(0xFFE94057),
                    modifier = Modifier.size(24.dp)
                )
            }

            androidx.compose.foundation.text.BasicTextField(
                value = text,
                onValueChange = onTextChange,
                textStyle = androidx.compose.material3.Typography().bodyLarge.copy(color = TextPrimary),
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
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
                        tint = Color(0xFFC73EE7),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

private fun formatMessageTimestamp(timestamp: Long): String {
    if (timestamp == 0L) return ""
    return SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(timestamp))
}
