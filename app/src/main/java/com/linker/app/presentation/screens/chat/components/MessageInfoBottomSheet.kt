package com.linker.app.presentation.screens.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linker.app.R
import com.linker.app.domain.model.MessageDeliveryStatus
import com.linker.app.presentation.components.LinkerAvatar
import com.linker.app.presentation.components.StoryState
import com.linker.app.presentation.screens.chat.MessageInfoState
import com.linker.app.presentation.screens.chat.ReactionUserInfo
import com.linker.app.presentation.screens.chat.ReadReceiptInfo
import com.linker.app.presentation.screens.chat.ReplyInfo
import com.linker.app.presentation.screens.chat.ReplyPreview
import com.linker.app.presentation.theme.AccentGreen
import com.linker.app.presentation.theme.InfoBlue
import com.linker.app.presentation.theme.TextPrimary
import com.linker.app.presentation.theme.TextSecondary

/**
 * Message info bottom sheet showing delivery status, reactions, and read receipts
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageInfoBottomSheet(
    state: MessageInfoState,
    isGroupChat: Boolean,
    onDismiss: () -> Unit,
    onNavigateToUserProfile: (String) -> Unit,
    onScrollToReply: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(id = R.string.title_message_info),
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )

            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                // Message preview
                MessagePreviewSection(state = state)

                // Reactions summary
                if (state.reactions.isNotEmpty()) {
                    ReactionSummarySection(
                        reactions = state.reactions,
                        onEmojiClick = { }
                    )
                }

                // Delivery info
                DeliveryInfoSection(
                    state = state,
                    isGroupChat = isGroupChat,
                    onNavigateToUserProfile = onNavigateToUserProfile
                )

                // Replies section
                if (state.replies.isNotEmpty()) {
                    RepliesSection(
                        replies = state.replies,
                        onReplyClick = { replyId ->
                            onScrollToReply?.invoke(replyId)
                            onDismiss()
                        },
                        onNavigateToUserProfile = onNavigateToUserProfile
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun MessagePreviewSection(state: MessageInfoState) {
    Column {
        val replyPreview = state.replyPreview
        if (replyPreview != null) {
            ReplyPreviewHologram(
                preview = replyPreview,
                alignEnd = state.isSelf,
                alpha = 0.7f
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Message bubble preview
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = if (state.isSelf) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            MessageBubbleContent(
                message = com.linker.app.presentation.screens.chat.MessageItem(
                    text = state.content,
                    isSelf = state.isSelf,
                )
            )
        }
    }
}

@Composable
fun ReplyPreviewHologram(
    preview: ReplyPreview, 
    alignEnd: Boolean, 
    alpha: Float,
    onClick: (() -> Unit)? = null
) {
    val contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart
    val backgroundColor = if (preview.isSelf) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = contentAlignment
    ){
        Text(
            text = stringResource(id = R.string.msg_replied_to, preview.senderName, preview.senderName),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            fontSize = 10.sp,
            maxLines = 1,
        )
    }

    Spacer(modifier = Modifier.height(2.dp))

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = contentAlignment
    ) {
        Row(
            modifier = Modifier
                .alpha(alpha)
                .clip(RoundedCornerShape(12.dp))
                .background(backgroundColor.copy(alpha = 0.8f))
                .then(
                    if (onClick != null) {
                        Modifier.clickable { onClick() }
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(8.dp))
        ) {
            Column(modifier = Modifier.widthIn(max = 240.dp)) {
                Text(
                    text = preview.previewText,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun DeliveryInfoSection(
    state: MessageInfoState,
    isGroupChat: Boolean,
    onNavigateToUserProfile: (String) -> Unit
) {
    // Sent time
    InfoRow(
        label = stringResource(id = R.string.status_sent),
        value = formatRelativeTime(state.sentAt),
        iconRes = R.drawable.ic_forward_outline
    )

    // Delivered info
    if (state.deliveredReceipts.isNotEmpty()) {
        Text(
            text = stringResource(id = R.string.delivered_to),
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp)
        )
        state.deliveredReceipts.forEach { dr ->
            ReceiptParticipantRow(
                name = dr.userName,
                timeLabel = formatRelativeTime(dr.atMillis),
                avatarUrl = dr.avatarUrl,
                onAvatarClick = {
                    if (dr.userId.isNotBlank()) onNavigateToUserProfile(dr.userId)
                }
            )
        }
    } else if (state.deliveredAt != null) {
        InfoRow(
            label = stringResource(id = R.string.status_delivered),
            value = formatRelativeTime(state.deliveredAt),
            iconRes = R.drawable.ic_box_search_outline
        )
    }

    // Read info
    if (state.readAt != null) {
        if (!isGroupChat) {
            // Direct chat: show simple "Seen" info
            InfoRow(
                label = stringResource(id = R.string.status_seen),
                value = formatRelativeTime(state.readAt),
                iconRes = R.drawable.ic_forward_bold
            )
        }
    }

    // Failed info
    if (state.failedAt != null) {
        InfoRow(
            label = stringResource(id = R.string.status_failed),
            value = formatRelativeTime(state.failedAt),
            iconRes = R.drawable.ic_cloud_cross_outline
        )
    }

    // Read receipts for group chat
    if (isGroupChat && state.readReceipts.isNotEmpty()) {
        Text(
            text = stringResource(id = R.string.seen_by),
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp)
        )
        state.readReceipts.forEach { rr ->
            ReceiptParticipantRow(
                name = rr.userName,
                timeLabel = formatRelativeTime(rr.readAt),
                avatarUrl = rr.avatarUrl,
                onAvatarClick = {
                    if (rr.userId.isNotBlank()) onNavigateToUserProfile(rr.userId)
                }
            )
        }
    }
}

@Composable
fun RepliesSection(
    replies: List<ReplyInfo>,
    onReplyClick: (String) -> Unit,
    onNavigateToUserProfile: (String) -> Unit
) {
    Text(
        text = stringResource(id = R.string.replies),
        color = TextPrimary,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp)
    )

    replies.forEach { reply ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onReplyClick(reply.messageId) }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LinkerAvatar(
                imageUrl = reply.avatarUrl,
                size = 40.dp,
                storyState = StoryState.NONE,
                onClick = {
                    if (reply.senderId.isNotBlank()) {
                        onNavigateToUserProfile(reply.senderId)
                    }
                }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reply.preview,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
fun ReceiptParticipantRow(
    name: String,
    timeLabel: String,
    avatarUrl: String?,
    onAvatarClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LinkerAvatar(
            imageUrl = avatarUrl,
            size = 32.dp,
            storyState = StoryState.NONE,
            onClick = onAvatarClick
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                color = TextPrimary,
                fontSize = 14.sp
            )
        }
        Text(
            text = timeLabel,
            color = TextSecondary,
            fontSize = 12.sp
        )
    }
}

@Composable
fun InfoRow(label: String, value: String, iconRes: Int? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = "$label: ",
            color = TextSecondary,
            fontSize = 13.sp
        )
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 13.sp
        )
    }
}

@Composable
fun ReactionSummarySection(
    reactions: List<ReactionUserInfo>,
    onEmojiClick: (String) -> Unit
) {
    val emojiCounts: Map<String, Int> = reactions.groupingBy { it.emoji }.eachCount()
    val summary: List<String> = emojiCounts.entries.sortedByDescending { entry: Map.Entry<String, Int> -> entry.value }.map { entry: Map.Entry<String, Int> ->
        if (entry.value > 1) "${entry.key} ${entry.value}" else entry.key
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        summary.forEach { emojiText: String ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onEmojiClick(emojiText) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = emojiText,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun formatRelativeTime(timestamp: Long?): String {
    if (timestamp == null) return "-"
    return com.linker.app.core.util.formatRelativeTime(timestamp).replaceFirstChar { 
        if (it.isLowerCase()) it.titlecase() else it.toString() 
    }
}
