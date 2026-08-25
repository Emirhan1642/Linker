package com.linker.app.presentation.screens.story

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linker.app.domain.repository.StoryViewer
import com.linker.app.presentation.components.LinkerAvatar
import com.linker.app.presentation.components.StoryState
import com.linker.app.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryViewersBottomSheet(
    viewers: List<StoryViewer>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onUserClick: (userId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkGray,
        dragHandle = { BottomSheetDefaults.DragHandle(color = TextSecondary) }
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
                .padding(horizontal = 20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Görüntüleyenler",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "(${viewers.size})",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Kapat",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            HorizontalDivider(
                color = Color.White.copy(alpha = 0.08f),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (isLoading && viewers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = LinkerPrimary,
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 2.5.dp
                    )
                }
            } else if (viewers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("👁️", fontSize = 32.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Henüz görüntüleyen yok",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(viewers, key = { it.userId }) { viewer ->
                        StoryViewerItem(
                            viewer = viewer,
                            onClick = { onUserClick(viewer.userId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StoryViewerItem(
    viewer: StoryViewer,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp)
    ) {
        LinkerAvatar(
            imageUrl = viewer.avatarUrl,
            size = 44.dp,
            storyState = StoryState.NONE
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = viewer.username,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            val minutesAgo = ((System.currentTimeMillis() - viewer.viewedAt) / 60_000).toInt()
            val timeText = when {
                minutesAgo < 1 -> "Az önce"
                minutesAgo < 60 -> "$minutesAgo dk önce"
                minutesAgo < 1440 -> "${minutesAgo / 60} sa önce"
                else -> "${minutesAgo / 1440} g önce"
            }
            Text(
                text = timeText,
                color = TextSecondary,
                fontSize = 12.sp
            )
        }

        if (viewer.hasLiked) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = "Beğendi",
                tint = ErrorRed,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
