package com.linker.app.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.linker.app.presentation.animation.bouncyClick
import com.linker.app.presentation.theme.*

enum class StoryState { NONE, UNSEEN, SEEN }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LinkerAvatar(
    imageUrl: String?,
    size: Dp = 56.dp,
    storyState: StoryState = StoryState.NONE,
    isOnline: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val effectiveStoryState = storyState

    val infiniteTransition = rememberInfiniteTransition(label = "avatarRingRotate")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "avatarRingRotation"
    )

    val clickModifier = if (onClick != null || onLongClick != null) {
        Modifier.combinedClickable(
            onClick = { onClick?.invoke() },
            onLongClick = { onLongClick?.invoke() }
        )
    } else {
        Modifier
    }

    val unseenBorderWidth = (size.value / 22).coerceAtLeast(2f).dp
    val seenBorderWidth = (size.value / 28).coerceAtLeast(1.5f).dp
    val avatarPadding = if (effectiveStoryState == StoryState.NONE) 0.dp else (size.value / 16).coerceAtLeast(3f).dp
    val onlineIndicatorSize = size * 0.25f

    Box(
        modifier = modifier
            .size(size)
            .bouncyClick(enabled = onClick != null || onLongClick != null) {
                onClick?.invoke()
            }
            .then(clickModifier),
        contentAlignment = Alignment.Center
    ) {
        // Border ring (NONE: no border, UNSEEN: rotating sweep gradient, SEEN: subtle white/gray)
        Box(
            modifier = Modifier
                .matchParentSize()
                .then(
                    when (effectiveStoryState) {
                        StoryState.UNSEEN -> Modifier
                            .rotate(rotation)
                            .border(width = unseenBorderWidth, LinkerAngularGradient, CircleShape)
                        StoryState.SEEN -> Modifier.border(width = seenBorderWidth, SolidColor(Color.White.copy(alpha = 0.7f)), CircleShape)
                        StoryState.NONE -> Modifier.border(1.dp, GlassCardBorder, CircleShape)
                    }
                )
                .padding(avatarPadding)
        ) {
            // Actual Avatar
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(CircleShape)
                    .background(DarkGrayTransparent),
                contentAlignment = Alignment.Center
            ) {
                if (imageUrl != null) {
                    val placeholderPainter = rememberVectorPainter(Icons.Default.Person)
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = "Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                        placeholder = placeholderPainter,
                        error = placeholderPainter
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Avatar",
                        tint = TextSecondary,
                        modifier = Modifier.matchParentSize().padding(size * 0.2f)
                    )
                }
            }
        }

        // Online indicator (bottom right overlay)
        if (isOnline) {
            Box(
                modifier = Modifier
                    .size(onlineIndicatorSize)
                    .align(Alignment.BottomEnd)
                    .offset(x = (-2).dp, y = (-2).dp)
                    .clip(CircleShape)
                    .background(AccentGreen)
                    .border(2.dp, Black, CircleShape)
            )
        }
    }
}
