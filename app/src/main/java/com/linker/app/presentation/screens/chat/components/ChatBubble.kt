package com.linker.app.presentation.screens.chat.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linker.app.R
import com.linker.app.domain.model.MessageStatus
import com.linker.app.domain.model.MessageType
import com.linker.app.presentation.screens.chat.MessageItem
import com.linker.app.presentation.theme.AccentGreen
import com.linker.app.presentation.theme.TextPrimary
import com.linker.app.presentation.theme.TextSecondary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Message bubble component with swipe-to-reply and long-press actions
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(
    message: MessageItem,
    coroutineScope: CoroutineScope,
    onBubblePositioned: (Rect) -> Unit,
    onLongPress: () -> Unit,
    onSwipeReply: () -> Unit,
    isHighlighted: Boolean,
    onHaptic: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val swipeOffset = remember { Animatable(0f) }
    val highlightAlpha = remember { Animatable(0f) }

    LaunchedEffect(isHighlighted) {
        if (isHighlighted) {
            highlightAlpha.animateTo(1f, tween(150))
            delay(800)
            highlightAlpha.animateTo(0f, tween(400))
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { translationX = swipeOffset.value }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val threshold = 80f
                        if (swipeOffset.value > threshold) {
                            coroutineScope.launch {
                                swipeOffset.animateTo(0f, tween(200))
                            }
                            onSwipeReply()
                        } else {
                            coroutineScope.launch {
                                swipeOffset.animateTo(0f, tween(200))
                            }
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        val newValue = (swipeOffset.value + dragAmount).coerceIn(0f, 120f)
                        coroutineScope.launch {
                            swipeOffset.snapTo(newValue)
                        }
                        if (newValue > 30f && newValue - dragAmount <= 30f) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                )
            }
            .combinedClickable(
                onClick = { },
                onLongClick = {
                    onHaptic()
                    onLongPress()
                }
            )
            .onGloballyPositioned { coordinates ->
                onBubblePositioned(coordinates.boundsInRoot())
            }
    ) {
        MessageBubbleContent(
            message = message,
            highlightAlpha = highlightAlpha.value
        )
    }
}

@Composable
fun MessageBubbleContent(
    message: MessageItem,
    highlightAlpha: Float = 0f,
    modifier: Modifier = Modifier
) {
    val bubbleColor = if (message.isSelf) Color(0xFF007E8E) else Color(0xFF2A2A2E)
    val alignment = if (message.isSelf) Alignment.CenterEnd else Alignment.CenterStart

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .drawBehind {
                    if (highlightAlpha > 0f) {
                        drawCircle(
                            color = Color(0xFFFFD700).copy(alpha = highlightAlpha * 0.3f),
                            radius = size.width.coerceAtLeast(size.height) * 0.8f,
                            center = Offset(size.width / 2, size.height / 2)
                        )
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = if (message.isSelf) 18.dp else 4.dp,
                            bottomEnd = if (message.isSelf) 4.dp else 18.dp
                        )
                    )
                    .background(bubbleColor)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = message.text,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    lineHeight = 20.sp
                )
            }

            // Status indicators
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (message.isSelf) {
                    MessageStatusIcon(status = message.status)
                }
            }
        }
    }
}

@Composable
fun MessageStatusIcon(status: MessageStatus) {
    when (status) {
        MessageStatus.SENDING -> {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                color = TextSecondary,
                strokeWidth = 1.5.dp
            )
        }
        MessageStatus.SENT -> {
            Icon(
                painter = painterResource(id = R.drawable.ic_forward_outline),
                contentDescription = "Sent",
                tint = TextSecondary,
                modifier = Modifier.size(14.dp)
            )
        }
        MessageStatus.DELIVERED -> {
            Icon(
                painter = painterResource(id = R.drawable.ic_forward_bold),
                contentDescription = "Delivered",
                tint = TextSecondary,
                modifier = Modifier.size(14.dp)
            )
        }
        MessageStatus.READ -> {
            Icon(
                painter = painterResource(id = R.drawable.ic_archive_book_outline),
                contentDescription = "Read",
                tint = AccentGreen,
                modifier = Modifier.size(14.dp)
            )
        }
        MessageStatus.FAILED -> {
            Icon(
                painter = painterResource(id = R.drawable.ic_cloud_cross_outline),
                contentDescription = "Failed",
                tint = Color(0xFFFF4B4B),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
