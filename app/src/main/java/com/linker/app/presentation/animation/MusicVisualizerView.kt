package com.linker.app.presentation.animation

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.linker.app.presentation.theme.AccentGreen

/**
 * Animated Spotify EQ equalizer bars.
 */
@Composable
fun MusicVisualizerView(
    isPlaying: Boolean = true,
    barColor: Color = AccentGreen,
    barWidth: Dp = 3.dp,
    maxHeight: Dp = 18.dp,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "musicVisualizer")

    val bar1 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = if (isPlaying) 1.0f else 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar1"
    )

    val bar2 by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = if (isPlaying) 0.3f else 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar2"
    )

    val bar3 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = if (isPlaying) 0.9f else 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(480, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar3"
    )

    val bar4 by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = if (isPlaying) 0.2f else 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(620, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar4"
    )

    Row(
        modifier = modifier.height(maxHeight),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        listOf(bar1, bar2, bar3, bar4).forEach { heightFraction ->
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .fillMaxHeight(heightFraction.coerceIn(0.15f, 1.0f))
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor)
            )
        }
    }
}
