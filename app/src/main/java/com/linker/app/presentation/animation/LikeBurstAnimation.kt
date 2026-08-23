package com.linker.app.presentation.animation

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.linker.app.R
import kotlinx.coroutines.delay

/**
 * Animated double-tap heart burst effect (Instagram style).
 */
@Composable
fun DoubleTapHeartOverlay(
    isShowing: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isShowing) return

    var visible by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (visible) 1.2f else 0.2f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "heartScale"
    )

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 350, easing = LinearOutSlowInEasing),
        label = "heartAlpha"
    )

    LaunchedEffect(isShowing) {
        if (isShowing) {
            visible = true
            delay(600)
            visible = false
            delay(350)
            onDismiss()
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_heart_bold),
            contentDescription = null,
            tint = Color.Red.copy(alpha = 0.95f),
            modifier = Modifier
                .size(110.dp)
                .scale(scale)
                .alpha(alpha)
        )
    }
}
