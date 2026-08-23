package com.linker.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linker.app.domain.model.Note
import com.linker.app.presentation.animation.bouncyClick
import com.linker.app.presentation.animation.MusicVisualizerView
import com.linker.app.presentation.theme.*
import coil3.request.crossfade
import java.util.concurrent.TimeUnit

@Composable
fun NoteCard(
    note: Note,
    onReply: (Note) -> Unit,
    onLike: (Note) -> Unit,
    onSubscribeCountdown: ((Note.Countdown) -> Unit)? = null
) {
    GlassBox(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .width(130.dp)
            .height(170.dp)
            .bouncyClick { onReply(note) }
            .padding(10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Author row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                LinkerAvatar(
                    imageUrl = note.author.profileImageUrl,
                    size = 26.dp,
                    storyState = StoryState.NONE
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = note.author.displayName,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
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
                            fontWeight = FontWeight.Medium,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    is Note.Location -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(LinkerPrimary.copy(alpha = 0.15f))
                                    .border(1.dp, LinkerPrimary.copy(alpha = 0.4f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = "Location",
                                    tint = LinkerPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = note.placeName,
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                    is Note.Countdown -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            var remainingStr by remember { mutableStateOf(getRemainingTime(note.countdownTargetTime)) }
                            LaunchedEffect(note.countdownTargetTime) {
                                while(true) {
                                    val rem = getRemainingTime(note.countdownTargetTime)
                                    remainingStr = rem
                                    if (rem == "00:00") break
                                    kotlinx.coroutines.delay(10_000)
                                }
                            }
                            PillBadge(
                                text = "⏳ $remainingStr",
                                accentColor = LinkerPrimary,
                                fontSize = 10
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = note.countdownTitle,
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    is Note.Music -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(contentAlignment = Alignment.Center) {
                                MusicVisualizerView(
                                    modifier = Modifier.size(width = 40.dp, height = 24.dp),
                                    barColor = AccentGreen
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = note.musicTrackName,
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = note.musicArtistName,
                                color = TextSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    is Note.Gif -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (note.gifUrl.isNotBlank()) {
                                coil3.compose.AsyncImage(
                                    model = coil3.request.ImageRequest.Builder(coil3.compose.LocalPlatformContext.current)
                                        .data(note.gifUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "GIF",
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, GlassCardBorder, RoundedCornerShape(12.dp))
                                )
                            }
                            if (note.content.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = note.content,
                                    color = TextPrimary,
                                    fontSize = 11.sp,
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
    if (diff <= 0) return "00:00"
    val days = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(diff)
    val hours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(diff) % 24
    val mins = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(diff) % 60
    return if (days > 0) "${days}g ${hours}s" else "${hours}s ${mins}dk"
}
