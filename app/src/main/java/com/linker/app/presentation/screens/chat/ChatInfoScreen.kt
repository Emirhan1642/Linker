package com.linker.app.presentation.screens.chat

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.linker.app.R
import com.google.firebase.auth.FirebaseAuth
import com.linker.app.domain.model.ChatType
import com.linker.app.domain.model.User
import com.linker.app.presentation.components.LinkerAvatar
import com.linker.app.presentation.theme.Black
import com.linker.app.presentation.theme.TextPrimary
import com.linker.app.presentation.theme.TextSecondary
import com.linker.app.presentation.theme.LightGray
import com.linker.app.presentation.theme.AccentGreen

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatInfoScreen(
    chatId: String,
    onNavigateBack: () -> Unit,
    onNavigateToUserProfile: (String) -> Unit = {},
    viewModel: ChatInfoViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    var memberMenuUser by remember { mutableStateOf<User?>(null) }
    var showEditGroupName by remember { mutableStateOf(false) }
    var groupNameField by remember { mutableStateOf("") }
    var showLeaveGroupConfirm by remember { mutableStateOf(false) }
    var showRemoveFromListConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(chatId) {
        viewModel.loadChatInfo(chatId)
    }

    LaunchedEffect(uiState.feedbackMessage) {
        val msg = uiState.feedbackMessage ?: return@LaunchedEffect
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        viewModel.clearFeedback()
    }

    LaunchedEffect(uiState.chatName) {
        groupNameField = uiState.chatName
    }

    LaunchedEffect(uiState.shouldCloseScreen) {
        if (uiState.shouldCloseScreen) {
            viewModel.consumeCloseScreen()
            onNavigateBack()
        }
    }

    if (showEditGroupName) {
        AlertDialog(
            onDismissRequest = { showEditGroupName = false },
            title = { Text("Group name", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = groupNameField,
                    onValueChange = { groupNameField = it },
                    singleLine = true,
                    label = { Text("Name") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateGroupName(groupNameField)
                        showEditGroupName = false
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEditGroupName = false }) { Text("Cancel") }
            }
        )
    }

    if (showLeaveGroupConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveGroupConfirm = false },
            title = { Text("Leave group?", color = TextPrimary) },
            text = {
                Text(
                    text = "Are you sure you want to leave this group?",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLeaveGroupConfirm = false
                        showRemoveFromListConfirm = true
                    }
                ) { Text("Yes") }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveGroupConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showRemoveFromListConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveFromListConfirm = false },
            title = { Text("Remove from list?", color = TextPrimary) },
            text = {
                Text(
                    text = "Do you also want to remove this group from your chat list?",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRemoveFromListConfirm = false
                        viewModel.leaveGroup(removeFromList = true)
                    }
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRemoveFromListConfirm = false
                        viewModel.leaveGroup(removeFromList = false)
                    }
                ) { Text("Keep") }
            }
        )
    }

    memberMenuUser?.let { target ->
        val isAdmin = uiState.groupAdminIds.contains(target.userId) ||
            target.userId == uiState.groupCreatedBy
        val canDemote = uiState.groupAdminIds.contains(target.userId) &&
            target.userId != uiState.groupCreatedBy &&
            uiState.groupAdminIds.size > 1
        AlertDialog(
            onDismissRequest = { memberMenuUser = null },
            title = {
                Text(
                    target.displayName.ifBlank { target.username },
                    color = TextPrimary
                )
            },
            text = {
                Column {
                    if (!isAdmin) {
                        TextButton(
                            onClick = {
                                viewModel.promoteMember(target.userId)
                                memberMenuUser = null
                            }
                        ) { Text("Make admin", color = AccentGreen) }
                    }
                    if (canDemote) {
                        TextButton(
                            onClick = {
                                viewModel.demoteMember(target.userId)
                                memberMenuUser = null
                            }
                        ) { Text("Remove admin role") }
                    }
                    if (target.userId != uiState.groupCreatedBy) {
                        TextButton(
                            onClick = {
                                viewModel.removeMember(target.userId)
                                memberMenuUser = null
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) { Text("Remove from group") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { memberMenuUser = null }) { Text("Close") }
            }
        )
    }

    Scaffold(
        containerColor = Black
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            painterResource(id = R.drawable.ic_arrow_left_01_outline),
                            contentDescription = "Back",
                            tint = TextPrimary,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text("Chat Info", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.weight(1f))
                }
            }

            if (uiState.isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(300.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = TextSecondary)
                    }
                }
            } else if (uiState.error != null) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(300.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(uiState.error ?: "Error", color = TextSecondary, fontSize = 16.sp)
                    }
                }
            } else {
                // Avatar & Name
                item {
                    val displayName = uiState.chatName
                    val username = uiState.otherParticipant?.username
                        ?: if (uiState.isGroupChat) "${uiState.participants.size} members" else null

                    LinkerAvatar(
                        imageUrl = uiState.chatImageUrl,
                        size = 150.dp,
                        storyState = com.linker.app.presentation.components.StoryState.NONE
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(displayName, color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    if (username != null) {
                        Text("@$username", color = TextSecondary, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Group members (if group chat)
                if (uiState.isGroupChat && uiState.participants.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                        ) {
                            Text("Members", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 12.dp))
                            uiState.participants.forEach { participant ->
                                val isMemberAdmin = uiState.groupAdminIds.contains(participant.userId) ||
                                    participant.userId == uiState.groupCreatedBy
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = { onNavigateToUserProfile(participant.userId) },
                                            onLongClick = {
                                                if (uiState.canManageGroup && participant.userId != currentUserId) {
                                                    memberMenuUser = participant
                                                }
                                            }
                                        )
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    LinkerAvatar(
                                        imageUrl = participant.profileImageUrl,
                                        size = 40.dp,
                                        storyState = com.linker.app.presentation.components.StoryState.NONE
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            participant.displayName.ifBlank { participant.username },
                                            color = TextPrimary,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        if (participant.userId == currentUserId) {
                                            Text("You", color = TextSecondary, fontSize = 11.sp)
                                        } else if (isMemberAdmin) {
                                            Text("Admin", color = AccentGreen, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }

                // Options List
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                        // Profile
                        if (!uiState.isGroupChat) {
                            ChatInfoOption(
                                icon = R.drawable.ic_enhance_user_ai_outline,
                                title = "Profile",
                                subtitle = uiState.otherParticipant?.username,
                                onClick = { uiState.otherParticipant?.let { onNavigateToUserProfile(it.userId) } }
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                        }

                        if (uiState.isGroupChat && uiState.canManageGroup) {
                            ChatInfoOption(
                                icon = R.drawable.ic_user_edit_outline,
                                title = "Edit group name",
                                subtitle = uiState.chatName,
                                onClick = { showEditGroupName = true }
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                        }

                        // Silent Mode
                        ChatInfoOption(
                            icon = R.drawable.ic_bell_2_outline,
                            title = "Silent Mode",
                            subtitle = if (uiState.isMuted) "On" else "Off",
                            onClick = { viewModel.toggleMute() }
                        )
                        Spacer(modifier = Modifier.height(20.dp))

                        // Pin Chat
                        ChatInfoOption(
                            icon = R.drawable.ic_security_safe_outline,
                            title = "Pin Chat",
                            subtitle = if (uiState.isPinned) "Pinned" else "Not pinned",
                            onClick = { viewModel.togglePin() }
                        )
                        Spacer(modifier = Modifier.height(20.dp))

                        // Search
                        ChatInfoOption(
                            icon = R.drawable.ic_search_status_1_outline,
                            title = "Search",
                            subtitle = null,
                            onClick = { /* TODO: open search in this chat */ }
                        )
                        Spacer(modifier = Modifier.height(20.dp))

                        // Theme
                        ChatInfoOption(
                            icon = R.drawable.ic_paint_brush_2_outline,
                            title = "Theme",
                            subtitle = uiState.theme ?: "Default",
                            onClick = { /* TODO: open theme picker */ }
                        )
                        Spacer(modifier = Modifier.height(20.dp))

                        // Archive
                        ChatInfoOption(
                            icon = R.drawable.ic_archive_outline,
                            title = "Archive Chat",
                            subtitle = if (uiState.isArchived) "UnArchive" else "Archive",
                            onClick = { viewModel.toggleArchive() }
                        )
                        Spacer(modifier = Modifier.height(20.dp))

                        if (!uiState.isGroupChat) {
                            // Disappearing messages are private-chat specific.
                            ChatInfoOption(
                                icon = R.drawable.ic_ai_sand_timer_outline,
                                title = "Disappearing messages",
                                subtitle = "Off",
                                onClick = { /* TODO */ }
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                        }

                        // Security
                        ChatInfoOption(
                            icon = R.drawable.ic_security_safe_outline,
                            title = "Security",
                            subtitle = "End-to-end encrypted",
                            subtitleStyle = true,
                            onClick = { /* TODO */ }
                        )
                        Spacer(modifier = Modifier.height(20.dp))

                        if (!uiState.isGroupChat) {
                            // Blocking is private-chat specific.
                            ChatInfoOption(
                                icon = R.drawable.ic_forbidden_outline,
                                title = "Block User",
                                subtitle = if (uiState.isBlocked) "UnBlock" else "Block",
                                onClick = { viewModel.toggleBlock() }
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                        }

                        // Nicknames
                        ChatInfoOption(
                            icon = R.drawable.ic_user_edit_outline,
                            title = "Nicknames",
                            subtitle = null,
                            onClick = { /* TODO */ }
                        )
                        Spacer(modifier = Modifier.height(20.dp))

                        // Create a group (only for private chats)
                        if (!uiState.isGroupChat) {
                            ChatInfoOption(
                                icon = R.drawable.ic_ai_users_outline,
                                title = "Create a group",
                                subtitle = null,
                                onClick = { /* TODO: navigate to create group with this user */ }
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                        }

                        // Fav Chat
                        ChatInfoOption(
                            icon = R.drawable.ic_star_outline,
                            title = "Favorite Chat",
                            subtitle = if (uiState.isFavorited) "UnFavorite" else "Favorite",
                            onClick = { viewModel.toggleFavorite() }
                        )
                        Spacer(modifier = Modifier.height(20.dp))

                        if (uiState.isGroupChat) {
                            ChatInfoOption(
                                icon = R.drawable.ic_close_circle_outline,
                                title = "Leave Group",
                                subtitle = null,
                                onClick = { showLeaveGroupConfirm = true }
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                        }
                    }
                }

                // Tabs
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TabRowIconItem(icon = R.drawable.ic_gallery_outline, isSelected = selectedTab == 0) { selectedTab = 0 }
                        TabRowIconItem(icon = R.drawable.ic_play_add_outline, isSelected = selectedTab == 1) { selectedTab = 1 }
                        TabRowIconItem(icon = R.drawable.ic_toy_6_outline, isSelected = selectedTab == 2) { selectedTab = 2 }
                        if (uiState.isGroupChat) {
                            TabRowIconItem(icon = R.drawable.ic_ai_users_outline, isSelected = selectedTab == 3) { selectedTab = 3 }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(LightGray.copy(alpha = 0.5f)))
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Grid content based on selected tab
                when (selectedTab) {
                    0 -> {
                        // Gallery tab
                        if (uiState.sharedMedia.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(150.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No shared media", color = TextSecondary, fontSize = 14.sp)
                                }
                            }
                        } else {
                            items(uiState.sharedMedia) { media ->
                                SharedMediaThumbnail(media)
                            }
                        }
                    }
                    1 -> {
                        // Reels/Videos tab
                        val videos = uiState.sharedMedia.filter { it.mediaType == MediaType.VIDEO }
                        if (videos.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(150.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No shared videos", color = TextSecondary, fontSize = 14.sp)
                                }
                            }
                        } else {
                            items(videos) { media ->
                                SharedMediaThumbnail(media)
                            }
                        }
                    }
                    2 -> {
                        // Links tab
                        if (uiState.sharedLinks.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(150.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No shared links", color = TextSecondary, fontSize = 14.sp)
                                }
                            }
                        } else {
                            items(uiState.sharedLinks) { link ->
                                SharedLinkItemRow(link)
                            }
                        }
                    }
                    3 -> {
                        // Members tab (group chats only)
                        items(uiState.participants) { participant ->
                            val isMemberAdmin = uiState.groupAdminIds.contains(participant.userId) ||
                                participant.userId == uiState.groupCreatedBy
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = { onNavigateToUserProfile(participant.userId) },
                                        onLongClick = {
                                            if (uiState.canManageGroup && participant.userId != currentUserId) {
                                                memberMenuUser = participant
                                            }
                                        }
                                    )
                                    .padding(horizontal = 24.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                LinkerAvatar(
                                    imageUrl = participant.profileImageUrl,
                                    size = 48.dp,
                                    storyState = com.linker.app.presentation.components.StoryState.NONE
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        participant.displayName.ifBlank { participant.username },
                                        color = TextPrimary,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (participant.userId == currentUserId) {
                                        Text("You", color = AccentGreen, fontSize = 12.sp)
                                    } else if (isMemberAdmin) {
                                        Text("Admin", color = AccentGreen, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SharedMediaThumbnail(media: SharedMediaItem) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(LightGray)
    ) {
        // TODO: Replace with Coil AsyncImage once mediaUrl is valid
        Icon(
            painter = painterResource(
                id = when (media.mediaType) {
                    MediaType.VIDEO -> R.drawable.ic_play_add_outline
                    MediaType.GIF -> R.drawable.ic_toy_6_outline
                    MediaType.IMAGE -> R.drawable.ic_gallery_outline
                }
            ),
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.align(Alignment.Center).size(32.dp)
        )
    }
}

@Composable
fun SharedLinkItemRow(link: SharedLinkItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* TODO: navigate to link */ }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(LightGray),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_toy_6_outline),
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = link.title,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Shared by ${link.senderName}",
                color = TextSecondary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun ChatInfoOption(
    icon: Int,
    title: String,
    subtitle: String?,
    subtitleStyle: Boolean = false,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = title,
            tint = TextPrimary,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = if (subtitleStyle) TextPrimary else TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }
        Icon(
            painter = painterResource(id = R.drawable.ic_arrow_left_01_outline),
            contentDescription = "Go",
            tint = TextSecondary,
            modifier = Modifier.size(20.dp).rotate(180f)
        )
    }
}

@Composable
fun TabRowIconItem(icon: Int, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clickable { onClick() }.padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = null,
            tint = if (isSelected) Color.White else TextSecondary,
            modifier = Modifier.size(32.dp).padding(bottom = 8.dp)
        )
        Box(
            modifier = Modifier
                .height(2.dp)
                .width(48.dp)
                .background(if (isSelected) Color.White else Color.Transparent)
        )
    }
}
