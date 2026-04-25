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

enum class StoryState { NONE, UNSEEN, SEEN }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LinkerAvatar(
    imageUrl: String?, // Would use Coil AsyncImage in a real app, currently fallback to Icon
    size: Dp = 56.dp,
    hasStory: Boolean = false, // Deprecated, use storyState
    storyState: StoryState = StoryState.NONE,
    isOnline: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val effectiveStoryState = if (hasStory) StoryState.UNSEEN else storyState

    val clickModifier = if (onClick != null || onLongClick != null) {
        Modifier.combinedClickable(
            onClick = { onClick?.invoke() },
            onLongClick = { onLongClick?.invoke() }
        )
    } else {
        Modifier
    }

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
                        StoryState.UNSEEN -> Modifier.border(width = (size/20).value.dp, LinkerAngularGradient, CircleShape)
                        StoryState.SEEN -> Modifier.border(width = (size/25).value.dp, SolidColor(Color.White), CircleShape)
                        StoryState.NONE -> Modifier
                    }
                )
                .padding(if (effectiveStoryState == StoryState.NONE) 0.dp else (size/15).value.dp)
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
                    /**
                     * AVATAR IMAGE LOADING:
                     * Replace with Coil AsyncImage once profile images are implemented:
                     * 
                     * AsyncImage(
                     *     model = imageUrl,
                     *     contentDescription = "Avatar",
                     *     contentScale = ContentScale.Crop,
                     *     modifier = Modifier.matchParentSize(),
                     *     placeholder = painterResource(R.drawable.ic_person_placeholder),
                     *     error = painterResource(R.drawable.ic_person_placeholder)
                     * )
                     */
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Avatar",
                        modifier = Modifier.matchParentSize()
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
                    .size(size * 0.25f)
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
