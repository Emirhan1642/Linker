package com.linker.app.presentation.screens.story

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.linker.app.R
import com.linker.app.domain.model.StoryReaction
import com.linker.app.domain.model.UserStories
import com.linker.app.presentation.components.LinkerAvatar
import com.linker.app.presentation.components.StoryState
import com.linker.app.presentation.components.ZoomableMediaBox
import com.linker.app.presentation.theme.LinkerAngularGradient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun StoryScreen(
    userId: String,
    allUserStories: List<UserStories>,
    onNavigateBack: () -> Unit,
    onUserTap: (userId: String) -> Unit,
    viewModel: StoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    val displayStories = if (uiState.allUserStories.isNotEmpty()) uiState.allUserStories else allUserStories

    // Find starting group index by userId
    val startGroupIndex = remember(displayStories, userId) {
        displayStories.indexOfFirst { it.author.userId == userId }.coerceAtLeast(0)
    }

    val groupPagerState = rememberPagerState(
        initialPage = startGroupIndex,
        pageCount = { displayStories.size }
    )

    var hasScrolledToInitialUser by remember { mutableStateOf(false) }
    LaunchedEffect(displayStories, userId) {
        if (!hasScrolledToInitialUser && displayStories.isNotEmpty()) {
            val targetIdx = displayStories.indexOfFirst { it.author.userId == userId }
            if (targetIdx >= 0) {
                groupPagerState.scrollToPage(targetIdx)
                hasScrolledToInitialUser = true
            }
        }
    }

    var isStoryZooming by remember { mutableStateOf(false) }

    // When last group ends, go back
    LaunchedEffect(groupPagerState.currentPage, displayStories) {
        val currentGroup = displayStories.getOrNull(groupPagerState.currentPage)
        if (currentGroup != null) {
            viewModel.loadStoriesForUser(currentGroup)
        }
    }

    // Vertical pager across user groups
    androidx.compose.foundation.pager.VerticalPager(
        state = groupPagerState,
        userScrollEnabled = !isStoryZooming,
        modifier = Modifier.fillMaxSize()
    ) { groupIndex ->
        val userStories = displayStories.getOrNull(groupIndex) ?: return@VerticalPager
        val stories = userStories.getActiveStories()

        var currentStoryIndex by remember(groupIndex) { mutableIntStateOf(0) }
        var currentProgress by remember { mutableFloatStateOf(0f) }
        var showReplyInput by remember { mutableStateOf(false) }
        var replyText by remember { mutableStateOf("") }
        var isHolding by remember { mutableStateOf(false) }
        var showMenu by remember { mutableStateOf(false) }
        var showDeleteDialog by remember { mutableStateOf(false) }
        var showViewersSheet by remember { mutableStateOf(false) }
        var showReportSheet by remember { mutableStateOf(false) }
        val replyFocusRequester = remember { FocusRequester() }

        val currentStory = stories.getOrNull(currentStoryIndex)
        val isOwnStory = currentStory != null && currentStory.author.userId == viewModel.currentUserId

        // Single-trigger view counter: increments ONLY once per unique story
        LaunchedEffect(currentStory?.storyId) {
            val sId = currentStory?.storyId
            if (sId != null) {
                viewModel.markViewed(sId)
            }
        }

        val isCurrentPageActive = groupPagerState.currentPage == groupIndex
        val isEffectivelyPaused = isHolding || showReplyInput || showMenu || showDeleteDialog || showViewersSheet || showReportSheet || isStoryZooming || !isCurrentPageActive

        // Auto-advance timer per story (strictly active page only)
        LaunchedEffect(currentStoryIndex, isEffectivelyPaused, isCurrentPageActive, stories) {
            val story = stories.getOrNull(currentStoryIndex) ?: return@LaunchedEffect
            val durationMs = (story.duration?.toLong()?.times(1000L)) ?: 5000L

            val interval = 50L
            val totalSteps = durationMs / interval
            while (currentProgress < 1f) {
                if (!isEffectivelyPaused) {
                    delay(interval)
                    currentProgress = (currentProgress + (1f / totalSteps)).coerceIn(0f, 1f)
                } else {
                    delay(100L)
                }
            }

            if (isCurrentPageActive) {
                // Advance to next story or next user group
                if (currentStoryIndex < stories.lastIndex) {
                    currentProgress = 0f
                    currentStoryIndex++
                } else if (groupIndex < displayStories.lastIndex) {
                    currentProgress = 0f
                    groupPagerState.animateScrollToPage(groupIndex + 1)
                } else {
                    onNavigateBack()
                }
            }
        }

        // Background Preload next story
        val context = androidx.compose.ui.platform.LocalContext.current
        LaunchedEffect(currentStoryIndex) {
            val nextStory = stories.getOrNull(currentStoryIndex + 1)
            val nextCleanUrl = com.linker.app.core.util.MediaUtils.sanitizeMediaUrl(nextStory?.mediaUrl)
            if (nextCleanUrl.isNotBlank()) {
                val request = coil3.request.ImageRequest.Builder(context)
                    .data(nextCleanUrl)
                    .build()
                coil3.SingletonImageLoader.get(context).enqueue(request)
            }
        }

        if (showDeleteDialog && currentStory != null) {
            AlertDialog(
                onDismissRequest = { 
                    showDeleteDialog = false
                },
                title = { Text("Hikayeyi Sil", color = com.linker.app.presentation.theme.TextPrimary, fontWeight = FontWeight.Bold) },
                text = { Text("Bu hikayeyi silmek istediğinize emin misiniz?", color = com.linker.app.presentation.theme.TextSecondary) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            viewModel.deleteStory(currentStory.storyId) {
                                if (stories.size <= 1) {
                                    onNavigateBack()
                                } else {
                                    currentStoryIndex = currentStoryIndex.coerceAtMost(stories.size - 2)
                                }
                            }
                        }
                    ) {
                        Text("Sil", color = com.linker.app.presentation.theme.ErrorRed, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                        }
                    ) {
                        Text("İptal", color = com.linker.app.presentation.theme.TextSecondary)
                    }
                },
                containerColor = com.linker.app.presentation.theme.DarkGray,
                shape = RoundedCornerShape(16.dp)
            )
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val screenWidth = maxWidth

            // 1. Zoomable Story Media Layer
            ZoomableMediaBox(
                modifier = Modifier.fillMaxSize(),
                onZoomStateChanged = { isStoryZooming = it }
            ) {
                if (currentStory != null) {
                    val cleanUrl = com.linker.app.core.util.MediaUtils.sanitizeMediaUrl(currentStory.mediaUrl)
                    val isVideo = currentStory.mediaType == com.linker.app.domain.model.StoryMediaType.VIDEO ||
                            com.linker.app.core.util.MediaUtils.isVideoUrl(cleanUrl)

                    if (isVideo && cleanUrl.isNotBlank()) {
                        com.linker.app.presentation.screens.home.VideoPlayerView(
                            videoUrl = cleanUrl,
                            isPlaying = !isEffectivelyPaused,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (cleanUrl.isNotBlank()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = cleanUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .drawWithContent {
                                        drawContent()
                                        drawRect(Color.Black.copy(alpha = 0.55f))
                                    }
                                    .blur(30.dp)
                            )
                            AsyncImage(
                                model = cleanUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(listOf(Color(0xFF2D1B69), Color(0xFF11111F)))
                                )
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(listOf(Color(0xFF2D1B69), Color(0xFF11111F)))
                            )
                    )
                }
            }

            // 2. Gesture Tap & Hold Detection Layer (Passes multi-touch pinch to ZoomableMediaBox)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(currentStoryIndex, groupIndex) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val downTime = System.currentTimeMillis()
                            var isLongHold = false
                            var isMultiTouch = false

                            // Wait in gesture loop; only activate hold pause after 200ms
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.size > 1) {
                                    isMultiTouch = true
                                }
                                if (!event.changes.any { it.pressed }) {
                                    break
                                }
                                val elapsed = System.currentTimeMillis() - downTime
                                if (elapsed > 200 && !isLongHold && !isMultiTouch && !isStoryZooming) {
                                    isLongHold = true
                                    isHolding = true
                                }
                            }

                            val holdDuration = System.currentTimeMillis() - downTime
                            isHolding = false

                            // Quick tap (< 200ms) navigates; Multi-touch zoom ignored for navigation
                            if (holdDuration <= 200 && !isLongHold && !isMultiTouch && !isStoryZooming && !showReplyInput && !showMenu && !showReportSheet && !showViewersSheet) {
                                val isLeft = down.position.x < screenWidth.toPx() * 0.35f
                                scope.launch {
                                    currentProgress = 0f
                                    if (isLeft) {
                                        if (currentStoryIndex > 0) {
                                            currentStoryIndex--
                                        } else if (groupIndex > 0) {
                                            groupPagerState.animateScrollToPage(groupIndex - 1)
                                        }
                                    } else {
                                        if (currentStoryIndex < stories.lastIndex) {
                                            currentStoryIndex++
                                        } else if (groupIndex < displayStories.lastIndex) {
                                            groupPagerState.animateScrollToPage(groupIndex + 1)
                                        } else {
                                            onNavigateBack()
                                        }
                                    }
                                }
                            }
                        }
                    }
            )

            // 3. Top HUD (Clean view when held)
            AnimatedVisibility(
                visible = !isHolding,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                            )
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    // Progress bars
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        stories.forEachIndexed { index, _ ->
                            val progress = when {
                                index < currentStoryIndex -> 1f
                                index == currentStoryIndex -> currentProgress
                                else -> 0f
                            }
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(2.dp)
                                    .clip(RoundedCornerShape(1.dp)),
                                color = Color.White,
                                trackColor = Color.White.copy(alpha = 0.3f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))                    // Author row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onUserTap(userStories.author.userId) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_arrow_left_01_outline),
                                contentDescription = "Geri",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        LinkerAvatar(
                            imageUrl = userStories.author.profileImageUrl,
                            size = 38.dp,
                            storyState = StoryState.NONE
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = userStories.author.username,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            currentStory?.let {
                                val minutesAgo = ((System.currentTimeMillis() - it.createdAt) / 60_000).toInt()
                                Text(
                                    text = if (minutesAgo < 60) "$minutesAgo dk önce"
                                           else "${minutesAgo / 60} sa önce",
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // 3-dot menu with owner delete / report / viewers
                        Box {
                            IconButton(onClick = { 
                                showMenu = true 
                            }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Daha fazla",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { 
                                    showMenu = false 
                                },
                                modifier = Modifier.background(com.linker.app.presentation.theme.DarkGray)
                            ) {
                                if (isOwnStory) {
                                    DropdownMenuItem(
                                        text = { 
                                            Text("Görüntüleyenler (${currentStory?.viewsCount ?: 0})", color = com.linker.app.presentation.theme.TextPrimary) 
                                        },
                                        onClick = {
                                            showMenu = false
                                            currentStory?.let {
                                                viewModel.loadStoryViewers(it.storyId)
                                                showViewersSheet = true
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Hikayeyi Sil", color = com.linker.app.presentation.theme.ErrorRed) },
                                        onClick = {
                                            showMenu = false
                                            showDeleteDialog = true
                                        }
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text("Şikayet Et", color = com.linker.app.presentation.theme.ErrorRed) },
                                        onClick = {
                                            showMenu = false
                                            showReportSheet = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Bottom: caption + emoji reactions + reply input (Clean view when held or zooming)
            AnimatedVisibility(
                visible = !isHolding && !isStoryZooming,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Story Caption display
                    if (!currentStory?.caption.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.Black.copy(alpha = 0.6f))
                                .border(0.8.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = currentStory?.caption ?: "",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Emoji reaction quick panel (only on other's stories)
                    if (!isOwnStory) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(StoryReaction.values().toList()) { reaction ->
                                val isSelected = currentStory?.reactionEmoji == reaction.emoji
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(22.dp))
                                        .background(
                                            if (isSelected) Color.White.copy(alpha = 0.25f)
                                            else Color.Black.copy(alpha = 0.4f)
                                        )
                                        .border(
                                            width = if (isSelected) 1.5.dp else 0.dp,
                                            color = Color.White.copy(alpha = 0.6f),
                                            shape = RoundedCornerShape(22.dp)
                                        )
                                        .clickable {
                                            currentStory?.let { story ->
                                                val newEmoji = if (isSelected) null else reaction.emoji
                                                viewModel.reactToStory(story.storyId, newEmoji)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = reaction.emoji, fontSize = 22.sp)
                                }
                            }
                        }
                    }

                    // Reply input + like button / or Viewers Bar on own story
                    if (isOwnStory) {
                        // Own Story Viewers Peek Bar
                        val storyViewersList by viewModel.storyViewers.collectAsState()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color.Black.copy(alpha = 0.5f))
                                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                                .clickable {
                                    currentStory?.let {
                                        viewModel.loadStoryViewers(it.storyId)
                                        showViewersSheet = true
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("👁️", fontSize = 16.sp)
                                Text(
                                    text = "Görüntüleyenler (${currentStory?.viewsCount ?: 0})",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if ((currentStory?.likesCount ?: 0) > 0) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Favorite,
                                        contentDescription = null,
                                        tint = Color(0xFFFF4B4B),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "${currentStory?.likesCount}",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    } else {
                        // Reply input + like button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Text input field
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(28.dp))
                                    .border(1.5.dp, LinkerAngularGradient, RoundedCornerShape(28.dp))
                                    .background(Color.Black.copy(alpha = 0.5f))
                            ) {
                                if (showReplyInput) {
                                    LaunchedEffect(Unit) {
                                        replyFocusRequester.requestFocus()
                                    }
                                    TextField(
                                        value = replyText,
                                        onValueChange = { replyText = it },
                                        placeholder = {
                                            Text("Yanıtla...", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
                                        },
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent
                                        ),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                        keyboardActions = KeyboardActions(
                                            onSend = {
                                                if (replyText.isNotBlank()) {
                                                    currentStory?.let {
                                                        viewModel.replyToStory(it.storyId, replyText.trim())
                                                    }
                                                }
                                                replyText = ""
                                                showReplyInput = false
                                            }
                                        ),
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(replyFocusRequester)
                                            .padding(horizontal = 4.dp)
                                    )
                                } else {
                                    Text(
                                        text = "Yanıtla...",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 14.sp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showReplyInput = true }
                                            .padding(horizontal = 20.dp, vertical = 16.dp)
                                    )
                                }
                            }

                            // Like button
                            val isLiked = currentStory?.isLiked ?: false
                            val likesCount = currentStory?.likesCount ?: 0
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                IconButton(
                                    onClick = {
                                        currentStory?.let { story ->
                                            viewModel.likeStory(story.storyId)
                                        }
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                                        contentDescription = "Beğen",
                                        tint = if (isLiked) Color(0xFFFF4B4B) else Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                if (likesCount > 0) {
                                    Text(
                                        text = formatLikes(likesCount),
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Reply overlay scrim to dismiss reply on outside click
            if (showReplyInput) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {
                            showReplyInput = false
                        }
                )
            }

            // Viewers Bottom Sheet
            if (showViewersSheet && currentStory != null) {
                val storyViewersList by viewModel.storyViewers.collectAsState()
                val isLoadingViewers by viewModel.isLoadingViewers.collectAsState()
                StoryViewersBottomSheet(
                    viewers = storyViewersList,
                    isLoading = isLoadingViewers,
                    onDismiss = { showViewersSheet = false },
                    onUserClick = { uId ->
                        showViewersSheet = false
                        onUserTap(uId)
                    }
                )
            }

            // Report Bottom Sheet
            if (showReportSheet && currentStory != null) {
                StoryReportBottomSheet(
                    onDismiss = { showReportSheet = false },
                    onReportSubmit = { reason ->
                        viewModel.reportStory(currentStory.storyId, reason)
                        android.widget.Toast.makeText(context, "Şikayetiniz incelenmek üzere iletildi", android.widget.Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

private fun formatLikes(count: Int): String = when {
    count >= 1_000_000 -> "${count / 1_000_000}M"
    count >= 1_000 -> "${count / 1_000}K"
    else -> count.toString()
}
