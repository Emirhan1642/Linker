package com.linker.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.linker.app.R
import com.linker.app.presentation.theme.LightGray
import com.linker.app.presentation.theme.TextSecondary
import com.linker.app.presentation.theme.AccentGreen
import com.linker.app.presentation.theme.LinkerAngularGradient
import com.linker.app.presentation.theme.TextHint
import com.linker.app.presentation.theme.TextPrimary

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

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

    val clickModifier = if (onClick != null || onLongClick != null) {
        Modifier.combinedClickable(
            onClick = { onClick?.invoke() },
            onLongClick = { onLongClick?.invoke() }
        )
    } else {
        Modifier
    }
    
    val unseenBorderWidth = (size.value / 20).dp
    val seenBorderWidth = (size.value / 25).dp
    val avatarPadding = if (effectiveStoryState == StoryState.NONE) 0.dp else (size.value / 15).dp
    val onlineIndicatorSize = size * 0.25f

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(clickModifier),
        contentAlignment = Alignment.Center
    ) {
        // Border ring (NONE: no border, UNSEEN: gradient, SEEN: white)
        Box(
            modifier = Modifier
                .matchParentSize()
                .then(
                    when (effectiveStoryState) {
                        StoryState.UNSEEN -> Modifier.border(width = unseenBorderWidth, LinkerAngularGradient, CircleShape)
                        StoryState.SEEN -> Modifier.border(width = seenBorderWidth, SolidColor(Color.White), CircleShape)
                        StoryState.NONE -> Modifier
                    }
                )
                .padding(avatarPadding)
        ) {
            // Actual Avatar
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(CircleShape)
                    .background(TextSecondary),
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
                        modifier = Modifier.matchParentSize()
                    )
                }
            }
        }

        // Online indicator (bottom right overlay)
        if (isOnline) {
            Box(
                modifier = Modifier
                    .size(onlineIndicatorSize)
                    .clip(CircleShape)
                    .background(Color.Black) // Inner spacing
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(AccentGreen)
                    .align(Alignment.BottomEnd)
            )
        }
    }
}
