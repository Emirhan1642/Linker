package com.linker.app.presentation.screens.story

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.linker.app.R
import com.linker.app.domain.model.Story
import com.linker.app.domain.model.StoryReaction
import com.linker.app.domain.model.UserStories
import com.linker.app.presentation.components.LinkerAvatar
import com.linker.app.presentation.components.StoryState
import com.linker.app.presentation.theme.LinkerAngularGradient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Story Viewer Screen — fullscreen pager across all UserStories groups.
 *
 * Navigation model:
 *  - Within a user group: horizontal pager (left/right or tap sides)
 *  - Between user groups: vertical swipe (next user's stories below)
 *  - When all groups exhausted: navigate back to StoryGrid
 *
 * Features:
 *  - Animated progress bars per story
 *  - Bottom emoji reaction row + text reply input
 *  - Like button with optimistic counter update
 *  - 3-dot menu (report, mute, share)
 */
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

    val displayStories = if (allUserStories.isNotEmpty()) allUserStories else uiState.allUserStories

    // Find starting group index by userId
    val startGroupIndex = displayStories.indexOfFirst { it.author.userId == userId }
        .coerceAtLeast(0)

    val groupPagerState = rememberPagerState(
        initialPage = startGroupIndex,
        pageCount = { displayStories.size }
    )

    // When last group ends, go back to grid
    LaunchedEffect(groupPagerState.currentPage, displayStories) {
        val currentGroup = displayStories.getOrNull(groupPagerState.currentPage)
        if (currentGroup != null) {
            viewModel.loadStoriesForUser(currentGroup)
        }
    }

    // Vertical pager across user groups
    androidx.compose.foundation.pager.VerticalPager(
        state = groupPagerState,
        modifier = Modifier.fillMaxSize()
    ) { groupIndex ->
        val userStories = displayStories.getOrNull(groupIndex) ?: return@VerticalPager
        val stories = userStories.getActiveStories()

        val storyPagerState = rememberPagerState(pageCount = { stories.size })
        var currentProgress by remember { mutableStateOf(0f) }
        var showReplyInput by remember { mutableStateOf(false) }
        var replyText by remember { mutableStateOf("") }

        // Auto-advance timer
        LaunchedEffect(storyPagerState.currentPage) {
            val story = stories.getOrNull(storyPagerState.currentPage) ?: return@LaunchedEffect
            val durationMs = (story.duration?.toLong()?.times(1000L)) ?: 5000L
            viewModel.markViewed(story.storyId)
            currentProgress = 0f

            val interval = 50L
            val steps = durationMs / interval
            repeat(steps.toInt()) {
                delay(interval)
                currentProgress = (it + 1).toFloat() / steps
            }

            // Advance to next story or next user group
            if (storyPagerState.currentPage < stories.lastIndex) {
                storyPagerState.animateScrollToPage(storyPagerState.currentPage + 1)
            } else if (groupIndex < displayStories.lastIndex) {
                groupPagerState.animateScrollToPage(groupIndex + 1)
            } else {
                onNavigateBack()
            }
        }

        val currentStory = stories.getOrNull(storyPagerState.currentPage)

        Box(modifier = Modifier.fillMaxSize()) {
            // Story media
            if (currentStory != null) {
                AsyncImage(
                    model = currentStory.mediaUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(listOf(Color(0xFF2D1B69), Color(0xFF11111F)))
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
                                Color.Black.copy(alpha = 0.55f),
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.75f)
                            )
                        )
                    )
            )

            // Tap zones (skip backward / forward)
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable {
                            scope.launch {
                                if (storyPagerState.currentPage > 0) {
                                    storyPagerState.animateScrollToPage(storyPagerState.currentPage - 1)
                                }
                            }
                        }
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable {
                            scope.launch {
                                if (storyPagerState.currentPage < stories.lastIndex) {
                                    storyPagerState.animateScrollToPage(storyPagerState.currentPage + 1)
                                } else if (groupIndex < allUserStories.lastIndex) {
                                    groupPagerState.animateScrollToPage(groupIndex + 1)
                                } else {
                                    onNavigateBack()
                                }
                            }
                        }
                )
            }

            // Top HUD
            Column(
                modifier = Modifier
                    .fillMaxWidth()
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
                            index < storyPagerState.currentPage -> 1f
                            index == storyPagerState.currentPage -> currentProgress
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

                Spacer(modifier = Modifier.height(12.dp))

                // Author row
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                    Column(modifier = Modifier
                        .weight(1f)
                        .clickable { onUserTap(userStories.author.userId) }) {
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

                    // 3-dot menu
                    var showMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Daha fazla",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    // Menu shown via ContentReportSheet in real impl; placeholder for now
                }
            }

            // Bottom: emoji reactions + reply input
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Emoji reaction quick panel
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
                                        currentStory?.let {
                                            viewModel.replyToStory(it.storyId, replyText)
                                        }
                                        replyText = ""
                                        showReplyInput = false
                                    }
                                ),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
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
}

private fun formatLikes(count: Int): String = when {
    count >= 1_000_000 -> "${count / 1_000_000}M"
    count >= 1_000 -> "${count / 1_000}K"
    else -> count.toString()
}
