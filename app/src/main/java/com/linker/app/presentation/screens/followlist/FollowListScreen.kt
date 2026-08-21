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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val title = when (uiState.listType) {
        FollowListType.FOLLOWERS        -> stringResource(R.string.followers)
        FollowListType.FOLLOWING        -> stringResource(R.string.following)
        FollowListType.PENDING_REQUESTS -> stringResource(R.string.follow_requests)
        FollowListType.SENT_REQUESTS    -> stringResource(R.string.sent_requests)
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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    painterResource(R.drawable.ic_arrow_left_01_outline),
                    contentDescription = stringResource(R.string.action_back),
                    tint = TextPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(title, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }

        // ── Content ───────────────────────────────────────────────────────────
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoading -> CircularProgressIndicator(
                    color = AccentGreen,
                    modifier = Modifier.align(Alignment.Center)
                )

                uiState.error != null -> {
                    val errorMessage = uiState.error
                    if (errorMessage != null) {
                        Text(
                            errorMessage,
                            color = ErrorRed,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }

                // null = henüz belli değil (isLoading true olmalı, ama defensive)
                // true = liste gizli
                uiState.isLocked == true -> LockedListState(
                    listType = uiState.listType,
                    modifier = Modifier.align(Alignment.Center)
                )

                uiState.users.isEmpty() -> Text(
                    stringResource(R.string.no_users_here),
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
            stringResource(R.string.list_is_private),
            color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold
        )
        Text(
            when (listType) {
                FollowListType.FOLLOWERS -> stringResource(R.string.follower_list_not_visible)
                else -> stringResource(R.string.following_list_not_visible)
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
                Text(stringResource(R.string.action_remove), fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
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
                Text(stringResource(R.string.action_unfollow), fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
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
                Text(stringResource(R.string.action_accept), fontSize = 12.sp, color = Black, fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(
                onClick = onDecline,
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(stringResource(R.string.action_decline), fontSize = 12.sp, color = TextSecondary)
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
            Text(stringResource(R.string.action_cancel), fontSize = 12.sp, color = TextSecondary)
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
