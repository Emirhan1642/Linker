package com.linker.app.presentation.screens.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.linker.app.core.util.Result
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import com.linker.app.presentation.components.LinkerAvatar
import com.linker.app.presentation.components.StoryState
import com.linker.app.presentation.components.ZoomableMediaBox
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linker.app.R
import com.linker.app.domain.model.Link
import com.linker.app.domain.model.LinkType
import com.linker.app.presentation.animation.DoubleTapHeartOverlay
import com.linker.app.presentation.animation.bouncyClick
import com.linker.app.presentation.animation.shimmerEffect
import com.linker.app.presentation.components.BottomNavItem
import com.linker.app.presentation.components.FeedPostOptionsBottomSheet
import com.linker.app.presentation.components.LinkerBottomNavigationBar
import com.linker.app.presentation.screens.link.CommentSheet
import com.linker.app.presentation.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateBottomNav: (BottomNavItem) -> Unit,
    onNavigateToStoryGrid: () -> Unit = {},
    onNavigateToLinkDetail: (String) -> Unit = {},
    onNavigateToProfile: (userId: String) -> Unit = {},
    onNavigateToSearch: (query: String, tab: com.linker.app.presentation.screens.search.SearchTab) -> Unit = { _, _ -> },
    showBottomBar: Boolean = true,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentLinks = if (uiState.selectedTab == 1 && uiState.hasFollowingPosts) uiState.followingLinks else uiState.links
    val pagerState = rememberPagerState(pageCount = { if (currentLinks.isNotEmpty()) currentLinks.size else 1 })
    
    val context = LocalContext.current
    var activeCommentLinkId by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { _ -> }
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

    var totalHorizontalDrag by remember { mutableFloatStateOf(0f) }
    var isAnyItemZooming by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Black,
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar && !isAnyItemZooming,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
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
                .padding(top = paddingValues.calculateTopPadding(), bottom = 0.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { totalHorizontalDrag = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            totalHorizontalDrag += dragAmount
                            if (totalHorizontalDrag < -50f) {
                                change.consume()
                                totalHorizontalDrag = 0f
                                onNavigateToStoryGrid()
                            }
                        }
                    )
                }
        ) {
            // Feed Items Pager
            if (currentLinks.isNotEmpty()) {
                VerticalPager(
                    state = pagerState,
                    userScrollEnabled = !isAnyItemZooming,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val link = currentLinks.getOrNull(page)
                    if (link != null) {
                        FeedItemView(
                            link = link,
                            isCurrentPage = pagerState.currentPage == page,
                            onZoomStateChanged = { isAnyItemZooming = it },
                            onNavigateToProfile = onNavigateToProfile,
                            onNavigateToSearch = onNavigateToSearch,
                            onLikeClick = { viewModel.toggleLike(link.linkId) },
                            onSaveClick = { viewModel.toggleSave(link.linkId) },
                            onRelinkClick = { viewModel.toggleRelink(link.linkId) },
                            onCommentClick = { activeCommentLinkId = link.linkId },
                            onNotInterested = { viewModel.hidePost(link.linkId) },
                            onHideUser = { viewModel.hideUserPosts(link.author.userId) },
                            viewModel = viewModel
                        )
                    }
                }
            } else if (uiState.isLoading || uiState.isRefreshing) {
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

            // Top Pill Bar - Automatically hidden when zooming
            AnimatedVisibility(
                visible = !isAnyItemZooming,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp)
            ) {
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
                    }
                )
            }

            // Comment BottomSheet
            if (activeCommentLinkId != null) {
                CommentSheet(
                    targetId = activeCommentLinkId!!,
                    onDismiss = { activeCommentLinkId = null }
                )
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPlayerView(
    videoUrl: String,
    isPlaying: Boolean,
    isManuallyPaused: Boolean = false,
    playbackSpeed: Float = 1.0f,
    seekToMs: Long? = null,
    onProgressUpdate: (currentMs: Long, totalDurationMs: Long) -> Unit = { _, _ -> },
    resizeMode: Int = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT,
    modifier: Modifier = Modifier
) {
    val cleanUrl = com.linker.app.core.util.MediaUtils.sanitizeMediaUrl(videoUrl)
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isAppInForeground by remember { mutableStateOf(true) }

    if (cleanUrl.isBlank()) {
        Box(modifier = modifier.background(Color.Black))
        return
    }

    val exoPlayer = remember(cleanUrl) {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            val uri = android.net.Uri.parse(cleanUrl)
            val mediaItem = androidx.media3.common.MediaItem.fromUri(uri)
            setMediaItem(mediaItem)
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
            playWhenReady = isPlaying && !isManuallyPaused
            prepare()
        }
    }

    // Lifecycle observer to stop audio when app goes to background
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    isAppInForeground = false
                    exoPlayer.pause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    isAppInForeground = true
                    if (isPlaying && !isManuallyPaused) {
                        exoPlayer.play()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    // Playback state control
    LaunchedEffect(isPlaying, isManuallyPaused, isAppInForeground) {
        if (isPlaying && !isManuallyPaused && isAppInForeground) {
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
    }

    // Playback speed control (e.g. 2x speed)
    LaunchedEffect(playbackSpeed) {
        exoPlayer.setPlaybackSpeed(playbackSpeed)
    }

    // Seek control
    LaunchedEffect(seekToMs) {
        if (seekToMs != null && seekToMs >= 0) {
            exoPlayer.seekTo(seekToMs)
        }
    }

    // Progress ticker
    LaunchedEffect(exoPlayer, isPlaying, isAppInForeground) {
        while (isActive) {
            if (isPlaying && isAppInForeground) {
                val current = exoPlayer.currentPosition.coerceAtLeast(0L)
                val duration = exoPlayer.duration.coerceAtLeast(0L)
                onProgressUpdate(current, duration)
            }
            delay(100)
        }
    }

    AndroidView(
        factory = { ctx ->
            androidx.media3.ui.PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
                this.resizeMode = resizeMode
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        update = { playerView ->
            playerView.resizeMode = resizeMode
        },
        modifier = modifier
    )
}

@Composable
fun FeedItemView(
    link: Link,
    isCurrentPage: Boolean = true,
    onZoomStateChanged: (Boolean) -> Unit = {},
    onNavigateToProfile: (String) -> Unit = {},
    onNavigateToSearch: (String, com.linker.app.presentation.screens.search.SearchTab) -> Unit = { _, _ -> },
    onLikeClick: () -> Unit = {},
    onSaveClick: () -> Unit = {},
    onRelinkClick: () -> Unit = {},
    onCommentClick: () -> Unit = {},
    onNotInterested: () -> Unit = {},
    onHideUser: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    var showHeartOverlay by remember { mutableStateOf(false) }
    var showOptionsBottomSheet by remember { mutableStateOf(false) }

    // Video playback & control states
    var isManuallyPaused by remember { mutableStateOf(false) }
    var isFastForwarding by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isScrubbing by remember { mutableStateOf(false) }
    var seekTargetMs by remember { mutableStateOf<Long?>(null) }
    var resizeMode by remember { mutableIntStateOf(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    val rawPrimaryUrl = link.primaryMedia.url
    val cleanPrimaryUrl = com.linker.app.core.util.MediaUtils.sanitizeMediaUrl(rawPrimaryUrl)
    val hasMedia = cleanPrimaryUrl.isNotBlank() && !cleanPrimaryUrl.startsWith("placeholder://text_only") &&
            link.mediaItems.any { it.url.isNotBlank() && !it.url.startsWith("placeholder://text_only") }

    if (!hasMedia) {
        TextFeedItemView(
            link = link,
            onNavigateToProfile = onNavigateToProfile,
            onNavigateToSearch = onNavigateToSearch,
            onLikeClick = onLikeClick,
            onCommentClick = onCommentClick,
            onRelinkClick = onRelinkClick,
            onSaveClick = onSaveClick,
            onNotInterested = onNotInterested,
            onHideUser = onHideUser,
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    val isVideo = link.linkType == LinkType.VIDEO || link.linkType == LinkType.REEL ||
            com.linker.app.core.util.MediaUtils.isVideoUrl(cleanPrimaryUrl)

    var isLocalZooming by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
    ) {
        // Dedicated Media Container with Focal-Point ZoomableMediaBox & Tap Gestures
        ZoomableMediaBox(
            modifier = Modifier.fillMaxSize(),
            onZoomStateChanged = { zoomed ->
                isLocalZooming = zoomed
                onZoomStateChanged(zoomed)
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (!link.engagement.isLiked) {
                                    onLikeClick()
                                }
                                showHeartOverlay = true
                            },
                            onTap = {
                                if (isVideo) {
                                    isManuallyPaused = !isManuallyPaused
                                }
                            },
                            onLongPress = {
                                showOptionsBottomSheet = true
                            }
                        )
                    }
            ) {
                val mediaUrls = link.mediaItems.map { it.url }

                if (isVideo) {
                    VideoPlayerView(
                        videoUrl = cleanPrimaryUrl,
                        isPlaying = isCurrentPage,
                        isManuallyPaused = isManuallyPaused,
                        playbackSpeed = if (isFastForwarding) 2.0f else 1.0f,
                        seekToMs = seekTargetMs,
                        resizeMode = resizeMode,
                        onProgressUpdate = { position, duration ->
                            currentPositionMs = position
                            durationMs = duration
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (mediaUrls.size > 1) {
                    val carouselState = rememberPagerState(pageCount = { mediaUrls.size })
                    Box(modifier = Modifier.fillMaxSize()) {
                        HorizontalPager(
                            state = carouselState,
                            modifier = Modifier.fillMaxSize()
                        ) { pageIdx ->
                            val media = mediaUrls[pageIdx]
                            val isPageVideo = com.linker.app.core.util.MediaUtils.isVideoUrl(media)
                            if (isPageVideo) {
                                VideoPlayerView(
                                    videoUrl = media,
                                    isPlaying = isCurrentPage && carouselState.currentPage == pageIdx && !isManuallyPaused,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    coil3.compose.AsyncImage(
                                        model = media,
                                        contentDescription = null,
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .drawWithContent {
                                                drawContent()
                                                drawRect(Color.Black.copy(alpha = 0.55f))
                                            }
                                            .blur(30.dp)
                                    )
                                    coil3.compose.AsyncImage(
                                        model = media,
                                        contentDescription = link.description,
                                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }

                        // Multi-media Badge
                        AnimatedVisibility(
                            visible = !isLocalZooming,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 96.dp, end = 16.dp)
                        ) {
                            Box(
                                modifier = Modifier
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
                        }
                    }
                } else if (cleanPrimaryUrl.isNotBlank()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        coil3.compose.AsyncImage(
                            model = cleanPrimaryUrl,
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .drawWithContent {
                                    drawContent()
                                    drawRect(Color.Black.copy(alpha = 0.55f))
                                }
                                .blur(30.dp)
                        )
                        coil3.compose.AsyncImage(
                            model = cleanPrimaryUrl,
                            contentDescription = link.description,
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
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
            }
        }

        // Overlay elements - ALL automatically hidden while zooming
        AnimatedVisibility(
            visible = !isLocalZooming,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Overlay gradients
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.35f),
                                    Color.Transparent, 
                                    Black.copy(alpha = 0.85f)
                                )
                            )
                        )
                )

                // Right side 2X Speed hold area
                if (isVideo) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.35f)
                            .align(Alignment.CenterEnd)
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    try {
                                        withTimeout(350L) {
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                if (!event.changes.any { it.pressed }) break
                                            }
                                        }
                                    } catch (_: Exception) {
                                        isFastForwarding = true
                                        do {
                                            val event = awaitPointerEvent()
                                        } while (event.changes.any { it.pressed })
                                        isFastForwarding = false
                                    }
                                }
                            }
                    )
                }

                // Center Paused Icon HUD
                if (isVideo && isManuallyPaused) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(Black.copy(alpha = 0.65f))
                            .border(1.5.dp, GlassCardBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Oynat",
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }

                // Top 2X Speed Floating Indicator HUD
                if (isFastForwarding) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 110.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Black.copy(alpha = 0.8f))
                            .border(1.2.dp, Brush.horizontalGradient(LinkerBrandGradient), RoundedCornerShape(20.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("⚡", fontSize = 14.sp)
                            Text(
                                text = "2X Hız",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Post caption & author info (Modern transparent floating overlay)
                var isDescriptionExpanded by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 84.dp, bottom = 78.dp)
                ) {
                    // 1. Author Row: Avatar + Username + Verified + Location + Date
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .clickable { onNavigateToProfile(link.author.userId) }
                            .padding(vertical = 4.dp)
                    ) {
                        LinkerAvatar(
                            imageUrl = link.author.profileImageUrl,
                            size = 38.dp,
                            storyState = StoryState.NONE
                        )

                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "@${link.author.username}",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                if (link.author.isVerified) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Doğrulanmış",
                                        tint = GradientBlue,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                if (!link.location.isNullOrBlank()) {
                                    Text(
                                        text = "•",
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 12.sp
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        modifier = Modifier
                                            .clickable { onNavigateToSearch(link.location, com.linker.app.presentation.screens.search.SearchTab.LINKS) }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LocationOn,
                                            contentDescription = "Konum",
                                            tint = GradientBlue,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = link.location,
                                            color = Color.White.copy(alpha = 0.85f),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            val minutesAgo = ((System.currentTimeMillis() - link.createdAt) / 60_000).toInt()
                            val timeText = when {
                                minutesAgo < 1 -> "Az önce"
                                minutesAgo < 60 -> "$minutesAgo dk önce"
                                minutesAgo < 1440 -> "${minutesAgo / 60} sa önce"
                                else -> "${minutesAgo / 1440} g önce"
                            }
                            Text(
                                text = timeText,
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                        }
                    }

                    // 2. Description (Expandable, clickable tags/mentions)
                    if (!link.description.isNullOrBlank()) {
                        val coroutineScope = rememberCoroutineScope()
                        Spacer(modifier = Modifier.height(4.dp))
                        com.linker.app.presentation.components.LinkerFormattedText(
                            text = link.description,
                            color = Color.White.copy(alpha = 0.95f),
                            fontSize = 14.sp,
                            lineHeight = 19.sp,
                            maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 3,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            onHashtagClick = { tag ->
                                val clean = tag.removePrefix("#").trim()
                                onNavigateToSearch("#$clean", com.linker.app.presentation.screens.search.SearchTab.LINKS)
                            },
                            onMentionClick = { mention ->
                                val clean = mention.removePrefix("@").trim()
                                coroutineScope.launch {
                                    when (val res = viewModel.getUserByUsername(clean)) {
                                        is Result.Success -> onNavigateToProfile(res.data.userId)
                                        else -> onNavigateToSearch(clean, com.linker.app.presentation.screens.search.SearchTab.USERS)
                                    }
                                }
                            },
                            onClick = { isDescriptionExpanded = !isDescriptionExpanded }
                        )
                    }

                    // 3. AI Info Chip
                    if (link.isAiGenerated) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.5f))
                                .border(0.8.dp, LinkerPrimary.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "✨ Yapay Zeka",
                                color = LinkerPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Right side Action Buttons
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 100.dp),
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

                // Interactive Video Timeline / Progress Bar
                if (isVideo && durationMs > 0L) {
                    val progress = (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(bottom = 2.dp)
                    ) {
                        if (isScrubbing) {
                            Text(
                                text = "${formatDuration(currentPositionMs)} / ${formatDuration(durationMs)}",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Black.copy(alpha = 0.8f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (isScrubbing) 8.dp else 3.dp)
                                .background(Color.White.copy(alpha = 0.25f))
                                .pointerInput(durationMs) {
                                    detectDragGestures(
                                        onDragStart = { startOffset ->
                                            isScrubbing = true
                                            val fraction = (startOffset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                            seekTargetMs = (fraction * durationMs).toLong()
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            val fraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                            seekTargetMs = (fraction * durationMs).toLong()
                                        },
                                        onDragEnd = {
                                            isScrubbing = false
                                        },
                                        onDragCancel = {
                                            isScrubbing = false
                                        }
                                    )
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .fillMaxHeight()
                                    .background(Brush.horizontalGradient(LinkerBrandGradient))
                            )
                        }
                    }
                }
            }
        }

        // Floating Double Tap Heart Pop
        DoubleTapHeartOverlay(
            isShowing = showHeartOverlay,
            onDismiss = { showHeartOverlay = false }
        )

        // Post Options Bottom Sheet
        if (showOptionsBottomSheet) {
            FeedPostOptionsBottomSheet(
                link = link,
                onDismiss = { showOptionsBottomSheet = false },
                onNotInterested = onNotInterested,
                onSaveToggle = onSaveClick,
                onReport = {},
                onHideUser = onHideUser
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
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

@Composable
fun TextFeedItemView(
    link: Link,
    onNavigateToProfile: (String) -> Unit = {},
    onNavigateToSearch: (String, com.linker.app.presentation.screens.search.SearchTab) -> Unit = { _, _ -> },
    onLikeClick: () -> Unit,
    onCommentClick: () -> Unit,
    onRelinkClick: () -> Unit,
    onSaveClick: () -> Unit,
    onNotInterested: () -> Unit,
    onHideUser: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    var showHeartOverlay by remember { mutableStateOf(false) }
    var showOptionsBottomSheet by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF160E29),
                        Color(0xFF0F0A1C),
                        Color(0xFF06040A)
                    )
                )
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (!link.engagement.isLiked) {
                            onLikeClick()
                        }
                        showHeartOverlay = true
                    },
                    onLongPress = {
                        showOptionsBottomSheet = true
                    }
                )
            }
    ) {
        // Ambient background glow
        Box(
            modifier = Modifier
                .size(340.dp)
                .align(Alignment.Center)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            LinkerPrimary.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Main Centered Text Card
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF141222).copy(alpha = 0.9f))
                    .border(
                        1.2.dp,
                        Brush.linearGradient(
                            listOf(
                                GlassCardBorder,
                                LinkerPrimary.copy(alpha = 0.35f)
                            )
                        ),
                        RoundedCornerShape(24.dp)
                    )
                    .padding(20.dp)
            ) {
                Column {
                    // Author Header Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.clickable { onNavigateToProfile(link.author.userId) }
                    ) {
                        LinkerAvatar(
                            imageUrl = link.author.profileImageUrl,
                            size = 46.dp,
                            storyState = StoryState.NONE
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = link.author.displayName.ifBlank { link.author.username },
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    maxLines = 1
                                )
                                if (link.author.isVerified) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Doğrulanmış",
                                        tint = GradientBlue,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                            Text(
                                text = "@${link.author.username}",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        }

                        // AI Badge if enabled
                        if (link.isAiGenerated) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(LinkerPrimary.copy(alpha = 0.2f))
                                    .border(1.dp, LinkerPrimary.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "✨ Yapay Zeka",
                                    color = LinkerPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Location Chip if exists
                    if (!link.location.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(GradientBlue.copy(alpha = 0.12f))
                                .clickable { onNavigateToSearch(link.location, com.linker.app.presentation.screens.search.SearchTab.LINKS) }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = "Konum",
                                tint = GradientBlue,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = link.location,
                                color = GradientBlue,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Large formatted text content (Twitter / Threads style)
                    val textLen = link.description?.length ?: 0
                    val textFontSize = when {
                        textLen < 80 -> 22.sp
                        textLen < 200 -> 18.sp
                        else -> 15.sp
                    }
                    val textLineHeight = when {
                        textLen < 80 -> 30.sp
                        textLen < 200 -> 26.sp
                        else -> 22.sp
                    }

                    com.linker.app.presentation.components.LinkerFormattedText(
                        text = link.description ?: "",
                        color = Color.White.copy(alpha = 0.95f),
                        fontSize = textFontSize,
                        lineHeight = textLineHeight,
                        maxLines = 14,
                        onHashtagClick = { tag ->
                            val clean = tag.removePrefix("#").trim()
                            onNavigateToSearch("#$clean", com.linker.app.presentation.screens.search.SearchTab.LINKS)
                        },
                        onMentionClick = { mention ->
                            val clean = mention.removePrefix("@").trim()
                            coroutineScope.launch {
                                when (val res = viewModel.getUserByUsername(clean)) {
                                    is Result.Success -> onNavigateToProfile(res.data.userId)
                                    else -> onNavigateToSearch(clean, com.linker.app.presentation.screens.search.SearchTab.USERS)
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(18.dp))
                    HorizontalDivider(color = GlassCardBorder.copy(alpha = 0.5f), thickness = 0.8.dp)
                    Spacer(modifier = Modifier.height(14.dp))

                    // Post stats / quick action bar inside card
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Like
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.clickable { onLikeClick() }
                        ) {
                            Icon(
                                painter = painterResource(if (link.engagement.isLiked) R.drawable.ic_heart_bold else R.drawable.ic_heart_outline),
                                contentDescription = "Beğen",
                                tint = if (link.engagement.isLiked) ErrorRed else TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = link.engagement.likesCount.toString(),
                                color = if (link.engagement.isLiked) ErrorRed else TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Comment
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.clickable { onCommentClick() }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_ai_commentary_outline),
                                contentDescription = "Yorum",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = link.engagement.commentsCount.toString(),
                                color = TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Relink
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.clickable { onRelinkClick() }
                        ) {
                            Icon(
                                painter = painterResource(if (link.engagement.isRelinked) R.drawable.ic_toy_6_bold else R.drawable.ic_toy_6_outline),
                                contentDescription = "Relink",
                                tint = if (link.engagement.isRelinked) LightPurple else TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = link.engagement.relinksCount.toString(),
                                color = if (link.engagement.isRelinked) LightPurple else TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Save
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.clickable { onSaveClick() }
                        ) {
                            Icon(
                                painter = painterResource(if (link.engagement.isSaved) R.drawable.ic_bookmark_2_bold else R.drawable.ic_bookmark_2_outline),
                                contentDescription = "Kaydet",
                                tint = if (link.engagement.isSaved) GradientYellow else TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = link.engagement.savesCount.toString(),
                                color = if (link.engagement.isSaved) GradientYellow else TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // Animated double-tap heart overlay
        DoubleTapHeartOverlay(
            isShowing = showHeartOverlay,
            onDismiss = { showHeartOverlay = false }
        )

        // Options bottom sheet
        if (showOptionsBottomSheet) {
            FeedPostOptionsBottomSheet(
                link = link,
                onDismiss = { showOptionsBottomSheet = false },
                onNotInterested = onNotInterested,
                onSaveToggle = onSaveClick,
                onReport = {},
                onHideUser = onHideUser
            )
        }
    }
}
