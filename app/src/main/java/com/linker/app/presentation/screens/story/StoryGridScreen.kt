package com.linker.app.presentation.screens.story

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.linker.app.R
import com.linker.app.domain.model.UserStories
import com.linker.app.presentation.components.LinkerAvatar
import com.linker.app.presentation.components.StoryState
import com.linker.app.presentation.theme.Black
import com.linker.app.presentation.theme.DarkGray
import com.linker.app.presentation.theme.LinkerAngularGradient
import com.linker.app.presentation.theme.TextHint
import com.linker.app.presentation.theme.TextPrimary
import com.linker.app.presentation.theme.TextSecondary

/**
 * Story Grid Screen — TikTok LIVE style 2-column grid layout.
 *
 * Shows all active stories grouped by user as large cards.
 * Tapping a card opens the StoryViewer for that user.
 * When no stories remain, navigates back automatically.
 */
@Composable
fun StoryGridScreen(
    onNavigateBack: () -> Unit,
    onOpenStoryViewer: (userId: String) -> Unit,
    viewModel: StoryGridViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            StoryGridTopBar(onNavigateBack = onNavigateBack)

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }

                uiState.error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = uiState.error ?: "Bir hata oluştu",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = uiState.userStories,
                            key = { it.author.userId }
                        ) { userStories ->
                            StoryGridCard(
                                userStories = userStories,
                                onClick = { onOpenStoryViewer(userStories.author.userId) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StoryGridTopBar(onNavigateBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onNavigateBack) {
            Icon(
                painter = painterResource(id = R.drawable.ic_arrow_left_01_outline),
                contentDescription = "Geri",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
        Text(
            text = "Storyler",
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

/**
 * TikTok LIVE-style story card with large thumbnail, user avatar and username.
 * Viewed stories appear slightly dimmed.
 */
@Composable
private fun StoryGridCard(
    userStories: UserStories,
    onClick: () -> Unit
) {
    val allViewed = !userStories.hasUnviewed
    val alpha by animateFloatAsState(
        targetValue = if (allViewed) 0.5f else 1f,
        animationSpec = tween(durationMillis = 300),
        label = "storyCardAlpha"
    )

    val mostRecentStory = userStories.getMostRecentStory()
    val viewsCount = userStories.stories.sumOf { it.viewsCount }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(9f / 16f) // Vertical card (portrait)
            .clip(RoundedCornerShape(16.dp))
            .alpha(alpha)
            .clickable { onClick() }
    ) {
        // Thumbnail background
        if (mostRecentStory?.thumbnailUrl != null || mostRecentStory?.mediaUrl != null) {
            AsyncImage(
                model = mostRecentStory.thumbnailUrl ?: mostRecentStory.mediaUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Placeholder gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF2D2D3A),
                                Color(0xFF1A1A24)
                            )
                        )
                    )
            )
        }

        // Gradient overlay (bottom to top)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.7f)
                        ),
                        startY = 200f
                    )
                )
        )

        // Gradient ring when unviewed
        if (!allViewed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        width = 2.dp,
                        brush = LinkerAngularGradient,
                        shape = RoundedCornerShape(16.dp)
                    )
            )
        }

        // Bottom info overlay
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // User avatar + name row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LinkerAvatar(
                    imageUrl = userStories.author.profileImageUrl,
                    size = 32.dp,
                    storyState = if (allViewed) StoryState.SEEN else StoryState.UNSEEN
                )
                Text(
                    text = userStories.author.username,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // View count
            if (viewsCount > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_ai_users_outline),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = formatCount(viewsCount),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Story count badge
            val storyCount = userStories.stories.size
            if (storyCount > 1) {
                Text(
                    text = "$storyCount story",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 10.sp
                )
            }
        }
    }
}

private fun formatCount(count: Int): String {
    return when {
        count >= 1_000_000 -> "${count / 1_000_000}M"
        count >= 1_000 -> "${count / 1_000}K"
        else -> count.toString()
    }
}
