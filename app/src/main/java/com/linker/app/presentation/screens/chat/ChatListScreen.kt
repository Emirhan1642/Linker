package com.linker.app.presentation.screens.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import com.linker.app.R
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.linker.app.presentation.components.BottomNavItem
import com.linker.app.presentation.components.LinkerAvatar
import com.linker.app.presentation.components.LinkerBottomNavigationBar
import com.linker.app.presentation.components.LinkerSearchBar
import com.linker.app.presentation.theme.AccentGreen
import com.linker.app.presentation.theme.Black
import com.linker.app.presentation.theme.LightGray
import com.linker.app.presentation.theme.TextPrimary
import com.linker.app.presentation.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun ChatListScreen(
    onNavigateToChatDetail: (String) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateBottomNav: (BottomNavItem) -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.chatListState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    val filters = listOf("All", "Unreads", "Favorites", "Groups", "Archived")
    var selectedFilter by remember { mutableStateOf("All") }
    val listState = rememberLazyListState()
    val showLockedHeader by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }

    val filteredChats = when (selectedFilter) {
        "Unreads" -> uiState.chats.filter { it.unreadCount > 0 }
        else -> uiState.chats
    }

    Scaffold(
        containerColor = Black,
        bottomBar = {
            LinkerBottomNavigationBar(
                currentRoute = "Chat",
                onNavigate = onNavigateBottomNav,
                modifier = Modifier.background(Color.Transparent)
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
                    Icon(painterResource(R.drawable.ic_arrow_left_01_outline), contentDescription = "Back", tint = TextPrimary, modifier = Modifier.size(30.dp))
                }
                LinkerSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { /* New Chat */ }) {
                    Icon(painterResource(R.drawable.ic_play_add_outline), contentDescription = "Settings", tint = TextPrimary, modifier = Modifier.size(30.dp))
                }
            }

            // Notes / Stories Row
            LazyRow(
                contentPadding = PaddingValues(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.padding(vertical = 10.dp)
            ) {
                // Your Note
                item {
                    NoteItem(name = "Your Note", question = if (uiState.notes.any { it.author.userId == com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid }) 
                        uiState.notes.first { it.author.userId == com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid }.content
                    else "Share a thought...", isSelf = true)
                }
                // Other users' notes
                val otherNotes = uiState.notes.filter { it.author.userId != com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid }
                items(otherNotes.size) { index ->
                    val note = otherNotes[index]
                    NoteItem(
                        name = note.author.displayName.ifBlank { "User" },
                        question = note.content
                    )
                }
            }

            // Filters
            LazyRow(
                contentPadding = PaddingValues(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                items(filters.size) { index ->
                    val filter = filters[index]
                    val isSelected = filter == selectedFilter
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { selectedFilter = filter }
                    ) {
                        Text(
                            text = filter,
                            color = if (isSelected) TextPrimary else TextSecondary,
                            fontSize = 17.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                        )
                        if (isSelected) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .width(24.dp)
                                    .height(2.dp)
                                    .background(TextPrimary)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = LightGray)

            // Chat List
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                if (showLockedHeader) {
                    stickyHeader {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Black)
                                .padding(horizontal = 20.dp)
                                .padding(top = 15.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_smart_lock_ai_outline),
                                contentDescription = "Locked chats",
                                tint = TextPrimary,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.width(20.dp))
                            Text("Locked chats", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                if (filteredChats.isEmpty() && !uiState.isLoading) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No chats yet. Start a conversation!", color = TextSecondary, fontSize = 16.sp)
                        }
                    }
                } else {
                    items(filteredChats.size) { index ->
                        val chat = filteredChats[index]
                        ChatItem(
                            name = chat.displayName,
                            message = chat.lastMessage ?: "Start chatting...",
                            time = formatTimestamp(chat.lastMessageTime),
                            unreadCount = chat.unreadCount,
                            isTyping = chat.isTyping,
                            onClick = { onNavigateToChatDetail(chat.chatId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NoteItem(name: String, question: String, isSelf: Boolean = false) {
    val context = LocalContext.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(80.dp)
    ) {
        Box(contentAlignment = Alignment.TopCenter) {
            // Avatar
            LinkerAvatar(
                imageUrl = null,
                size = 80.dp,
                hasStory = isSelf,
                modifier = Modifier.padding(top = 24.dp),
                onClick = { android.widget.Toast.makeText(context, "$name notu", android.widget.Toast.LENGTH_SHORT).show() }
            )
            // Speech Bubble
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(15.dp))
                    .background(Color.White)
                    .padding(horizontal = 2.dp)
                    .width(75.dp)
                    .height(40.dp)
            ) {
                Text(
                    text = question,
                    color = Black,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 10.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = name,
            color = TextPrimary,
            fontSize = 9.sp,
            maxLines = 1
        )
    }
}

@Composable
fun ChatItem(
    name: String,
    message: String,
    time: String,
    unreadCount: Int,
    isTyping: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val hasUnread = unreadCount > 0
    val displayMessage = if (hasUnread) "$unreadCount new message" else message
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LinkerAvatar(
            imageUrl = null,
            size = 60.dp,
            hasStory = true,
            onClick = { android.widget.Toast.makeText(context, "$name profili", android.widget.Toast.LENGTH_SHORT).show() }
        )
        Spacer(modifier = Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = displayMessage,
                color = if (isTyping) AccentGreen else if (hasUnread) AccentGreen else TextSecondary,
                fontSize = 14.sp,
                maxLines = 1,
                fontWeight = if (hasUnread) FontWeight.Medium else FontWeight.Normal
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = time,
                color = if (hasUnread) AccentGreen else TextSecondary,
                fontSize = 12.sp,
                fontWeight = if (hasUnread) FontWeight.Medium else FontWeight.Normal
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (hasUnread) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(AccentGreen)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$unreadCount",
                        color = Black,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

fun formatTimestamp(timestamp: Long): String {
    if (timestamp == 0L) return ""
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val daysDiff = TimeUnit.MILLISECONDS.toDays(diff)
    return when {
        daysDiff == 0L -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        daysDiff == 1L -> "Yesterday"
        daysDiff < 7 -> SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(timestamp))
        else -> SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(timestamp))
    }
}
