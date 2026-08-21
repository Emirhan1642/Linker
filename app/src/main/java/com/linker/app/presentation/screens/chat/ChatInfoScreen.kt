package com.linker.app.presentation.screens.chat

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.linker.app.R
import com.google.firebase.auth.FirebaseAuth
import com.linker.app.domain.model.User
import com.linker.app.presentation.components.LinkerAvatar
import com.linker.app.presentation.components.StoryState
import com.linker.app.presentation.theme.AccentGreen
import com.linker.app.presentation.theme.Black
import com.linker.app.presentation.theme.LightGray
import com.linker.app.presentation.theme.TextPrimary
import com.linker.app.presentation.theme.TextSecondary

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
        val msg = uiState.feedbackMessage?.asString(context) ?: return@LaunchedEffect
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        viewModel.clearFeedback()
    }

    LaunchedEffect(uiState.basicInfo.chatName) {
        groupNameField = uiState.basicInfo.chatName
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
            title = { Text(stringResource(R.string.chat_info_group_name), color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = groupNameField,
                    onValueChange = { groupNameField = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.chat_info_name)) }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateGroupName(groupNameField)
                        showEditGroupName = false
                    }
                ) { Text(stringResource(R.string.chat_info_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showEditGroupName = false }) { Text(stringResource(R.string.chat_info_cancel)) }
            }
        )
    }

    if (showLeaveGroupConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveGroupConfirm = false },
            title = { Text(stringResource(R.string.chat_info_leave_group_title), color = TextPrimary) },
            text = {
                Text(
                    text = stringResource(R.string.chat_info_leave_group_desc),
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLeaveGroupConfirm = false
                        showRemoveFromListConfirm = true
                    }
                ) { Text(stringResource(R.string.chat_info_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveGroupConfirm = false }) { Text(stringResource(R.string.chat_info_cancel)) }
            }
        )
    }

    if (showRemoveFromListConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveFromListConfirm = false },
            title = { Text(stringResource(R.string.chat_info_remove_list_title), color = TextPrimary) },
            text = {
                Text(
                    text = stringResource(R.string.chat_info_remove_list_desc),
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRemoveFromListConfirm = false
                        viewModel.leaveGroup(removeFromList = true)
                    }
                ) { Text(stringResource(R.string.chat_info_remove)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRemoveFromListConfirm = false
                        viewModel.leaveGroup(removeFromList = false)
                    }
                ) { Text(stringResource(R.string.chat_info_keep)) }
            }
        )
    }

    memberMenuUser?.let { target ->
        val isAdmin = uiState.basicInfo.groupAdminIds.contains(target.userId) ||
            target.userId == uiState.basicInfo.groupCreatedBy
        val canDemote = uiState.basicInfo.groupAdminIds.contains(target.userId) &&
            target.userId != uiState.basicInfo.groupCreatedBy &&
            uiState.basicInfo.groupAdminIds.size > 1

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
                        ) { Text(stringResource(R.string.chat_info_make_admin), color = AccentGreen) }
                    }
                    if (canDemote) {
                        TextButton(
                            onClick = {
                                viewModel.demoteMember(target.userId)
                                memberMenuUser = null
                            }
                        ) { Text(stringResource(R.string.chat_info_remove_admin_role)) }
                    }
                    if (target.userId != uiState.basicInfo.groupCreatedBy) {
                        TextButton(
                            onClick = {
                                viewModel.removeMember(target.userId)
                                memberMenuUser = null
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) { Text(stringResource(R.string.chat_info_remove_from_group)) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { memberMenuUser = null }) { Text(stringResource(R.string.chat_info_close)) }
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
            item {
                ChatInfoHeader(onNavigateBack)
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
                        Text(uiState.error?.asString() ?: "", color = TextSecondary, fontSize = 16.sp)
                    }
                }
            } else {
                item {
                    ChatProfileSection(uiState.basicInfo)
                }

                if (uiState.basicInfo.isGroupChat && uiState.basicInfo.participants.isNotEmpty()) {
                    item {
                        ChatMembersList(
                            basicInfo = uiState.basicInfo,
                            currentUserId = currentUserId,
                            onNavigateToUserProfile = onNavigateToUserProfile,
                            onMemberLongClick = { member -> memberMenuUser = member }
                        )
                    }
                }

                item {
                    ChatOptionsList(
                        uiState = uiState,
                        viewModel = viewModel,
                        onNavigateToUserProfile = onNavigateToUserProfile,
                        onShowEditGroupName = { showEditGroupName = true },
                        onShowLeaveGroupConfirm = { showLeaveGroupConfirm = true }
                    )
                }

                item {
                    ChatTabsSection(
                        selectedTab = selectedTab,
                        isGroupChat = uiState.basicInfo.isGroupChat,
                        onTabSelected = { selectedTab = it }
                    )
                }

                when (selectedTab) {
                    0 -> galleryContent(uiState.sharedMediaState)
                    1 -> videosContent(uiState.sharedMediaState)
                    2 -> linksContent(uiState.sharedMediaState)
                    3 -> membersTabContent(
                        basicInfo = uiState.basicInfo,
                        currentUserId = currentUserId,
                        onNavigateToUserProfile = onNavigateToUserProfile,
                        onMemberLongClick = { member -> memberMenuUser = member }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatInfoHeader(onNavigateBack: () -> Unit) {
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
        Text(stringResource(R.string.chat_info_title), color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ChatProfileSection(basicInfo: ChatBasicInfo) {
    LinkerAvatar(
        imageUrl = basicInfo.chatImageUrl,
        size = 150.dp,
        storyState = StoryState.NONE
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(basicInfo.chatName, color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    if (basicInfo.chatSubtitle != null) {
        Text(basicInfo.chatSubtitle, color = TextSecondary, fontSize = 14.sp)
    }
    Spacer(modifier = Modifier.height(24.dp))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatMembersList(
    basicInfo: ChatBasicInfo,
    currentUserId: String,
    onNavigateToUserProfile: (String) -> Unit,
    onMemberLongClick: (User) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
    ) {
        Text(
            stringResource(R.string.chat_info_members_title),
            color = TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        basicInfo.participants.forEach { participant ->
            val isMemberAdmin = basicInfo.groupAdminIds.contains(participant.userId) ||
                participant.userId == basicInfo.groupCreatedBy
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onNavigateToUserProfile(participant.userId) },
                        onLongClick = {
                            if (basicInfo.canManageGroup && participant.userId != currentUserId) {
                                onMemberLongClick(participant)
                            }
                        }
                    )
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LinkerAvatar(
                    imageUrl = participant.profileImageUrl,
                    size = 40.dp,
                    storyState = StoryState.NONE
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
                        Text(stringResource(R.string.chat_info_you), color = TextSecondary, fontSize = 11.sp)
                    } else if (isMemberAdmin) {
                        Text(stringResource(R.string.chat_info_admin), color = AccentGreen, fontSize = 11.sp)
                    }
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(20.dp))
}

@Composable
private fun ChatOptionsList(
    uiState: ChatInfoUiState,
    viewModel: ChatInfoViewModel,
    onNavigateToUserProfile: (String) -> Unit,
    onShowEditGroupName: () -> Unit,
    onShowLeaveGroupConfirm: () -> Unit
) {
    val context = LocalContext.current
    val comingSoonMsg = stringResource(R.string.chat_info_feature_coming_soon)
    val showComingSoon = { Toast.makeText(context, comingSoonMsg, Toast.LENGTH_SHORT).show() }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        if (!uiState.basicInfo.isGroupChat) {
            ChatInfoOption(
                icon = R.drawable.ic_enhance_user_ai_outline,
                title = stringResource(R.string.chat_info_profile),
                subtitle = uiState.basicInfo.otherParticipant?.username,
                onClick = { uiState.basicInfo.otherParticipant?.let { onNavigateToUserProfile(it.userId) } }
            )
            Spacer(modifier = Modifier.height(20.dp))
        }

        if (uiState.basicInfo.isGroupChat && uiState.basicInfo.canManageGroup) {
            ChatInfoOption(
                icon = R.drawable.ic_user_edit_outline,
                title = stringResource(R.string.chat_info_edit_group_name),
                subtitle = uiState.basicInfo.chatName,
                onClick = onShowEditGroupName
            )
            Spacer(modifier = Modifier.height(20.dp))
        }

        ChatInfoOption(
            icon = R.drawable.ic_bell_2_outline,
            title = stringResource(R.string.chat_info_silent_mode),
            subtitle = if (uiState.settings.isMuted) stringResource(R.string.chat_info_on) else stringResource(R.string.chat_info_off),
            onClick = { viewModel.toggleMute() }
        )
        Spacer(modifier = Modifier.height(20.dp))

        ChatInfoOption(
            icon = R.drawable.ic_security_safe_outline,
            title = stringResource(R.string.chat_info_pin_chat),
            subtitle = if (uiState.settings.isPinned) stringResource(R.string.chat_info_pinned) else stringResource(R.string.chat_info_not_pinned),
            onClick = { viewModel.togglePin() }
        )
        Spacer(modifier = Modifier.height(20.dp))

        ChatInfoOption(
            icon = R.drawable.ic_search_status_1_outline,
            title = stringResource(R.string.chat_info_search),
            subtitle = null,
            onClick = showComingSoon
        )
        Spacer(modifier = Modifier.height(20.dp))

        ChatInfoOption(
            icon = R.drawable.ic_paint_brush_2_outline,
            title = stringResource(R.string.chat_info_theme),
            subtitle = uiState.settings.theme ?: stringResource(R.string.chat_info_default_theme),
            onClick = showComingSoon
        )
        Spacer(modifier = Modifier.height(20.dp))

        ChatInfoOption(
            icon = R.drawable.ic_archive_outline,
            title = stringResource(R.string.chat_info_archive_chat),
            subtitle = if (uiState.settings.isArchived) stringResource(R.string.chat_info_unarchive) else stringResource(R.string.chat_info_archive),
            onClick = { viewModel.toggleArchive() }
        )
        Spacer(modifier = Modifier.height(20.dp))

        if (!uiState.basicInfo.isGroupChat) {
            ChatInfoOption(
                icon = R.drawable.ic_ai_sand_timer_outline,
                title = stringResource(R.string.chat_info_disappearing_messages),
                subtitle = stringResource(R.string.chat_info_off),
                onClick = showComingSoon
            )
            Spacer(modifier = Modifier.height(20.dp))
        }

        ChatInfoOption(
            icon = R.drawable.ic_security_safe_outline,
            title = stringResource(R.string.chat_info_security),
            subtitle = stringResource(R.string.chat_info_e2e_encrypted),
            subtitleStyle = true,
            onClick = showComingSoon
        )
        Spacer(modifier = Modifier.height(20.dp))

        if (!uiState.basicInfo.isGroupChat) {
            ChatInfoOption(
                icon = R.drawable.ic_forbidden_outline,
                title = stringResource(R.string.chat_info_block_user),
                subtitle = if (uiState.settings.isBlocked) stringResource(R.string.chat_info_unblock) else stringResource(R.string.chat_info_block),
                onClick = { viewModel.toggleBlock() }
            )
            Spacer(modifier = Modifier.height(20.dp))
        }

        ChatInfoOption(
            icon = R.drawable.ic_user_edit_outline,
            title = stringResource(R.string.chat_info_nicknames),
            subtitle = null,
            onClick = showComingSoon
        )
        Spacer(modifier = Modifier.height(20.dp))

        if (!uiState.basicInfo.isGroupChat) {
            ChatInfoOption(
                icon = R.drawable.ic_ai_users_outline,
                title = stringResource(R.string.chat_info_create_group),
                subtitle = null,
                onClick = showComingSoon
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        ChatInfoOption(
            icon = R.drawable.ic_star_outline,
            title = stringResource(R.string.chat_info_favorite_chat),
            subtitle = if (uiState.settings.isFavorited) stringResource(R.string.chat_info_unfavorite) else stringResource(R.string.chat_info_favorite),
            onClick = { viewModel.toggleFavorite() }
        )
        Spacer(modifier = Modifier.height(20.dp))

        if (uiState.basicInfo.isGroupChat) {
            ChatInfoOption(
                icon = R.drawable.ic_close_circle_outline,
                title = stringResource(R.string.chat_info_leave_group),
                subtitle = null,
                onClick = onShowLeaveGroupConfirm
            )
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ChatTabsSection(selectedTab: Int, isGroupChat: Boolean, onTabSelected: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TabRowIconItem(icon = R.drawable.ic_gallery_outline, isSelected = selectedTab == 0) { onTabSelected(0) }
        TabRowIconItem(icon = R.drawable.ic_play_add_outline, isSelected = selectedTab == 1) { onTabSelected(1) }
        TabRowIconItem(icon = R.drawable.ic_toy_6_outline, isSelected = selectedTab == 2) { onTabSelected(2) }
        if (isGroupChat) {
            TabRowIconItem(icon = R.drawable.ic_ai_users_outline, isSelected = selectedTab == 3) { onTabSelected(3) }
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(LightGray.copy(alpha = 0.5f)))
    Spacer(modifier = Modifier.height(16.dp))
}

// Extracted LazyListScrope extensions
@OptIn(ExperimentalFoundationApi::class)
private fun androidx.compose.foundation.lazy.LazyListScope.galleryContent(sharedMediaState: SharedMediaState) {
    if (sharedMediaState.sharedMedia.isEmpty()) {
        item {
            Box(
                modifier = Modifier.fillMaxWidth().height(150.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.chat_info_no_shared_media), color = TextSecondary, fontSize = 14.sp)
            }
        }
    } else {
        items(sharedMediaState.sharedMedia) { media ->
            SharedMediaThumbnail(media)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.videosContent(sharedMediaState: SharedMediaState) {
    val videos = sharedMediaState.sharedMedia.filter { it.mediaType == MediaType.VIDEO }
    if (videos.isEmpty()) {
        item {
            Box(
                modifier = Modifier.fillMaxWidth().height(150.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.chat_info_no_shared_videos), color = TextSecondary, fontSize = 14.sp)
            }
        }
    } else {
        items(videos) { media ->
            SharedMediaThumbnail(media)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.linksContent(sharedMediaState: SharedMediaState) {
    if (sharedMediaState.sharedLinks.isEmpty()) {
        item {
            Box(
                modifier = Modifier.fillMaxWidth().height(150.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.chat_info_no_shared_links), color = TextSecondary, fontSize = 14.sp)
            }
        }
    } else {
        items(sharedMediaState.sharedLinks) { link ->
            SharedLinkItemRow(link)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun androidx.compose.foundation.lazy.LazyListScope.membersTabContent(
    basicInfo: ChatBasicInfo,
    currentUserId: String,
    onNavigateToUserProfile: (String) -> Unit,
    onMemberLongClick: (User) -> Unit
) {
    items(basicInfo.participants) { participant ->
        val isMemberAdmin = basicInfo.groupAdminIds.contains(participant.userId) ||
            participant.userId == basicInfo.groupCreatedBy
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { onNavigateToUserProfile(participant.userId) },
                    onLongClick = {
                        if (basicInfo.canManageGroup && participant.userId != currentUserId) {
                            onMemberLongClick(participant)
                        }
                    }
                )
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LinkerAvatar(
                imageUrl = participant.profileImageUrl,
                size = 48.dp,
                storyState = StoryState.NONE
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
                    Text(stringResource(R.string.chat_info_you), color = AccentGreen, fontSize = 12.sp)
                } else if (isMemberAdmin) {
                    Text(stringResource(R.string.chat_info_admin), color = AccentGreen, fontSize = 12.sp)
                }
            }
        }
    }
}
