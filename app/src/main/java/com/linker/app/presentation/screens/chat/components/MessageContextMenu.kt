package com.linker.app.presentation.screens.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linker.app.R
import com.linker.app.presentation.screens.chat.MessageItem
import com.linker.app.presentation.screens.chat.MessageUiModel
import com.linker.app.presentation.theme.TextPrimary
import com.linker.app.presentation.theme.TextSecondary
import kotlin.math.roundToInt

/**
 * Context menu shown on long press of a message
 */
@Composable
fun MessageContextMenu(
    message: MessageUiModel,
    messageBounds: Rect?,
    screenWidth: Float,
    screenHeight: Float,
    quickReactions: List<String>,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onForward: () -> Unit,
    onInfo: () -> Unit,
    onDelete: (() -> Unit)?,
    onReaction: (String) -> Unit,
    onShowMoreReactions: () -> Unit,
    showEmojiPicker: Boolean,
    modifier: Modifier = Modifier
) {
    if (messageBounds == null) return

    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    val actions = if (message.isSelf) {
        listOf("Reply", "Copy", "Forward", "Message Info", "Delete")
    } else {
        listOf("Reply", "Copy", "Forward", "Message Info")
    }

    val paddingPx = with(density) { 8.dp.toPx() }
    val menuWidthPx = with(density) { 180.dp.toPx() }
    val menuHeightPx = with(density) { (actions.size * 48).dp.toPx() + 16.dp.toPx() }
    val emojiBarWidthPx = with(density) { 280.dp.toPx() }
    val emojiBarHeightPx = with(density) { 52.dp.toPx() }

    // Calculate menu position
    val menuYSide = messageBounds.top + messageBounds.height / 2f - menuHeightPx / 2f
    val sideXUnclamped = if (message.isSelf) {
        messageBounds.left - menuWidthPx - paddingPx
    } else {
        messageBounds.right + paddingPx
    }
    val sideX = sideXUnclamped.coerceIn(paddingPx, screenWidth - menuWidthPx - paddingPx)

    val belowY = messageBounds.bottom + paddingPx
    val aboveY = messageBounds.top - menuHeightPx - paddingPx
    val canBelow = belowY + menuHeightPx <= screenHeight - paddingPx
    val canAbove = aboveY >= paddingPx

    val menuY = when {
        canBelow -> belowY
        canAbove -> aboveY
        else -> menuYSide.coerceIn(paddingPx, screenHeight - menuHeightPx - paddingPx)
    }
    val menuX = when {
        canBelow || canAbove -> (messageBounds.left + (messageBounds.width - menuWidthPx) / 2f)
            .coerceIn(paddingPx, screenWidth - menuWidthPx - paddingPx)
        else -> sideX
    }

    // Calculate emoji bar position
    val emojiYAbove = messageBounds.top - emojiBarHeightPx - paddingPx
    val emojiYBelow = messageBounds.bottom + paddingPx
    var emojiY = if (menuY == belowY) emojiYAbove else if (menuY == aboveY) emojiYBelow else {
        if (emojiYAbove < paddingPx) emojiYBelow else emojiYAbove
    }
    val emojiXBase = if (message.isSelf) messageBounds.right - emojiBarWidthPx else messageBounds.left
    val emojiX = emojiXBase.coerceIn(paddingPx, screenWidth - emojiBarWidthPx - paddingPx)

    // Check overlap
    val menuRect = Rect(menuX, menuY, menuX + menuWidthPx, menuY + menuHeightPx)
    val emojiRect = Rect(emojiX, emojiY, emojiX + emojiBarWidthPx, emojiY + emojiBarHeightPx)
    val overlaps = !(emojiRect.right < menuRect.left || emojiRect.left > menuRect.right ||
            emojiRect.bottom < menuRect.top || emojiRect.top > menuRect.bottom)
    if (overlaps) {
        emojiY = if (emojiY == emojiYAbove) emojiYBelow else emojiYAbove
    }
    emojiY = emojiY.coerceIn(paddingPx, screenHeight - emojiBarHeightPx - paddingPx)

    // Dismiss overlay
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0x77000000))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { onDismiss() }
    )

    // Highlighted message
    Box(
        modifier = Modifier.offset {
            IntOffset(messageBounds.left.roundToInt(), messageBounds.top.roundToInt())
        }
    ) {
        MessageBubbleContent(
            message = MessageItem(
                text = message.content ?: "",
                isSelf = message.isSelf,
            )
        )
    }

    // Emoji bar
        QuickReactionsBar(
            reactions = quickReactions,
            onReactionClick = { emoji ->
                onReaction(emoji)
                onDismiss()
            },
            onShowMoreClick = onShowMoreReactions,
            modifier = Modifier.offset {
                IntOffset(emojiX.roundToInt(), emojiY.roundToInt())
            }
        )

    // Action menu
    Column(
        modifier = Modifier
            .offset { IntOffset(menuX.roundToInt(), menuY.roundToInt()) }
            .width(with(density) { menuWidthPx.toDp() })
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1F1F23))
            .padding(vertical = 8.dp)
    ) {
        actions.forEach { action ->
            val isDelete = action == "Delete"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        when (action) {
                            "Reply" -> onReply()
                            "Copy" -> onCopy()
                            "Forward" -> onForward()
                            "Message Info" -> onInfo()
                            "Delete" -> onDelete?.invoke()
                        }
                        onDismiss()
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(
                        id = when (action) {
                            "Reply" -> R.drawable.ic_export_circle_01_outline
                            "Copy" -> R.drawable.ic_archive_book_outline
                            "Forward" -> R.drawable.ic_forward_outline
                            "Message Info" -> R.drawable.ic_search_status_1_outline
                            "Delete" -> R.drawable.ic_cloud_cross_bold
                            else -> R.drawable.ic_arrow_down_02_bold
                        }
                    ),
                    contentDescription = action,
                    tint = if (isDelete) Color(0xFFFF4B4B) else TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = action,
                    color = if (isDelete) Color(0xFFFF4B4B) else TextPrimary,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
fun QuickReactionsBar(
    reactions: List<String>,
    onReactionClick: (String) -> Unit,
    onShowMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xFF1F1F23))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        reactions.forEach { emoji ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onReactionClick(emoji) }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = emoji,
                    fontSize = 28.sp
                )
            }
        }

        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(Color(0xFF2E2E32))
                .clickable { onShowMoreClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+",
                color = TextSecondary,
                fontSize = 20.sp
            )
        }
    }
}
