package com.linker.app.presentation.screens.chat.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linker.app.R
import com.linker.app.domain.model.MessageStatus
import com.linker.app.domain.model.MessageType
import com.linker.app.presentation.components.LinkerAvatar
import com.linker.app.presentation.components.StoryState
import com.linker.app.presentation.screens.chat.MessageItem
import com.linker.app.presentation.theme.AccentGreen
import com.linker.app.presentation.theme.TextPrimary
import com.linker.app.presentation.theme.TextSecondary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// Magic numbers replacement
private const val SWIPE_THRESHOLD = 80f
private const val MAX_SWIPE_OFFSET = 120f
private const val HAPTIC_TRIGGER_OFFSET = 30f

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
    onNoteReplyClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val swipeOffset = remember { Animatable(0f) }
    val highlightAlpha = remember { Animatable(0f) }

    LaunchedEffect(isHighlighted) {
        if (isHighlighted) {
            // 3 kez yumuşak yanıp sönme
            repeat(3) {
                highlightAlpha.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
                highlightAlpha.animateTo(0f, tween(600, easing = FastOutSlowInEasing))
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { translationX = swipeOffset.value }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (swipeOffset.value > SWIPE_THRESHOLD) {
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
                        val newValue = (swipeOffset.value + dragAmount).coerceIn(0f, MAX_SWIPE_OFFSET)
                        coroutineScope.launch {
                            swipeOffset.snapTo(newValue)
                        }
                        if (newValue > HAPTIC_TRIGGER_OFFSET && newValue - dragAmount <= HAPTIC_TRIGGER_OFFSET) {
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
    ) {
        MessageBubbleContent(
            message = message,
            highlightAlpha = highlightAlpha.value,
            onBubblePositioned = onBubblePositioned,
            onNoteReplyClick = onNoteReplyClick
        )
    }
}

@Composable
fun MessageBubbleContent(
    message: MessageItem,
    highlightAlpha: Float = 0f,
    onBubblePositioned: ((Rect) -> Unit)? = null,
    onNoteReplyClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val bubbleColor = if (message.isSelf) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val alignment = if (message.isSelf) Alignment.CenterEnd else Alignment.CenterStart
    
    // Highlight efekti için alpha değerini hesapla (0.5 ile 1.0 arası)
    val finalAlpha = if (highlightAlpha > 0f) {
        0.5f + (highlightAlpha * 0.5f)
    } else {
        1f
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .onGloballyPositioned { coordinates ->
                    onBubblePositioned?.invoke(coordinates.boundsInRoot())
                },
            horizontalAlignment = if (message.isSelf) Alignment.End else Alignment.Start
        ) {
            if (message.replyToNote != null) {
                NoteReplyBubble(
                    noteRef = message.replyToNote,
                    isSelf = message.isSelf,
                    onClick = { onNoteReplyClick?.invoke(it) }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
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
                    .background(bubbleColor.copy(alpha = finalAlpha))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Column {
                    Text(
                        text = message.text,
                        color = if (message.isDeleted) TextSecondary else TextPrimary,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        fontStyle = if (message.isDeleted) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal
                    )
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
                contentDescription = stringResource(id = R.string.msg_status_sent),
                tint = TextSecondary,
                modifier = Modifier.size(14.dp)
            )
        }
        MessageStatus.DELIVERED -> {
            Icon(
                painter = painterResource(id = R.drawable.ic_forward_bold),
                contentDescription = stringResource(id = R.string.msg_status_delivered),
                tint = TextSecondary,
                modifier = Modifier.size(14.dp)
            )
        }
        MessageStatus.READ -> {
            Icon(
                painter = painterResource(id = R.drawable.ic_archive_book_outline),
                contentDescription = stringResource(id = R.string.msg_status_read),
                tint = AccentGreen,
                modifier = Modifier.size(14.dp)
            )
        }
        MessageStatus.FAILED -> {
            Icon(
                painter = painterResource(id = R.drawable.ic_cloud_cross_outline),
                contentDescription = stringResource(id = R.string.msg_status_failed),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun NoteReplyBubble(
    noteRef: com.linker.app.domain.model.NoteReference,
    isSelf: Boolean,
    onClick: (String) -> Unit
) {
    val isExpired = System.currentTimeMillis() > noteRef.expiresAt
    val bgColor = noteRef.backgroundColor?.let {
        try { Color(android.graphics.Color.parseColor(it)) } catch (e: Exception) { com.linker.app.presentation.theme.LightGray }
    } ?: com.linker.app.presentation.theme.LightGray
    
    val txtColor = noteRef.textColor?.let {
        try { Color(android.graphics.Color.parseColor(it)) } catch (e: Exception) { TextPrimary }
    } ?: TextPrimary

    Column(
        horizontalAlignment = if (isSelf) Alignment.End else Alignment.Start,
        modifier = Modifier
            .padding(bottom = 6.dp)
            .alpha(0.7f)
            .clickable {
                if (!isExpired) {
                    onClick(noteRef.noteId)
                }
            }
    ) {
        val replyText = if (isSelf) {
            "${noteRef.authorName} adlı kullanıcının notuna yanıt verdin"
        } else {
            "${noteRef.authorName} adlı kullanıcının notuna yanıt verdi"
        }
        
        Text(
            text = replyText,
            color = TextPrimary.copy(alpha = 0.8f),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(6.dp))
        
        Box(contentAlignment = Alignment.TopCenter) {
            LinkerAvatar(
                imageUrl = null, // Backend'den gelene kadar default
                size = 64.dp,
                storyState = StoryState.NONE,
                modifier = Modifier.padding(top = 20.dp)
            )
            
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(bgColor)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .widthIn(min = 40.dp, max = 120.dp)
            ) {
                val desc = when (noteRef.noteType) {
                    "MUSIC" -> "🎵 ${noteRef.musicArtistName} - ${noteRef.musicTrackName}"
                    "TEXT" -> noteRef.content ?: "Durum"
                    "COUNTDOWN" -> "⏳ ${noteRef.content}"
                    "LOCATION" -> "📍 ${noteRef.content}"
                    "GIF" -> "GIF"
                    else -> "Durum"
                }
                Text(
                    text = if (isExpired) "Bu içeriğe ulaşılamıyor" else desc,
                    color = txtColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}
