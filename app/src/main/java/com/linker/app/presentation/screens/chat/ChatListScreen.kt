package com.linker.app.presentation.screens.chat

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
import androidx.compose.ui.res.stringResource
import com.linker.app.R
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.linker.app.presentation.components.BottomNavItem
import com.linker.app.presentation.components.LinkerAvatar
import com.linker.app.presentation.components.StoryState
import com.linker.app.presentation.components.LinkerBottomNavigationBar
import com.linker.app.presentation.components.LinkerSearchBar
import com.linker.app.presentation.theme.AccentGreen
import com.linker.app.presentation.theme.Black
import com.linker.app.presentation.theme.LightGray
import com.linker.app.presentation.theme.TextPrimary
import com.linker.app.presentation.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onNavigateToChatDetail: (String) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateBottomNav: (BottomNavItem) -> Unit,
    onNavigateToNewChat: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.chatListState.collectAsState()
    
    val filters = listOf(
        stringResource(R.string.chat_list_filter_all),
        stringResource(R.string.chat_list_filter_unreads),
        stringResource(R.string.chat_list_filter_favorites),
        stringResource(R.string.chat_list_filter_groups),
        stringResource(R.string.chat_list_filter_archived)
    )
    val filterKeys = listOf("All", "Unreads", "Favorites", "Groups", "Archived")
    
    val listState = rememberLazyListState()
    
    // Check if scrolled past the first item to show "Locked chats" header
    // Only derive state from list state changes
    val showLockedHeader by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
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
                    Icon(
                        painterResource(R.drawable.ic_arrow_left_01_outline),
                        contentDescription = stringResource(R.string.action_back),
                        tint = TextPrimary,
                        modifier = Modifier.size(30.dp)
                    )
                }
                LinkerSearchBar(
                    query = uiState.searchQuery,
                    onQueryChange = viewModel::updateSearchQuery,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onNavigateToNewChat) {
                    Icon(
                        painterResource(R.drawable.ic_play_add_outline),
                        contentDescription = "New Chat",
                        tint = TextPrimary,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            // Notes / Stories Row
            LazyRow(
                contentPadding = PaddingValues(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.padding(vertical = 10.dp)
            ) {
                item {
                    val noteContent = when (val userNote = uiState.userNote) {
                        is com.linker.app.domain.model.Note.Text -> userNote.content
                        is com.linker.app.domain.model.Note.Music -> userNote.content
                        is com.linker.app.domain.model.Note.Countdown -> userNote.content
                        is com.linker.app.domain.model.Note.Location -> userNote.placeName
                        null -> stringResource(R.string.chat_list_share_note)
                    }
                    NoteItem(
                        name = stringResource(R.string.chat_list_your_note),
                        question = noteContent,
                        isSelf = true
                    )
                }
                items(uiState.otherNotes.size) { index ->
                    val note = uiState.otherNotes[index]
                    val noteContent = when (note) {
                        is com.linker.app.domain.model.Note.Text -> note.content
                        is com.linker.app.domain.model.Note.Music -> note.content
                        is com.linker.app.domain.model.Note.Countdown -> note.content
                        is com.linker.app.domain.model.Note.Location -> note.placeName
                    }
                    NoteItem(
                        name = note.author.displayName.ifBlank { stringResource(R.string.chat_list_default_user) },
                        question = noteContent
                    )
                }
            }

            // Filters
            LazyRow(
                contentPadding = PaddingValues(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                items(filters.size) { index ->
                    val filterDisplay = filters[index]
                    val filterKey = filterKeys[index]
                    val isSelected = filterKey == uiState.selectedFilter
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { viewModel.updateSelectedFilter(filterKey) }
                    ) {
                        Text(
                            text = filterDisplay,
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
                    item {
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
                                contentDescription = stringResource(R.string.chat_list_locked_chats),
                                tint = TextPrimary,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.width(20.dp))
                            Text(
                                text = stringResource(R.string.chat_list_locked_chats),
                                color = TextPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                if (uiState.chats.isEmpty() && !uiState.isLoading) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.chat_list_empty_state),
                                color = TextSecondary,
                                fontSize = 16.sp
                            )
                        }
                    }
                } else {
                    items(uiState.chats.size) { index ->
                        val chat = uiState.chats[index]
                        ChatItem(
                            name = chat.displayName,
                            message = chat.lastMessage?.ifBlank { null } ?: stringResource(R.string.chat_list_tap_to_chat),
                            time = chat.formattedTime,
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
            LinkerAvatar(
                imageUrl = null,
                size = 80.dp,
                storyState = if (isSelf) StoryState.UNSEEN else StoryState.NONE,
                modifier = Modifier.padding(top = 24.dp),
                onClick = { android.widget.Toast.makeText(context, "$name note", android.widget.Toast.LENGTH_SHORT).show() }
            )
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
            storyState = StoryState.UNSEEN,
            onClick = { android.widget.Toast.makeText(context, "$name profile", android.widget.Toast.LENGTH_SHORT).show() }
        )
        Spacer(modifier = Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = message,
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
            if (hasUnread) {
                Spacer(modifier = Modifier.height(4.dp))
                val unreadText = if (unreadCount == 1) {
                    stringResource(R.string.chat_list_new_messages_single)
                } else {
                    stringResource(R.string.chat_list_new_messages_plural, unreadCount)
                }
                Text(
                    text = unreadText,
                    color = AccentGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
