package com.linker.app.presentation.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linker.app.R
import com.linker.app.domain.model.User
import com.linker.app.presentation.components.LinkerAvatar
import com.linker.app.presentation.components.StoryState
import com.linker.app.presentation.theme.AccentGreen
import com.linker.app.presentation.theme.LightGray
import com.linker.app.presentation.theme.TextPrimary
import com.linker.app.presentation.theme.TextSecondary

@Composable
fun SharedMediaThumbnail(media: SharedMediaItem) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(LightGray)
    ) {
        Icon(
            painter = painterResource(
                id = when (media.mediaType) {
                    MediaType.VIDEO -> R.drawable.ic_play_add_outline
                    MediaType.GIF -> R.drawable.ic_toy_6_outline
                    MediaType.IMAGE -> R.drawable.ic_gallery_outline
                }
            ),
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.align(Alignment.Center).size(32.dp)
        )
    }
}

@Composable
fun SharedLinkItemRow(link: SharedLinkItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(LightGray),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_toy_6_outline),
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = link.title,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.chat_info_shared_by, link.senderName),
                color = TextSecondary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun ChatInfoOption(
    icon: Int,
    title: String,
    subtitle: String?,
    subtitleStyle: Boolean = false,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = title,
            tint = TextPrimary,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = if (subtitleStyle) TextPrimary else TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }
        Icon(
            painter = painterResource(id = R.drawable.ic_arrow_left_01_outline),
            contentDescription = "Go",
            tint = TextSecondary,
            modifier = Modifier.size(20.dp).rotate(180f)
        )
    }
}

@Composable
fun TabRowIconItem(icon: Int, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clickable { onClick() }.padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = null,
            tint = if (isSelected) Color.White else TextSecondary,
            modifier = Modifier.size(32.dp).padding(bottom = 8.dp)
        )
        Box(
            modifier = Modifier
                .height(2.dp)
                .width(48.dp)
                .background(if (isSelected) Color.White else Color.Transparent)
        )
    }
}
