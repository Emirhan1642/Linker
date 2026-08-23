package com.linker.app.presentation.screens.profile

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linker.app.R
import com.linker.app.core.util.FormatUtil.formatStat
import com.linker.app.domain.model.Link
import com.linker.app.domain.model.User
import com.linker.app.presentation.animation.bouncyClick
import com.linker.app.presentation.components.*
import com.linker.app.presentation.theme.*

@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateBottomNav: (BottomNavItem) -> Unit,
    onNavigateToStory: () -> Unit = {},
    onNavigateToFollowers: (String) -> Unit = {},
    onNavigateToFollowing: (String) -> Unit = {},
    onNavigateToCreateLink: () -> Unit = { onNavigateBottomNav(BottomNavItem.Add) },
    showBottomBar: Boolean = true,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(0) }
    var showFullScreenAvatar by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.user?.userId) {
        selectedTab = 0
        showFullScreenAvatar = false
    }

    val displayedLinks = if (selectedTab == 0) uiState.myPosts else uiState.relinkedPosts

    val handleAvatarClick = {
        if (uiState.storyState != StoryState.NONE) onNavigateToStory()
        else showFullScreenAvatar = true
    }

    val context = androidx.compose.ui.platform.LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBackgroundGradient)
    ) {
        Scaffold(
            modifier = if (showFullScreenAvatar) Modifier.blur(16.dp) else Modifier,
            containerColor = Color.Transparent,
            bottomBar = {
                if (showBottomBar) {
                    LinkerBottomNavigationBar(
                        currentRoute = "Profile",
                        onNavigate = onNavigateBottomNav,
                        modifier = Modifier.background(Color.Transparent)
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalItemSpacing = 12.dp
                ) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        ProfileHeader(
                            user = uiState.user,
                            storyState = uiState.storyState,
                            onNavigateBack = onNavigateBack,
                            onNavigateToSettings = onNavigateToSettings,
                            onEditProfileClick = onNavigateToSettings,
                            onShareProfileClick = {
                                val username = uiState.user?.username ?: "user"
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT, context.getString(R.string.profile_share_text, username))
                                }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, context.getString(R.string.profile_share_title)))
                            },
                            selectedTab = selectedTab,
                            onTabSelected = { selectedTab = it },
                            onAvatarClick = handleAvatarClick,
                            onAvatarLongClick = { showFullScreenAvatar = true },
                            onFollowersClick = {
                                val uid = viewModel.currentUid ?: return@ProfileHeader
                                onNavigateToFollowers(uid)
                            },
                            onFollowingClick = {
                                val uid = viewModel.currentUid ?: return@ProfileHeader
                                onNavigateToFollowing(uid)
                            }
                        )
                    }

                    if (displayedLinks.isEmpty()) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 36.dp, horizontal = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(LinkerPrimary.copy(alpha = 0.15f))
                                        .border(1.dp, LinkerPrimary.copy(alpha = 0.4f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(if (selectedTab == 0) R.drawable.ic_link_3_outline else R.drawable.ic_play_add_outline),
                                        contentDescription = null,
                                        tint = LinkerPrimary,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                Text(
                                    text = if (selectedTab == 0) "Henüz hiçbir link paylaşmadın" else "Henüz bir link yeniden paylaşmadın",
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (selectedTab == 0)
                                        "İlk linkini paylaşarak takipçilerinle anılarını ve ilginç içerikleri buluşturmaya başla!"
                                    else
                                        "Beğendiğin gönderileri yeniden paylaşarak profilinde topla.",
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    lineHeight = 18.sp
                                )
                                if (selectedTab == 0) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(22.dp))
                                            .background(Brush.horizontalGradient(LinkerBrandGradient))
                                            .bouncyClick { onNavigateToCreateLink() }
                                            .padding(horizontal = 24.dp, vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_ai_add_outline),
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = "İlk Linkini Paylaş",
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        items(displayedLinks, key = { it.linkId }) { link ->
                            ProfilePostItem(post = link)
                        }
                    }
                    item(span = StaggeredGridItemSpan.FullLine) { Spacer(modifier = Modifier.height(100.dp)) }
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
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AvatarAction(R.drawable.ic_export_circle_01_outline, stringResource(R.string.profile_action_share)) { showFullScreenAvatar = false }
                        AvatarAction(R.drawable.ic_close_circle_outline, stringResource(R.string.profile_action_block)) { showFullScreenAvatar = false }
                    }
                }
            }
        }
    }
}

@Composable
private fun AvatarAction(iconRes: Int, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.bouncyClick(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(DarkGrayTransparent)
                .border(1.dp, GlassCardBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(iconRes), label, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Text(
            label,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
fun ProfileHeader(
    user: User?,
    storyState: StoryState,
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onAvatarClick: () -> Unit,
    onAvatarLongClick: () -> Unit,
    onEditProfileClick: () -> Unit = {},
    onShareProfileClick: () -> Unit = {},
    onFollowersClick: () -> Unit = {},
    onFollowingClick: () -> Unit = {}
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
                iconRes = R.drawable.ic_setting_2_outline,
                onClick = onNavigateToSettings,
                size = 44.dp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Center Avatar
        LinkerAvatar(
            imageUrl = user?.profileImageUrl,
            size = 96.dp,
            storyState = storyState,
            onClick = onAvatarClick,
            onLongClick = onAvatarLongClick
        )

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            user?.displayName ?: "User",
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            "@${user?.username ?: "username"}",
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

        // 3-Stat Glass Cards
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

        // Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(23.dp))
                    .background(Brush.horizontalGradient(LinkerBrandGradient))
                    .bouncyClick(onClick = onEditProfileClick),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(R.drawable.ic_user_edit_outline),
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.profile_action_edit),
                        fontSize = 14.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(23.dp))
                    .background(DarkGrayTransparent)
                    .border(1.dp, GlassCardBorder, RoundedCornerShape(23.dp))
                    .bouncyClick(onClick = onShareProfileClick),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(R.drawable.ic_export_circle_01_outline),
                        null,
                        tint = LinkerPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.profile_action_share),
                        fontSize = 14.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tab Selector (Feed / Shorts)
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
                            if (isFeed) Modifier.background(Brush.horizontalGradient(LinkerBrandGradient))
                            else Modifier.background(Color.Transparent)
                        )
                        .bouncyClick { onTabSelected(0) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painterResource(R.drawable.ic_gallery_outline),
                            stringResource(R.string.profile_tab_feed),
                            tint = if (isFeed) Color.White else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.profile_tab_feed),
                            color = if (isFeed) Color.White else TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (isFeed) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }

                val isShorts = selectedTab == 1
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .then(
                            if (isShorts) Modifier.background(Brush.horizontalGradient(LinkerBrandGradient))
                            else Modifier.background(Color.Transparent)
                        )
                        .bouncyClick { onTabSelected(1) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painterResource(R.drawable.ic_play_add_outline),
                            stringResource(R.string.profile_tab_shorts),
                            tint = if (isShorts) Color.White else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.profile_tab_shorts),
                            color = if (isShorts) Color.White else TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (isShorts) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
fun StatGlassCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .bouncyClick(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ProfilePostItem(post: Link) {
    val aspectRatio = post.primaryMedia.aspectRatio ?: 1f
    val rawUrl = post.primaryMedia.url
    val cleanUrl = com.linker.app.core.util.MediaUtils.sanitizeMediaUrl(rawUrl)

    GlassBox(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .bouncyClick {}
    ) {
        if (cleanUrl.isNotBlank()) {
            AsyncImage(
                model = cleanUrl,
                contentDescription = post.description,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                        startY = 100f
                    )
                )
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(DarkGrayTransparent)
                    .border(1.dp, GlassCardBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(R.drawable.ic_profile_outline),
                    null,
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkGrayTransparent)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Icon(
                    painterResource(R.drawable.ic_heart_bold),
                    null,
                    tint = ErrorRed,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    formatStat(post.engagement.likesCount),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
        ) {
            Text(
                post.description ?: "",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
