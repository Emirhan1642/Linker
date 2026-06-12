package com.linker.app.presentation.screens.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.linker.app.presentation.components.LinkerAvatar
import com.linker.app.presentation.components.StoryState
import com.linker.app.presentation.theme.TextPrimary

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
            .padding(start = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onNavigateBack) {
            Icon(
                painter = painterResource(id = R.drawable.ic_arrow_left_01_outline),
                contentDescription = stringResource(id = R.string.action_back),
                tint = TextPrimary,
                modifier = Modifier.size(30.dp)
            )
        }

        Row(
            modifier = Modifier
                .weight(0.9f)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onNavigateToInfo() }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LinkerAvatar(
                imageUrl = recipientImageUrl,
                size = 36.dp,
                storyState = StoryState.NONE,
                onClick = {}
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = recipientName,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LinkerAvatar(
            imageUrl = recipientImageUrl,
            size = 120.dp,
            storyState = StoryState.NONE
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = recipientName,
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        recipientUsername?.takeIf { it.isNotBlank() }?.let { username ->
            Text(
                text = "@$username",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
