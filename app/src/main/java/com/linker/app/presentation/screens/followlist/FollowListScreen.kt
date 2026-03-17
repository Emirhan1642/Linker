package com.linker.app.presentation.screens.followlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.linker.app.R
import com.linker.app.domain.model.User
import com.linker.app.presentation.components.LinkerAvatar
import com.linker.app.presentation.components.StoryState
import com.linker.app.presentation.theme.*

@Composable
fun FollowListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToUserProfile: (String) -> Unit = {},
    viewModel: FollowListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val title = when (uiState.listType) {
        FollowListType.FOLLOWERS        -> "Followers"
        FollowListType.FOLLOWING        -> "Following"
        FollowListType.PENDING_REQUESTS -> "Follow Requests"
        FollowListType.SENT_REQUESTS    -> "Sent Requests"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            // Status bar'ın üstüne taşmaması için — Scaffold değil, manuel padding
            .statusBarsPadding()
    ) {
        // ── Top Bar ──────────────────────────────────────────────────────────
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
                    tint = TextPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(title, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }

        // ── Content ───────────────────────────────────────────────────────────
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoading -> CircularProgressIndicator(
                    color = AccentGreen,
                    modifier = Modifier.align(Alignment.Center)
                )

                uiState.error != null -> Text(
                    uiState.error!!,
                    color = ErrorRed,
                    modifier = Modifier.align(Alignment.Center)
                )

                // null = henüz belli değil (isLoading true olmalı, ama defensive)
                // true = liste gizli
                uiState.isLocked == true -> LockedListState(
                    listType = uiState.listType,
                    modifier = Modifier.align(Alignment.Center)
                )

                uiState.users.isEmpty() -> Text(
                    "No users here",
                    color = TextSecondary,
                    modifier = Modifier.align(Alignment.Center)
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    items(uiState.users, key = { it.userId }) { user ->
                        when (uiState.listType) {
                            FollowListType.FOLLOWERS -> FollowerRow(
                                user = user,
                                isOwnList = viewModel.isOwnList,
                                isCurrentUser = user.userId == uiState.currentUid,
                                onClick = { onNavigateToUserProfile(user.userId) },
                                onRemove = { viewModel.removeFollower(user.userId) }
                            )
                            FollowListType.FOLLOWING -> FollowingRow(
                                user = user,
                                isOwnList = viewModel.isOwnList,
                                onClick = { onNavigateToUserProfile(user.userId) },
                                onUnfollow = { viewModel.unfollow(user.userId) }
                            )
                            FollowListType.PENDING_REQUESTS -> PendingRequestRow(
                                user = user,
                                onClick = { onNavigateToUserProfile(user.userId) },
                                onAccept  = { viewModel.acceptRequest(user.userId) },
                                onDecline = { viewModel.declineRequest(user.userId) }
                            )
                            FollowListType.SENT_REQUESTS -> SentRequestRow(
                                user = user,
                                onClick = { onNavigateToUserProfile(user.userId) },
                                onCancel = { viewModel.cancelSentRequest(user.userId) }
                            )
                        }
                        HorizontalDivider(color = LightGray.copy(alpha = 0.2f), thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

// ── Locked state ──────────────────────────────────────────────────────────────

@Composable
private fun LockedListState(listType: FollowListType, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(64.dp).clip(CircleShape).background(DarkGray),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(R.drawable.ic_smart_lock_ai_outline),
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(32.dp)
            )
        }
        Text(
            "This list is private",
            color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold
        )
        Text(
            when (listType) {
                FollowListType.FOLLOWERS -> "This account's follower list is not visible"
                else -> "This account's following list is not visible"
            },
            color = TextSecondary, fontSize = 13.sp,
            textAlign = TextAlign.Center, lineHeight = 20.sp
        )
    }
}

// ── Row types ─────────────────────────────────────────────────────────────────

@Composable
private fun FollowerRow(
    user: User,
    isOwnList: Boolean,
    isCurrentUser: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserInfo(user = user, modifier = Modifier.weight(1f))
        if (isOwnList && !isCurrentUser) {
            OutlinedButton(
                onClick = onRemove,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("Remove", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun FollowingRow(
    user: User,
    isOwnList: Boolean,
    onClick: () -> Unit,
    onUnfollow: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserInfo(user = user, modifier = Modifier.weight(1f))
        if (isOwnList) {
            Button(
                onClick = onUnfollow,
                colors = ButtonDefaults.buttonColors(containerColor = LightGray),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("Unfollow", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun PendingRequestRow(
    user: User, onClick: () -> Unit,
    onAccept: () -> Unit, onDecline: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserInfo(user = user, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onAccept,
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("Accept", fontSize = 12.sp, color = Black, fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(
                onClick = onDecline,
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("Decline", fontSize = 12.sp, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun SentRequestRow(user: User, onClick: () -> Unit, onCancel: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserInfo(user = user, modifier = Modifier.weight(1f))
        OutlinedButton(
            onClick = onCancel,
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
            modifier = Modifier.height(32.dp)
        ) {
            Text("Cancel", fontSize = 12.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun UserInfo(user: User, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LinkerAvatar(imageUrl = user.profileImageUrl, size = 48.dp, storyState = StoryState.NONE)
        Column {
            Text(user.displayName, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("@${user.username}", color = TextSecondary, fontSize = 13.sp)
        }
    }
}
