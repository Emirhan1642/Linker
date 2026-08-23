package com.linker.app.presentation.screens.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linker.app.R
import com.linker.app.presentation.animation.bouncyClick
import com.linker.app.presentation.components.GlassBox
import com.linker.app.presentation.screens.chat.ReplyPreview
import com.linker.app.presentation.theme.*

/**
 * Chat input bar with reply preview and send button
 */
@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    replyPreview: ReplyPreview?,
    onCancelReply: () -> Unit,
    onSend: () -> Unit,
    isSending: Boolean,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkGrayTransparent)
            .border(1.dp, GlassCardBorder)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .navigationBarsPadding()
            .imePadding()
    ) {
        // Reply preview
        if (replyPreview != null) {
            ReplyPreviewBar(
                preview = replyPreview,
                onCancel = onCancelReply
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Text input
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(DarkGray)
                    .border(1.dp, GlassCardBorder, RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    cursorBrush = SolidColor(LinkerPrimary),
                    textStyle = TextStyle(
                        color = TextPrimary,
                        fontSize = 15.sp
                    ),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (text.isEmpty()) {
                                Text(
                                    text = stringResource(id = R.string.hint_message),
                                    color = TextHint,
                                    fontSize = 15.sp
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }

            // Send button
            val hasText = text.isNotBlank()
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(
                        if (hasText && !isSending) Brush.horizontalGradient(LinkerBrandGradient)
                        else Brush.horizontalGradient(listOf(DarkGray, LightGray))
                    )
                    .bouncyClick(enabled = hasText && !isSending, onClick = onSend),
                contentAlignment = Alignment.Center
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_ai_send_message_outline),
                        contentDescription = stringResource(id = R.string.action_send),
                        tint = if (hasText) Color.White else TextHint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ReplyPreviewBar(
    preview: ReplyPreview,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassBox(
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Reply indicator line
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (preview.isSelf) GradientPurple else GradientBlue)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = preview.senderName,
                    color = if (preview.isSelf) GradientPurple else GradientBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = preview.previewText,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1
                )
            }

            IconButton(
                onClick = onCancel,
                modifier = Modifier.size(28.dp).bouncyClick { onCancel() }
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_close_circle_outline),
                    contentDescription = stringResource(id = R.string.action_cancel_reply),
                    tint = TextHint,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
