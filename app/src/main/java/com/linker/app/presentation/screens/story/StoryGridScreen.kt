package com.linker.app.presentation.screens.story

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.linker.app.R
import com.linker.app.domain.model.UserStories
import com.linker.app.presentation.animation.bouncyClick
import com.linker.app.presentation.components.AmbientGlow
import com.linker.app.presentation.components.GlassBox
import com.linker.app.presentation.components.GlassIconButton
import com.linker.app.presentation.components.LinkerAvatar
import com.linker.app.presentation.components.PillBadge
import com.linker.app.presentation.components.StoryState
import com.linker.app.presentation.theme.*

/**
 * Story Grid Screen — TikTok LIVE style 2-column grid layout.
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
            .background(ObsidianBackgroundGradient)
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
                        CircularProgressIndicator(color = GradientBlue)
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
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
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
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassIconButton(
            iconRes = R.drawable.ic_arrow_left_01_outline,
            onClick = onNavigateBack,
            size = 44.dp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "Stories",
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun StoryGridCard(
    userStories: UserStories,
    onClick: () -> Unit
) {
    val allViewed = !userStories.hasUnviewed
    val alpha by animateFloatAsState(
        targetValue = if (allViewed) 0.6f else 1f,
        animationSpec = tween(durationMillis = 300),
        label = "storyCardAlpha"
    )

    val mostRecentStory = userStories.getMostRecentStory()
    val viewsCount = userStories.stories.sumOf { it.viewsCount }

    GlassBox(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(9f / 16f)
            .alpha(alpha)
            .bouncyClick(onClick = onClick)
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
                            Color.Black.copy(alpha = 0.8f)
                        ),
                        startY = 180f
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
                        shape = RoundedCornerShape(20.dp)
                    )
            )
        }

        // Bottom info overlay in glass capsule
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp),
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
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // View count & story count
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (viewsCount > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_ai_users_outline),
                            contentDescription = null,
                            tint = GradientBlue,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = formatCount(viewsCount),
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                val storyCount = userStories.stories.size
                if (storyCount > 1) {
                    PillBadge(
                        text = "$storyCount",
                        accentColor = GradientPurple,
                        fontSize = 9
                    )
                }
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
