package com.linker.app.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import kotlinx.coroutines.launch

@Composable
fun ZoomableMediaBox(
    modifier: Modifier = Modifier,
    onZoomStateChanged: (Boolean) -> Unit = {},
    content: @Composable () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var pivot by remember { mutableStateOf(Offset(0.5f, 0.5f)) }

    val animatableScale = remember { Animatable(1f) }
    val animatableOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    var isFirstTwoFinger = true
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size >= 2) {
                            val centroid = event.calculateCentroid(useCurrent = true)
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()

                            if (isFirstTwoFinger && size.width > 0 && size.height > 0) {
                                // Set transform pivot based on the exact two-finger touch centroid!
                                pivot = Offset(
                                    (centroid.x / size.width.toFloat()).coerceIn(0f, 1f),
                                    (centroid.y / size.height.toFloat()).coerceIn(0f, 1f)
                                )
                                isFirstTwoFinger = false
                            }

                            val newScale = (scale * zoom).coerceIn(1f, 4.5f)
                            scale = newScale
                            val isCurrentlyZoomed = newScale > 1.05f
                            onZoomStateChanged(isCurrentlyZoomed)

                            if (isCurrentlyZoomed) {
                                offset += pan
                            }

                            // Consume all pointer events during multi-touch so underlying pagers do not swipe!
                            event.changes.forEach { change ->
                                if (change.positionChanged()) {
                                    change.consume()
                                }
                            }
                        } else {
                            isFirstTwoFinger = true
                        }
                    } while (event.changes.any { it.pressed })

                    // When fingers are lifted, smoothly snap back to normal
                    if (scale > 1.05f || offset != Offset.Zero) {
                        scope.launch {
                            animatableScale.snapTo(scale)
                            animatableOffset.snapTo(offset)
                            launch {
                                animatableScale.animateTo(
                                    1f,
                                    spring(stiffness = Spring.StiffnessMediumLow)
                                )
                            }
                            launch {
                                animatableOffset.animateTo(
                                    Offset.Zero,
                                    spring(stiffness = Spring.StiffnessMediumLow)
                                )
                            }
                            scale = 1f
                            offset = Offset.Zero
                            onZoomStateChanged(false)
                        }
                    } else {
                        scale = 1f
                        offset = Offset.Zero
                        onZoomStateChanged(false)
                    }
                }
            }
            .graphicsLayer {
                val currentScale = if (animatableScale.isRunning) animatableScale.value else scale
                val currentOffset = if (animatableOffset.isRunning) animatableOffset.value else offset

                scaleX = currentScale
                scaleY = currentScale
                translationX = currentOffset.x
                translationY = currentOffset.y
                transformOrigin = TransformOrigin(pivot.x, pivot.y)
            }
    ) {
        content()
    }
}
