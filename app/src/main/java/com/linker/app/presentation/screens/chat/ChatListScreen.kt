package com.linker.app.presentation.screens.chat

import android.R.style.Theme
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.linker.app.core.util.findActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.linker.app.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.aspectRatio
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
import com.linker.app.presentation.theme.DarkGray
import com.linker.app.presentation.theme.DarkerGray
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
    onNavigateToNoteEditor: () -> Unit,
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

    val selectedNoteForDetailState = remember { androidx.compose.runtime.mutableStateOf<com.linker.app.domain.model.Note?>(null) }
    val selectedNoteForDetail = selectedNoteForDetailState.value

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
                        is com.linker.app.domain.model.Note.Music -> if (userNote.content.isNotBlank()) userNote.content else "🎵 ${userNote.musicTrackName}"
                        is com.linker.app.domain.model.Note.Countdown -> userNote.content
                        is com.linker.app.domain.model.Note.Location -> if (userNote.placeName.isNotBlank()) "📍 ${userNote.placeName}" else "📍 Location"
                        is com.linker.app.domain.model.Note.Gif -> "GIF"
                        null -> "Bugün nasılsın?"
                    }
                    NoteItem(
                        name = stringResource(R.string.chat_list_your_note),
                        question = noteContent,
                        isSelf = true,
                        note = uiState.userNote,
                        onClick = { 
                            if (uiState.userNote != null) {
                                selectedNoteForDetailState.value = uiState.userNote
                            } else {
                                onNavigateToNoteEditor()
                            }
                        }
                    )
                }
                items(uiState.otherNotes.size) { index ->
                    val note = uiState.otherNotes[index]
                    val noteContent = when (note) {
                        is com.linker.app.domain.model.Note.Text -> note.content
                        is com.linker.app.domain.model.Note.Music -> if (note.content.isNotBlank()) note.content else "🎵 ${note.musicTrackName}"
                        is com.linker.app.domain.model.Note.Countdown -> note.content
                        is com.linker.app.domain.model.Note.Location -> if (note.placeName.isNotBlank()) "📍 ${note.placeName}" else "📍 Location"
                        is com.linker.app.domain.model.Note.Gif -> "GIF"
                    }
                    NoteItem(
                        name = note.author.displayName.ifBlank { stringResource(R.string.chat_list_default_user) },
                        question = noteContent,
                        note = note,
                        onClick = { selectedNoteForDetailState.value = note }
                    )
                }
                items(uiState.onlineUsers.size) { index ->
                    val user = uiState.onlineUsers[index]
                    NoteItem(
                        name = user.displayName.ifBlank { stringResource(R.string.chat_list_default_user) },
                        question = "", // No note bubble
                        note = null,
                        isOnline = true,
                        avatarUrl = user.profileImageUrl,
                        onClick = { onNavigateToChatDetail(user.userId) } // Click navigates to chat
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

        if (selectedNoteForDetail != null) {
            NoteDetailBottomSheet(
                note = selectedNoteForDetail!!,
                onDismiss = { selectedNoteForDetailState.value = null }
            )
        }
    }
}

@Composable
fun NoteItem(
    name: String,
    question: String,
    isSelf: Boolean = false,
    note: com.linker.app.domain.model.Note? = null,
    isOnline: Boolean = false,
    avatarUrl: String? = null,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(80.dp)
    ) {
        val isGif = note is com.linker.app.domain.model.Note.Gif
        Box(contentAlignment = Alignment.TopCenter) {
            if (isGif) {
                coil3.compose.AsyncImage(
                    model = (note as com.linker.app.domain.model.Note.Gif).gifUrl,
                    contentDescription = "GIF",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .padding(top = 24.dp)
                        .size(80.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onClick?.invoke() }
                )
            } else {
                LinkerAvatar(
                    imageUrl = avatarUrl ?: note?.author?.profileImageUrl,
                    size = 80.dp,
                    isOnline = isOnline,
                    storyState = if (isSelf && note == null) StoryState.UNSEEN else StoryState.NONE,
                    modifier = Modifier.padding(top = 24.dp),
                    onClick = { onClick?.invoke() }
                )
                
                // Note Bubble
                if (note != null || (isSelf && question.isNotBlank())) {
                    val bgColor = if (note == null) {
                        LightGray.copy(alpha = 0.5f)
                    } else {
                        note.backgroundColor?.let {
                            try { androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(it)) } catch (e: Exception) { LightGray }
                        } ?: LightGray
                    }
                    
                    val txtColor = if (note == null) {
                        TextPrimary.copy(alpha = 0.7f)
                    } else {
                        note.textColor?.let {
                            try { androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(it)) } catch (e: Exception) { TextPrimary }
                        } ?: TextPrimary
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(15.dp))
                            .background(bgColor)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .widthIn(min = 20.dp, max = 75.dp)
                            .heightIn(min = 20.dp)
                    ) {
                        val displayText = if (note is com.linker.app.domain.model.Note.Countdown) {
                            var tick by remember(note.countdownTargetTime) { mutableStateOf(0) }
                            androidx.compose.runtime.LaunchedEffect(note.countdownTargetTime) {
                                while(true) {
                                    kotlinx.coroutines.delay(1000)
                                    tick++
                                }
                            }
                            val remaining = (note.countdownTargetTime - System.currentTimeMillis() - (tick * 0)).coerceAtLeast(0)
                            val d = (remaining / 86400000).toInt()
                            val h = ((remaining % 86400000) / 3600000).toInt()
                            val m = ((remaining % 3600000) / 60000).toInt()
                            val timeStr = if (d > 0) "${d}g ${h}s" else "${h}s ${m}d"
                            if (question.isNotBlank()) "⏳ $timeStr\n$question" else "⏳ $timeStr\n${note.countdownTitle}"
                        } else {
                            question
                        }

                        Text(
                            text = displayText,
                            color = txtColor,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 10.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailBottomSheet(
    note: com.linker.app.domain.model.Note,
    onDismiss: () -> Unit,
    viewModel: com.linker.app.presentation.screens.note.NoteDetailViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isAudioPlaying by viewModel.audioPlayerManager.isPlaying.collectAsState()
    val isRemotePlaying by viewModel.spotifyAppRemoteManager.isPlaying.collectAsState()
    val audioPosMs by viewModel.audioPlayerManager.currentPositionMs.collectAsState()
    val remotePosMs by viewModel.spotifyAppRemoteManager.currentPositionMs.collectAsState()
    val isRemoteConnected by viewModel.spotifyAppRemoteManager.isConnected.collectAsState()
    val isPremium by viewModel.spotifyAuthManager.isPremium.collectAsState()

    val isPlaying = isAudioPlaying || isRemotePlaying
    val rawPosMs = if (isRemoteConnected) remotePosMs else audioPosMs
    val currentPosMs = if (!isPlaying && rawPosMs == 0L && note is com.linker.app.domain.model.Note.Music) note.clipStartTime else rawPosMs

    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(note) {
        viewModel.initNote(context.findActivity() ?: context, note)
    }

    val bgColor = note.backgroundColor?.let {
        try { androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(it)) } catch (e: Exception) { com.linker.app.presentation.theme.DarkGray }
    } ?: com.linker.app.presentation.theme.DarkGray

    val txtColor = note.textColor?.let {
        try { androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(it)) } catch (e: Exception) { Color.White }
    } ?: Color.White

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = bgColor,
        modifier = Modifier.wrapContentHeight()
    ) {
        val currentUserId = viewModel.currentUserId
        val isOwner = currentUserId == note.author.userId

        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Cleanup on dismiss
            DisposableEffect(note) {
                onDispose {
                    viewModel.clearNote()
                }
            }

            // Auto-close when clip finishes
            LaunchedEffect(currentPosMs) {
                if (note is com.linker.app.domain.model.Note.Music) {
                    if (currentPosMs >= note.clipEndTime && note.clipEndTime > 0) {
                        viewModel.clearNote()
                        onDismiss()
                    }
                }
            }

            // Profile & Author (Hidden if music note, we show album art instead)
            if (note !is com.linker.app.domain.model.Note.Music) {
                LinkerAvatar(
                    imageUrl = note.author.profileImageUrl,
                    size = 80.dp,
                    storyState = StoryState.NONE
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = note.author.displayName,
                    color = txtColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
            
            // Note specific content
            when (note) {
                is com.linker.app.domain.model.Note.Text -> {
                    Text(
                        text = note.content,
                        color = txtColor,
                        fontSize = 20.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
                is com.linker.app.domain.model.Note.Music -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Album Art (Small)
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF2C2C2C))
                                .border(
                                    width = if (isPlaying) 2.dp else 0.dp,
                                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                        listOf(Color(0xFF1DB954), Color(0xFF1565C0))
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    if (isPlaying) {
                                        if (isAudioPlaying) viewModel.audioPlayerManager.pause()
                                        else viewModel.spotifyAppRemoteManager.pause()
                                    } else {
                                        if (isRemoteConnected) {
                                            viewModel.spotifyAppRemoteManager.playTrack(
                                                note.musicTrackId,
                                                note.clipStartTime,
                                                if (note.clipEndTime > 0) note.clipEndTime else null
                                            )
                                        } else if (note.previewUrl != null) {
                                            if (isPremium == false) {
                                                android.widget.Toast.makeText(context, "Orijinal kesit için Premium gereklidir, genel önizleme çalınıyor.", android.widget.Toast.LENGTH_LONG).show()
                                            }
                                            viewModel.audioPlayerManager.playPreview(
                                                note.previewUrl,
                                                0L,
                                                30000L
                                            )
                                        } else {
                                            android.widget.Toast.makeText(context, "Dinlemek için Premium gereklidir.", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                        ) {
                            if (!note.musicAlbumArt.isNullOrBlank()) {
                                coil3.compose.AsyncImage(
                                    model = note.musicAlbumArt,
                                    contentDescription = "Albüm Kapağı",
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            
                            if (isPremium == false) {
                                Box(
                                    modifier = Modifier
                                        .align(androidx.compose.ui.Alignment.TopEnd)
                                        .padding(4.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                        .padding(4.dp)
                                ) {
                                    Text("👑", fontSize = 10.sp)
                                }
                            }
                            if (isPlaying) {
                                val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition()
                                val alpha by infiniteTransition.animateFloat(
                                    initialValue = 0f, targetValue = 0.3f,
                                    animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                                        animation = androidx.compose.animation.core.tween(800),
                                        repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                                    )
                                )
                                Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1DB954).copy(alpha = alpha)))
                            }
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Track Info
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = note.musicTrackName, color = txtColor, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1)
                            Text(text = note.musicArtistName, color = txtColor.copy(alpha = 0.8f), fontSize = 14.sp, maxLines = 1)
                            
                            if (note.content.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "\"${note.content}\"",
                                    color = txtColor,
                                    fontSize = 14.sp,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    maxLines = 2
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Single Line Lyrics
                    if (uiState.isLoadingLyrics) {
                        CircularProgressIndicator(color = txtColor, modifier = Modifier.size(24.dp))
                    } else if (uiState.lyrics.isNotEmpty()) {
                        val activeIndex = remember(currentPosMs, uiState.lyrics) {
                            uiState.lyrics.indexOfLast { it.timeMs <= currentPosMs }.coerceAtLeast(0)
                        }
                        val activeLyric = if (activeIndex >= 0) uiState.lyrics[activeIndex].text else "♪ ♪ ♪"
                        androidx.compose.animation.AnimatedContent(
                            targetState = activeLyric,
                            transitionSpec = {
                                androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(300)).togetherWith(androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(300)))
                            },
                            label = "LyricAnimation"
                        ) { lyricText ->
                            Text(
                                text = lyricText,
                                color = txtColor,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
                is com.linker.app.domain.model.Note.Countdown -> {
                    if (note.content.isNotBlank()) {
                        Text(
                            text = note.content,
                            color = txtColor,
                            fontSize = 16.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(com.linker.app.presentation.theme.Black.copy(alpha = 0.3f), shape = RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = note.countdownTitle, color = txtColor.copy(alpha = 0.7f))
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            var tick by remember(note.countdownTargetTime) { mutableStateOf(0) }
                            LaunchedEffect(note.countdownTargetTime) {
                                while(true) {
                                    kotlinx.coroutines.delay(1000)
                                    tick++
                                }
                            }
                            
                            // Re-evaluate based on the tick by adding (tick * 0)
                            val remaining = (note.countdownTargetTime - System.currentTimeMillis() - (tick * 0)).coerceAtLeast(0)
                            if (remaining <= 0) {
                                Text(text = "Süre doldu!", color = txtColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            } else {
                                val d = (remaining / 86400000).toInt()
                                val h = ((remaining % 86400000) / 3600000).toInt()
                                val m = ((remaining % 3600000) / 60000).toInt()
                                val s = ((remaining % 60000) / 1000).toInt()
                                
                                val timeText = if (d > 0) {
                                    String.format("%dg %02d:%02d:%02d", d, h, m, s)
                                } else {
                                    String.format("%02d:%02d:%02d", h, m, s)
                                }
                                
                                Text(text = timeText, color = txtColor, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                is com.linker.app.domain.model.Note.Location -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(com.linker.app.presentation.theme.Black, shape = RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = Color(0xFF9C27B0),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = note.placeName, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                is com.linker.app.domain.model.Note.Gif -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(com.linker.app.presentation.theme.Black, shape = RoundedCornerShape(12.dp))
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        coil3.compose.AsyncImage(
                            model = note.gifUrl,
                            contentDescription = "GIF",
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(note.aspectRatio ?: 1f)
                        )
                    }
                }
            }
            
            
            if (!isOwner) {
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = Color.DarkGray)
                Spacer(modifier = Modifier.height(16.dp))
                
                // Interaction Bar for viewer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Like Button
                    IconButton(onClick = { viewModel.toggleLike(note.noteId) }) {
                        Icon(
                            imageVector = if (note.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Beğen",
                            tint = if (note.isLiked) Color(0xFFE91E63) else Color.White
                        )
                    }
                    Text(text = note.likesCount.toString(), color = Color.White)
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    // Reply Input
                    var replyText by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Yanıt yaz...", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = txtColor,
                            unfocusedBorderColor = Color.DarkGray,
                            cursorColor = txtColor
                        ),
                        singleLine = true,
                        trailingIcon = {
                            if (replyText.isNotBlank()) {
                                IconButton(onClick = {
                                    val contextText = when (note) {
                                        is com.linker.app.domain.model.Note.Music -> "${note.musicArtistName} - ${note.musicTrackName}"
                                        is com.linker.app.domain.model.Note.Text -> note.content
                                        is com.linker.app.domain.model.Note.Countdown -> note.countdownTitle
                                        is com.linker.app.domain.model.Note.Location -> note.placeName
                                        is com.linker.app.domain.model.Note.Gif -> "GIF Yanıtı"
                                    }
                                    viewModel.replyToNote(note.author.userId, contextText, replyText) { success, err ->
                                        if (success) {
                                            android.widget.Toast.makeText(context, "Yanıt gönderildi", android.widget.Toast.LENGTH_SHORT).show()
                                            replyText = ""
                                            onDismiss()
                                        } else {
                                            android.widget.Toast.makeText(context, err ?: "Gönderilemedi", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }) {
                                    Icon(imageVector = Icons.Default.Send, contentDescription = "Gönder", tint = txtColor)
                                }
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            }

            if (isOwner) {
                val showDeleteDialog = remember { mutableStateOf(false) }
                IconButton(
                    onClick = { showDeleteDialog.value = true },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Sil", tint = txtColor)
                }

                if (showDeleteDialog.value) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showDeleteDialog.value = false },
                        title = { Text("Notu Sil") },
                        text = { Text("Bu notu silmek istediğinize emin misiniz?") },
                        confirmButton = {
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    showDeleteDialog.value = false
                                    viewModel.deleteNote(note.noteId) { success, err ->
                                        if (success) {
                                            onDismiss()
                                        } else {
                                            android.widget.Toast.makeText(context, err ?: "Silinemedi", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            ) {
                                Text("Sil", color = Color(0xFFD32F2F))
                            }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(onClick = { showDeleteDialog.value = false }) {
                                Text("İptal", color = Color.White)
                            }
                        },
                        containerColor = com.linker.app.presentation.theme.DarkGray,
                        titleContentColor = Color.White,
                        textContentColor = Color.LightGray
                    )
                }
            }
        }
    }
}
