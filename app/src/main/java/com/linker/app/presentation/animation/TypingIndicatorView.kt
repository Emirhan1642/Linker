package com.linker.app.presentation.animation

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
 * Animated sine-wave bouncing dots for active typing state.
 */
@Composable
fun TypingIndicatorView(
    dotColor: Color = AccentGreen,
    dotSize: Dp = 6.dp,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "typingIndicator")

    val offset1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 400, delayMillis = 0, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotOffset_0"
    )

    val offset2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 400, delayMillis = 150, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotOffset_150"
    )

    val offset3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 400, delayMillis = 300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotOffset_300"
    )

    Row(
        modifier = modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(offset1, offset2, offset3).forEach { offset: Float ->
            Box(
                modifier = Modifier
                    .offset(y = offset.dp)
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }
    }
}
