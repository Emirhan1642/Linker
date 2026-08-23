package com.linker.app.presentation.animation

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import com.linker.app.presentation.theme.DarkGray
import com.linker.app.presentation.theme.LightGray

/**
 * Adds a bouncy scale-down effect when pressed with smooth spring physics.
 */
fun Modifier.bouncyClick(
    enabled: Boolean = true,
    scaleDown: Float = 0.93f,
    onClick: () -> Unit
): Modifier = composed {
    if (!enabled) return@composed this
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bouncyClickScale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            enabled = enabled,
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
        .pointerInput(Unit) {
            while (true) {
                awaitPointerEventScope {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    waitForUpOrCancellation()
                    isPressed = false
                }
            }
        }
}

/**
 * Shimmer effect modifier for modern skeleton loading.
 */
fun Modifier.shimmerEffect(
    enabled: Boolean = true,
    shimmerColors: List<Color> = listOf(
        DarkGray,
        LightGray.copy(alpha = 0.6f),
        DarkGray
    )
): Modifier = composed {
    if (!enabled) return@composed this

    var size by remember { mutableStateOf(IntSize.Zero) }
    val transition = rememberInfiniteTransition(label = "shimmerTransition")
    val startOffsetX by transition.animateFloat(
        initialValue = -2 * size.width.toFloat(),
        targetValue = 2 * size.width.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffsetX"
    )

    this
        .onGloballyPositioned { size = it.size }
        .background(
            brush = Brush.linearGradient(
                colors = shimmerColors,
                start = Offset(startOffsetX, 0f),
                end = Offset(startOffsetX + size.width.toFloat(), size.height.toFloat())
            )
        )
}
