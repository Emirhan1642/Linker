package com.linker.app.presentation.screens.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linker.app.R
import com.linker.app.presentation.animation.bouncyClick
import com.linker.app.presentation.components.AmbientGlow
import com.linker.app.presentation.components.GlassBox
import com.linker.app.presentation.components.GlassIconButton
import com.linker.app.presentation.components.LinkerAvatar
import com.linker.app.presentation.components.StoryState
import com.linker.app.presentation.theme.*

/**
 * Chat header with back button, avatar, and recipient info
 */
@Composable
fun ChatHeader(
    recipientName: String,
    recipientUsername: String?,
    recipientImageUrl: String?,
    onNavigateBack: () -> Unit,
    onNavigateToInfo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassIconButton(
            iconRes = R.drawable.ic_arrow_left_01_outline,
            onClick = onNavigateBack,
            size = 44.dp
        )

        Spacer(modifier = Modifier.width(10.dp))

        GlassBox(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .weight(1f)
                .bouncyClick { onNavigateToInfo() }
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                LinkerAvatar(
                    imageUrl = recipientImageUrl,
                    size = 38.dp,
                    storyState = StoryState.NONE
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = recipientName,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    recipientUsername?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = "@$it",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Chat profile header shown at top of message list
 */
@Composable
fun ChatProfileHeader(
    recipientName: String,
    recipientUsername: String?,
    recipientImageUrl: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LinkerAvatar(
                imageUrl = recipientImageUrl,
                size = 100.dp,
                storyState = StoryState.NONE
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = recipientName,
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            recipientUsername?.takeIf { it.isNotBlank() }?.let { username ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "@$username",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
        }
    }
}
