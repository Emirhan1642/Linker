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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.linker.app.R
import com.linker.app.presentation.components.LinkerAvatar
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
    val uiState by suggestViewModel.uiState.collectAsState()
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
                    Icon(painterResource(R.drawable.ic_arrow_left_01_outline), contentDescription = "Back", tint = TextPrimary)
                }
                LinkerSearchBar(
                    query = uiState.query,
                    onQueryChange = suggestViewModel::onQueryChange,
                    placeholder = "Search people...",
                    modifier = Modifier.weight(1f)
                )
            }

            if (!showGroupDetails) {
                Text(
                    text = if (isGroupMode) "Select members" else "Suggested",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                Button(
                    onClick = { isGroupMode = true },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                ) {
                    Text("Create a Group", color = Black, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(filtered, key = { it.userId }) { user ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
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
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LinkerAvatar(imageUrl = user.profileImageUrl, size = 56.dp, hasStory = false)
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
                        onClick = { showGroupDetails = true },
                        enabled = selectedUsers.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text("Continue", color = Black, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            } else {
                Text(
                    text = "Group details",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("Group name") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    PermissionToggle("Members can edit settings", canEditSettings) { canEditSettings = it }
                    PermissionToggle("Members can send messages", canSendMessages) { canSendMessages = it }
                    PermissionToggle("Members can add members", canAddMembers) { canAddMembers = it }
                    PermissionToggle("Disappearing messages", disappearingMessages) { disappearingMessages = it }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
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
                    },
                    enabled = groupName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Text("Create Group", color = Black, fontWeight = FontWeight.SemiBold)
                }
            }
        }
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
