package com.linker.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linker.app.domain.model.Note
import com.linker.app.presentation.theme.DarkGray
import com.linker.app.presentation.theme.TextPrimary
import com.linker.app.presentation.theme.TextSecondary
import androidx.compose.ui.draw.clip
import coil3.request.crossfade
import java.util.concurrent.TimeUnit

@Composable
fun NoteCard(
    note: Note,
    onReply: (Note) -> Unit,
    onLike: (Note) -> Unit,
    onSubscribeCountdown: ((Note.Countdown) -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .width(120.dp)
            .height(160.dp)
            .background(DarkGray, shape = MaterialTheme.shapes.medium)
            .clickable { onReply(note) }
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Author row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                coil3.compose.AsyncImage(
                    model = note.author.profileImageUrl?.takeIf { it.isNotBlank() } ?: "https://ui-avatars.com/api/?name=${note.author.displayName}&background=random",
                    contentDescription = "Profile Picture",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .size(24.dp)
                        .background(Color.Gray, shape = CircleShape)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = note.author.displayName,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Note Content based on type
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                when (note) {
                    is Note.Text -> {
                        Text(
                            text = note.content,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    is Note.Location -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = "Location",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = note.placeName,
                                color = TextPrimary,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                    is Note.Countdown -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = "Timer",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = note.countdownTitle,
                                color = TextPrimary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            var remainingStr by remember { mutableStateOf(getRemainingTime(note.countdownTargetTime)) }
                            LaunchedEffect(note.countdownTargetTime) {
                                while(true) {
                                    remainingStr = getRemainingTime(note.countdownTargetTime)
                                    kotlinx.coroutines.delay(1000)
                                }
                            }
                            Text(
                                text = remainingStr,
                                color = TextSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }
                    is Note.Music -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Music",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = note.musicTrackName,
                                color = TextPrimary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = note.musicArtistName,
                                color = TextSecondary,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    is Note.Gif -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color.DarkGray, shape = MaterialTheme.shapes.small),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "GIF", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            if (note.gifUrl.isNotBlank()) {
                                coil3.compose.AsyncImage(
                                    model = coil3.request.ImageRequest.Builder(coil3.compose.LocalPlatformContext.current)
                                        .data(note.gifUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "GIF",
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(MaterialTheme.shapes.small)
                                )
                            }
                            if (note.content.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = note.content,
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }

            }
        }
    }
}

private fun getRemainingTime(targetTime: Long): String {
    val diff = targetTime - System.currentTimeMillis()
    if (diff <= 0) return "Süre doldu"
    val days = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(diff)
    val hours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(diff) % 24
    val mins = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(diff) % 60
    return if (days > 0) "${days}g ${hours}s" else "${hours}s ${mins}dk"
}
