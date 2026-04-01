package com.linker.app.presentation.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatMessageScreen(
    chatId: String,
    onNavigateBack: () -> Unit,
    onNavigateToInfo: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    var messageText by remember { mutableStateOf("") }
    val context = LocalContext.current
    val uiState by viewModel.messageState.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(chatId) {
        viewModel.openChat(chatId)
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            val targetIndex = uiState.messages.size
            try {
                listState.animateScrollToItem(targetIndex)
            } catch (_: Exception) { /* index might be out of range during update */ }
        }
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
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(painterResource(id = R.drawable.ic_arrow_left_01_outline), contentDescription = "Back", tint = TextPrimary, modifier = Modifier.size(30.dp))
                }
                
                // Active Chat Info Pill
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(32.dp))
                        .clickable { onNavigateToInfo() }
                        .background(Color(0xFF262626), RoundedCornerShape(32.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .padding(end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LinkerAvatar(
                        imageUrl = uiState.recipientImageUrl,
                        size = 36.dp,
                        hasStory = false,
                        onClick = { android.widget.Toast.makeText(context, "Profil detayı yakında eklenecek", android.widget.Toast.LENGTH_SHORT).show() }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(uiState.recipientName, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("Online", color = AccentGreen, fontSize = 12.sp)
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Intro item
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
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                if (uiState.messages.isEmpty() && !uiState.isLoading) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Send a message to start the conversation!", color = TextSecondary, fontSize = 14.sp)
                        }
                    }
                }
                
                items(uiState.messages) { msg ->
                    ChatBubble(
                        message = MessageItem(
                            text = msg.content ?: "",
                            isSelf = msg.isSelf,
                            status = msg.status
                        ),
                        timestamp = msg.timestamp
                    )
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

                // Send error
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

data class MessageItem(val text: String, val isSelf: Boolean, val status: MessageStatus = MessageStatus.SENT)

@Composable
fun ChatBubble(message: MessageItem, timestamp: Long = 0L) {
    val backgroundColor = if (message.isSelf) Color(0xFF007E8E) else Color(0xFF2E2E32)
    val align = if (message.isSelf) Alignment.CenterEnd else Alignment.CenterStart
    val shape = RoundedCornerShape(16.dp)
    val timeText = if (timestamp > 0) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    } else ""

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = align
    ) {
        Column(horizontalAlignment = if (message.isSelf) Alignment.End else Alignment.Start) {
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(shape)
                    .background(backgroundColor)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = message.text,
                    color = Color.White,
                    fontSize = 15.sp,
                    lineHeight = 20.sp
                )
            }
            Row(
                modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (timeText.isNotEmpty()) {
                    Text(
                        text = timeText,
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                }
                if (message.isSelf) {
                    Spacer(modifier = Modifier.width(4.dp))
                    val statusIcon = when (message.status) {
                        MessageStatus.SENDING -> R.drawable.ic_ai_sand_timer_outline
                        MessageStatus.SENT -> R.drawable.ic_forward_outline
                        MessageStatus.DELIVERED -> R.drawable.ic_forward_bold
                        MessageStatus.READ -> R.drawable.ic_archive_book_outline
                        MessageStatus.FAILED -> R.drawable.ic_cloud_cross_outline
                    }
                    val statusColor = when (message.status) {
                        MessageStatus.READ -> AccentGreen
                        MessageStatus.FAILED -> Color(0xFFFF4B4B)
                        else -> TextSecondary
                    }
                    Icon(
                        painter = painterResource(id = statusIcon),
                        contentDescription = message.status.name,
                        tint = statusColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ChatInputBar(text: String, onTextChange: (String) -> Unit, onSend: () -> Unit = {}, isSending: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
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
            IconButton(onClick = { /* Media */ }) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_ai_send_message_bold),
                    contentDescription = "Attach",
                    tint = Color(0xFFE94057),
                    modifier = Modifier.size(24.dp)
                )
            }
            
            // Input text
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
