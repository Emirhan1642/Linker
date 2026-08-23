package com.linker.app.presentation.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.linker.app.R
import com.linker.app.presentation.theme.*
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linker.app.presentation.components.BottomNavItem
import com.linker.app.presentation.components.LinkerBottomNavigationBar
import com.linker.app.presentation.theme.Black
import com.linker.app.presentation.theme.LightGray
import com.linker.app.presentation.theme.DarkGray
import com.linker.app.presentation.theme.LightPurple
import com.linker.app.presentation.theme.LinkerAngularGradient
import com.linker.app.presentation.theme.TextPrimary
import com.linker.app.presentation.theme.TextSecondary
import com.linker.app.presentation.theme.TextHint
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.foundation.clickable

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.linker.app.presentation.animation.bouncyClick
import com.linker.app.presentation.animation.shimmerEffect
import com.linker.app.presentation.animation.DoubleTapHeartOverlay

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.viewinterop.AndroidView
import com.linker.app.domain.model.LinkType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateBottomNav: (BottomNavItem) -> Unit,
    onNavigateToStoryGrid: () -> Unit = {},
    onNavigateToLinkDetail: (String) -> Unit = {},
    showBottomBar: Boolean = true,
    viewModel: HomeViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentLinks = if (uiState.selectedTab == 1 && uiState.hasFollowingPosts) uiState.followingLinks else uiState.links
    val pagerState = rememberPagerState(pageCount = { if (currentLinks.isNotEmpty()) currentLinks.size else 1 })
    
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            // Optionally log or handle permission result
        }
    )

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionCheckResult = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionCheckResult != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Scaffold(
        containerColor = Black, // Full black for edge to edge videos
        bottomBar = {
            if (showBottomBar) {
                LinkerBottomNavigationBar(
                    currentRoute = "Explore",
                    onNavigate = onNavigateBottomNav,
                    modifier = Modifier.background(Color.Transparent)
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding(), bottom = if (showBottomBar) 0.dp else 0.dp)
        ) {
            // Background / Video Player
            if (currentLinks.isNotEmpty()) {
                VerticalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val link = currentLinks.getOrNull(page)
                    if (link != null) {
                        FeedItemView(
                            link = link,
                            isCurrentPage = pagerState.currentPage == page,
                            onLikeClick = { viewModel.toggleLike(link.linkId) },
                            onSaveClick = { viewModel.toggleSave(link.linkId) },
                            onRelinkClick = { viewModel.toggleRelink(link.linkId) },
                            onCommentClick = { onNavigateToLinkDetail(link.linkId) }
                        )
                    }
                }
            } else if (uiState.isRefreshing) {
                // Shimmer Loading Skeleton
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .shimmerEffect()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.feed_empty),
                        color = TextSecondary,
                        fontSize = 16.sp
                    )
                }
            }

            // Top Pill Bar
            TopPillTabs(
                selectedTab = uiState.selectedTab,
                hasFollowingPosts = uiState.hasFollowingPosts,
                hasActiveStories = uiState.hasActiveStories,
                onTabSelected = { tabIndex ->
                    if (tabIndex == 2) {
                        onNavigateToStoryGrid()
                    } else {
                        viewModel.onTabSelected(tabIndex)
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp) // Status bar padding approx
            )
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPlayerView(
    videoUrl: String,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exoPlayer = remember(videoUrl) {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            val mediaItem = androidx.media3.common.MediaItem.fromUri(videoUrl)
            setMediaItem(mediaItem)
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
            playWhenReady = isPlaying
            prepare()
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            androidx.media3.ui.PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        modifier = modifier
    )
}

@Composable
fun FeedItemView(
    link: com.linker.app.domain.model.Link,
    isCurrentPage: Boolean = true,
    onLikeClick: () -> Unit = {},
    onSaveClick: () -> Unit = {},
    onRelinkClick: () -> Unit = {},
    onCommentClick: () -> Unit = {}
) {
    var showHeartOverlay by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (!link.engagement.isLiked) {
                            onLikeClick()
                        }
                        showHeartOverlay = true
                    }
                )
            }
    ) {
        val primaryMediaUrl = link.primaryMedia.url
        val isVideo = link.linkType == LinkType.VIDEO || link.linkType == LinkType.REEL ||
                primaryMediaUrl.endsWith(".mp4") || primaryMediaUrl.endsWith(".mov")

        if (isVideo && primaryMediaUrl.isNotBlank() && !primaryMediaUrl.startsWith("placeholder://")) {
            VideoPlayerView(
                videoUrl = primaryMediaUrl,
                isPlaying = isCurrentPage,
                modifier = Modifier.fillMaxSize()
            )
        } else if (link.mediaItems.size > 1) {
            val mediaUrls = link.mediaItems.map { it.url }
            val carouselState = rememberPagerState(pageCount = { mediaUrls.size })
            HorizontalPager(
                state = carouselState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val media = mediaUrls.getOrNull(page) ?: ""
                if (media.isNotBlank() && !media.startsWith("placeholder://")) {
                    coil3.compose.AsyncImage(
                        model = media,
                        contentDescription = link.description,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Multi-media Badge
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 96.dp, end = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Black.copy(alpha = 0.65f))
                    .border(1.dp, GlassCardBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_gallery_outline),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "${carouselState.currentPage + 1}/${mediaUrls.size}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else if (primaryMediaUrl.isNotBlank() && !primaryMediaUrl.startsWith("placeholder://")) {
            coil3.compose.AsyncImage(
                model = primaryMediaUrl,
                contentDescription = link.description,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF2D1B69).copy(alpha = 0.8f),
                                Color(0xFF11111F).copy(alpha = 0.95f)
                            )
                        )
                    )
            )
        }

        // Overlay gradients
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.4f),
                            Color.Transparent, 
                            Black.copy(alpha = 0.85f)
                        )
                    )
                )
        )

        // Post caption & author info
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, end = 80.dp, bottom = 100.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkGrayTransparent)
                    .border(1.dp, GlassCardBorder, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Column {
                    Text(
                        text = "@${link.author.username}",
                        color = LinkerPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                    if (!link.description.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = link.description,
                            color = Color.White.copy(alpha = 0.95f),
                            fontSize = 14.sp,
                            maxLines = 3,
                            lineHeight = 19.sp
                        )
                    }
                }
            }
        }

        // Right side Action Buttons
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 110.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ActionButton(
                icon = if (link.engagement.isLiked) R.drawable.ic_heart_bold else R.drawable.ic_heart_outline,
                count = link.engagement.likesCount.toString(),
                tint = if (link.engagement.isLiked) ErrorRed else Color.White,
                onClick = onLikeClick
            )
            ActionButton(
                icon = R.drawable.ic_ai_commentary_outline,
                count = link.engagement.commentsCount.toString(),
                onClick = onCommentClick
            )
            ActionButton(
                icon = if (link.engagement.isRelinked) R.drawable.ic_toy_6_bold else R.drawable.ic_toy_6_outline,
                count = link.engagement.relinksCount.toString(),
                tint = if (link.engagement.isRelinked) LightPurple else Color.White,
                onClick = onRelinkClick
            )
            ActionButton(
                icon = if (link.engagement.isSaved) R.drawable.ic_bookmark_2_bold else R.drawable.ic_bookmark_2_outline,
                count = link.engagement.savesCount.toString(),
                tint = if (link.engagement.isSaved) GradientYellow else Color.White,
                onClick = onSaveClick
            )
        }

        // Floating Double Tap Heart Pop
        DoubleTapHeartOverlay(
            isShowing = showHeartOverlay,
            onDismiss = { showHeartOverlay = false }
        )
    }
}

@Composable
fun TopPillTabs(
    selectedTab: Int,
    hasFollowingPosts: Boolean,
    hasActiveStories: Boolean,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!hasFollowingPosts && !hasActiveStories) return

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(32.dp))
            .background(DarkGrayTransparent)
            .border(1.2.dp, GlassCardBorder, RoundedCornerShape(32.dp))
            .padding(horizontal = 6.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PillTab(stringResource(R.string.feed_tab_all), R.drawable.ic_hashtag_down_outline, R.drawable.ic_hashtag_down_bold, isSelected = selectedTab == 0) { onTabSelected(0) }
        if (hasFollowingPosts) {
            PillTab(stringResource(R.string.feed_tab_followed), R.drawable.ic_ai_users_outline, R.drawable.ic_ai_users_bold, isSelected = selectedTab == 1) { onTabSelected(1) }
        }
        if (hasActiveStories) {
            PillTab(stringResource(R.string.feed_tab_stories), R.drawable.ic_story_outline, R.drawable.ic_story_bold, isSelected = selectedTab == 2) { onTabSelected(2) }
        }
    }
}

@Composable
fun PillTab(
    title: String,
    @DrawableRes icon: Int,
    @DrawableRes selectedIcon: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .then(
                if (isSelected) Modifier.background(Brush.horizontalGradient(LinkerBrandGradient))
                else Modifier.background(Color.Transparent)
            )
            .bouncyClick { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                painter = painterResource(id = if (isSelected) selectedIcon else icon),
                contentDescription = title,
                tint = if (isSelected) Color.White else TextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = title,
                color = if (isSelected) Color.White else TextSecondary,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
fun ActionButton(
    @DrawableRes icon: Int,
    count: String?,
    tint: Color = Color.White,
    onClick: () -> Unit = {}
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.bouncyClick { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(DarkGrayTransparent)
                .border(1.2.dp, GlassCardBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
        }
        if (count != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = count,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
