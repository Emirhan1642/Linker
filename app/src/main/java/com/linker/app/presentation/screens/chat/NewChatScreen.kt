package com.linker.app.presentation.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linker.app.R
import com.linker.app.presentation.components.LinkerAvatar
import com.linker.app.presentation.components.StoryState
import com.linker.app.presentation.components.LinkerSearchBar
import com.linker.app.presentation.theme.*
import kotlinx.coroutines.launch

@Composable
fun NewChatScreen(
    onNavigateBack: () -> Unit,
    onNavigateToChat: (String) -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
    suggestViewModel: NewChatViewModel = hiltViewModel()
) {
    val uiState by suggestViewModel.uiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    var isGroupMode by remember { mutableStateOf(false) }
    var selectedUsers by remember { mutableStateOf(setOf<String>()) }
    var showGroupDetails by remember { mutableStateOf(false) }
    var groupName by remember { mutableStateOf("") }
    var canEditSettings by remember { mutableStateOf(true) }
    var canSendMessages by remember { mutableStateOf(true) }
    var canAddMembers by remember { mutableStateOf(true) }
    var disappearingMessages by remember { mutableStateOf(false) }

    val filtered = if (uiState.query.isBlank()) {
        uiState.suggested
    } else {
        uiState.suggested.filter {
            it.displayName.contains(uiState.query, ignoreCase = true) ||
                it.username.contains(uiState.query, ignoreCase = true)
        }
    }

    Scaffold(containerColor = Black) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        painterResource(R.drawable.ic_arrow_left_01_outline),
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
                LinkerSearchBar(
                    query = uiState.query,
                    onQueryChange = suggestViewModel::onQueryChange,
                    placeholder = stringResource(R.string.new_chat_search_hint),
                    modifier = Modifier.weight(1f)
                )
            }

            if (!showGroupDetails) {
                UserSelectionSection(
                    isGroupMode = isGroupMode,
                    filteredUsers = filtered,
                    selectedUsers = selectedUsers,
                    onToggleGroupMode = { isGroupMode = true },
                    onUserClick = { user ->
                        if (isGroupMode) {
                            selectedUsers = if (selectedUsers.contains(user.userId)) {
                                selectedUsers - user.userId
                            } else {
                                selectedUsers + user.userId
                            }
                        } else {
                            coroutineScope.launch {
                                val result = viewModel.createPrivateChat(user.userId)
                                if (result is com.linker.app.core.util.Result.Success) {
                                    onNavigateToChat(result.data.chatId)
                                }
                            }
                        }
                    },
                    onContinueToGroupDetails = { showGroupDetails = true }
                )
            } else {
                GroupDetailsSection(
                    groupName = groupName,
                    onGroupNameChange = { groupName = it },
                    canEditSettings = canEditSettings,
                    onCanEditSettingsChange = { canEditSettings = it },
                    canSendMessages = canSendMessages,
                    onCanSendMessagesChange = { canSendMessages = it },
                    canAddMembers = canAddMembers,
                    onCanAddMembersChange = { canAddMembers = it },
                    disappearingMessages = disappearingMessages,
                    onDisappearingMessagesChange = { disappearingMessages = it },
                    onCreateGroup = {
                        coroutineScope.launch {
                            val permissions = mapOf(
                                "canEditSettings" to canEditSettings,
                                "canSendMessages" to canSendMessages,
                                "canAddMembers" to canAddMembers,
                                "disappearingMessages" to disappearingMessages
                            )
                            val result = viewModel.createGroupChat(groupName.trim(), selectedUsers.toList(), permissions)
                            if (result is com.linker.app.core.util.Result.Success) {
                                onNavigateToChat(result.data.chatId)
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.UserSelectionSection(
    isGroupMode: Boolean,
    filteredUsers: List<com.linker.app.domain.model.User>,
    selectedUsers: Set<String>,
    onToggleGroupMode: () -> Unit,
    onUserClick: (com.linker.app.domain.model.User) -> Unit,
    onContinueToGroupDetails: () -> Unit
) {
    Text(
        text = if (isGroupMode) stringResource(R.string.new_chat_select_members) else stringResource(R.string.new_chat_suggested),
        color = TextPrimary,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )

    Button(
        onClick = onToggleGroupMode,
        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
    ) {
        Text(stringResource(R.string.new_chat_create_group), color = Black, fontWeight = FontWeight.SemiBold)
    }

    Spacer(modifier = Modifier.height(8.dp))

    LazyColumn(
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        items(filteredUsers, key = { it.userId }) { user ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onUserClick(user) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LinkerAvatar(imageUrl = user.profileImageUrl, size = 56.dp, storyState = StoryState.NONE)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(user.displayName, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("@${user.username}", color = TextSecondary, fontSize = 12.sp)
                }
                if (isGroupMode) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selectedUsers.contains(user.userId)) AccentGreen else Color(0xFF2E2E32)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedUsers.contains(user.userId)) {
                            Text("✓", color = Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (isGroupMode) {
        Button(
            onClick = onContinueToGroupDetails,
            enabled = selectedUsers.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(stringResource(R.string.new_chat_continue), color = Black, fontWeight = FontWeight.SemiBold)
        }
    } else {
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun GroupDetailsSection(
    groupName: String,
    onGroupNameChange: (String) -> Unit,
    canEditSettings: Boolean,
    onCanEditSettingsChange: (Boolean) -> Unit,
    canSendMessages: Boolean,
    onCanSendMessagesChange: (Boolean) -> Unit,
    canAddMembers: Boolean,
    onCanAddMembersChange: (Boolean) -> Unit,
    disappearingMessages: Boolean,
    onDisappearingMessagesChange: (Boolean) -> Unit,
    onCreateGroup: () -> Unit
) {
    Text(
        text = stringResource(R.string.new_chat_group_details),
        color = TextPrimary,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
    OutlinedTextField(
        value = groupName,
        onValueChange = onGroupNameChange,
        label = { Text(stringResource(R.string.new_chat_group_name_hint)) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    )
    Spacer(modifier = Modifier.height(12.dp))
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        PermissionToggle(stringResource(R.string.new_chat_perm_edit_settings), canEditSettings, onCanEditSettingsChange)
        PermissionToggle(stringResource(R.string.new_chat_perm_send_messages), canSendMessages, onCanSendMessagesChange)
        PermissionToggle(stringResource(R.string.new_chat_perm_add_members), canAddMembers, onCanAddMembersChange)
        PermissionToggle(stringResource(R.string.new_chat_perm_disappearing), disappearingMessages, onDisappearingMessagesChange)
    }
    Spacer(modifier = Modifier.height(16.dp))
    Button(
        onClick = onCreateGroup,
        enabled = groupName.isNotBlank(),
        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(stringResource(R.string.new_chat_create_group_btn), color = Black, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PermissionToggle(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
