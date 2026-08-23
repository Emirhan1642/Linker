package com.linker.app.presentation.screens.userprofile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.linker.app.R
import com.linker.app.core.util.FormatUtil.formatStat
import com.linker.app.domain.model.FollowState
import com.linker.app.domain.model.Link
import com.linker.app.domain.model.User
import com.linker.app.domain.model.followState
import com.linker.app.presentation.animation.bouncyClick
import com.linker.app.presentation.components.*
import com.linker.app.presentation.screens.profile.ProfilePostItem
import com.linker.app.presentation.screens.profile.StatGlassCard
import com.linker.app.presentation.theme.*

@Composable
fun UserProfileScreen(
    onNavigateBack: () -> Unit,
    onNavigateToChat: (String) -> Unit = {},
    onNavigateToFollowers: (String) -> Unit = {},
    onNavigateToFollowing: (String) -> Unit = {},
    viewModel: UserProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showFullScreenAvatar by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is UserProfileEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message.asString(context))
                is UserProfileEffect.NavigateToChat -> {}
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBackgroundGradient)
    ) {
        Scaffold(
            modifier = if (showFullScreenAvatar) Modifier.blur(16.dp) else Modifier,
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Top Ambient Light Orbs
                AmbientGlow(
                    glowColor = GradientBlue,
                    size = 260.dp,
                    alpha = 0.18f,
                    modifier = Modifier.align(Alignment.TopCenter).offset(y = (-40).dp)
                )

                when {
                    uiState.isLoading -> CircularProgressIndicator(
                        color = GradientBlue,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    uiState.error != null -> {
                        val errorMessage = uiState.error?.asString()
                        if (errorMessage != null) {
                            ErrorState(errorMessage, onNavigateBack)
                        }
                    }
                    else -> LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalItemSpacing = 12.dp
                    ) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            UserProfileHeader(
                                user = uiState.user,
                                isActionLoading = uiState.isActionLoading,
                                selectedTab = uiState.selectedTab,
                                onNavigateBack = onNavigateBack,
                                onTabSelected = viewModel::onTabSelected,
                                onFollowAction = viewModel::onFollowAction,
                                onMessage = { uiState.user?.let { onNavigateToChat(it.userId) } },
                                onFollowersClick = { uiState.user?.let { onNavigateToFollowers(it.userId) } },
                                onFollowingClick = { uiState.user?.let { onNavigateToFollowing(it.userId) } },
                                onAvatarClick = { showFullScreenAvatar = true }
                            )
                        }

                        if (uiState.isContentLocked) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                PrivateAccountLock(user = uiState.user)
                            }
                        } else if (uiState.posts.isEmpty()) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(200.dp),
                                    contentAlignment = Alignment.Center
                                ) { Text(stringResource(R.string.user_profile_no_posts), color = TextSecondary) }
                            }
                        } else {
                            items(uiState.posts, key = { it.linkId }) { link -> ProfilePostItem(post = link) }
                        }

                        item(span = StaggeredGridItemSpan.FullLine) {
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }
            }
        }

        // Full Screen Avatar Overlay
        AnimatedVisibility(
            visible = showFullScreenAvatar,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f))
                    .clickable { showFullScreenAvatar = false },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LinkerAvatar(
                        imageUrl = uiState.user?.profileImageUrl,
                        size = 200.dp,
                        storyState = StoryState.NONE,
                        onClick = {}
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        uiState.user?.displayName ?: "User",
                        color = TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text("@${uiState.user?.username ?: "username"}", color = GradientBlue, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun UserProfileHeader(
    user: User?,
    isActionLoading: Boolean,
    selectedTab: Int,
    onNavigateBack: () -> Unit,
    onTabSelected: (Int) -> Unit,
    onFollowAction: () -> Unit,
    onMessage: () -> Unit,
    onFollowersClick: () -> Unit,
    onFollowingClick: () -> Unit,
    onAvatarClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Navigation Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassIconButton(
                iconRes = R.drawable.ic_arrow_left_01_outline,
                onClick = onNavigateBack,
                size = 44.dp
            )
            GlassIconButton(
                iconRes = R.drawable.ic_more_square_outline,
                onClick = { /* more */ },
                size = 44.dp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Avatar
        LinkerAvatar(
            imageUrl = user?.profileImageUrl,
            size = 96.dp,
            storyState = StoryState.NONE,
            onClick = onAvatarClick
        )

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            user?.displayName ?: "User",
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            "@${user?.username ?: ""}",
            color = GradientBlue,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )

        if (!user?.bio.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            GlassBox(
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Text(
                    user?.bio ?: "",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Stats Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatGlassCard(
                value = formatStat(user?.metrics?.followersCount ?: 0),
                label = stringResource(R.string.followers),
                modifier = Modifier.weight(1f),
                onClick = onFollowersClick
            )
            StatGlassCard(
                value = formatStat(user?.metrics?.followingCount ?: 0),
                label = stringResource(R.string.following),
                modifier = Modifier.weight(1f),
                onClick = onFollowingClick
            )
            StatGlassCard(
                value = formatStat(user?.metrics?.likesCount ?: 0),
                label = stringResource(R.string.profile_likes),
                modifier = Modifier.weight(1f),
                onClick = {}
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Action Buttons (Follow / Message)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val followState = user?.followState() ?: FollowState.NOT_FOLLOWING
            val isFollowing = followState == FollowState.FOLLOWING || followState == FollowState.REQUEST_SENT
            val btnLabel = when (followState) {
                FollowState.FOLLOWING -> stringResource(R.string.follow_status_following)
                FollowState.REQUEST_SENT -> stringResource(R.string.user_profile_requested)
                else -> stringResource(R.string.follow_status_follow)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(23.dp))
                    .then(
                        if (isFollowing) Modifier.background(DarkGrayTransparent)
                        else Modifier.background(Brush.horizontalGradient(NeonBlueGreenGradient))
                    )
                    .border(
                        1.dp,
                        if (isFollowing) GlassCardBorder else Color.Transparent,
                        RoundedCornerShape(23.dp)
                    )
                    .bouncyClick(enabled = !isActionLoading, onClick = onFollowAction),
                contentAlignment = Alignment.Center
            ) {
                if (isActionLoading) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Text(btnLabel, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(23.dp))
                    .background(DarkGrayTransparent)
                    .border(1.dp, GlassCardBorder, RoundedCornerShape(23.dp))
                    .bouncyClick(onClick = onMessage),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(R.drawable.ic_ai_send_message_outline),
                        null,
                        tint = GradientBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.user_profile_message),
                        fontSize = 14.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tabs
        if (user?.privacy?.isPrivate == false || user?.relationship?.isFollowing == true) {
            GlassBox(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            ) {
                Row(modifier = Modifier.padding(4.dp)) {
                    val isFeed = selectedTab == 0
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .then(
                                if (isFeed) Modifier.background(Brush.horizontalGradient(listOf(GradientPurple, GradientBlue)))
                                else Modifier.background(Color.Transparent)
                            )
                            .bouncyClick { onTabSelected(0) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.user_profile_posts),
                            color = if (isFeed) Color.White else TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (isFeed) FontWeight.Bold else FontWeight.Medium
                        )
                    }

                    val isRelinks = selectedTab == 1
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .then(
                                if (isRelinks) Modifier.background(Brush.horizontalGradient(listOf(GradientPurple, GradientBlue)))
                                else Modifier.background(Color.Transparent)
                            )
                            .bouncyClick { onTabSelected(1) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.user_profile_relinks),
                            color = if (isRelinks) Color.White else TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (isRelinks) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun PrivateAccountLock(user: User?) {
    GlassBox(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(GradientPurple.copy(alpha = 0.2f))
                    .border(1.dp, GradientPurple.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(R.drawable.ic_smart_lock_ai_outline),
                    null,
                    tint = GradientPurple,
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(
                stringResource(R.string.user_profile_account_private),
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(R.string.user_profile_follow_private_desc, user?.username ?: ""),
                color = TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp
            )
        }
    }
}

@Composable
private fun ErrorState(error: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(error, color = ErrorRed)
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onBack) { Text(stringResource(R.string.action_back), color = GradientBlue) }
    }
}
