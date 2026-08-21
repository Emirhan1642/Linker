package com.linker.app.presentation.screens.note

import android.app.Activity
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.linker.app.core.util.AudioPlayerManager
import com.linker.app.core.util.SpotifyAppRemoteManager
import com.linker.app.core.util.SpotifyAuthManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * TrackClipPickerScreen
 *
 * Shown between SpotifySearch and NoteEditor.
 * Lets the user preview the track and drag start/end handles to pick a 30-second clip.
 *
 * - If [previewUrl] is set (free track): plays via AudioPlayerManager
 * - If [previewUrl] is empty (premium): plays via SpotifyAppRemote
 *
 * After confirming, calls [onClipConfirmed] with (startMs, endMs).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackClipPickerScreen(
    trackId: String,
    trackName: String,
    artistName: String,
    albumArtUrl: String,
    previewUrl: String,
    trackDurationMs: Long = 0L,
    isExplicit: Boolean = false,
    onNavigateBack: () -> Unit,
    onClipConfirmed: (startMs: Long, endMs: Long) -> Unit,
    audioPlayerManager: AudioPlayerManager = hiltViewModel<SpotifySearchViewModel>().audioPlayerManager,
    spotifyAppRemoteManager: SpotifyAppRemoteManager = hiltViewModel<SpotifySearchViewModel>().spotifyAppRemoteManager,
    spotifyAuthManager: SpotifyAuthManager = hiltViewModel<SpotifySearchViewModel>().spotifyAuthManager,
    viewModel: SpotifySearchViewModel = hiltViewModel<SpotifySearchViewModel>()
) {
    val context = LocalContext.current

    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(trackId) {
        viewModel.fetchLyrics(trackName, artistName)
    }

    LaunchedEffect(Unit) {
        // Stop any dashboard preview that might still be playing
        audioPlayerManager.stop()
    }

    val isPremium by spotifyAuthManager.isPremium.collectAsState()
    
    // If we are NOT premium and we have a previewUrl, we are forced to use the preview
    val isPreviewMode = (isPremium == false && previewUrl.isNotEmpty())
    
    val isPlaying = if (isPreviewMode) {
        audioPlayerManager.isPlaying.collectAsState().value
    } else {
        spotifyAppRemoteManager.isPlaying.collectAsState().value
    }

    val positionMs = if (isPreviewMode) {
        audioPlayerManager.currentPositionMs.collectAsState().value
    } else {
        spotifyAppRemoteManager.currentPositionMs.collectAsState().value
    }
    
    val isConnected by spotifyAppRemoteManager.isConnected.collectAsState()
    val rawDurationMs by spotifyAppRemoteManager.durationMs.collectAsState()

    // Always prefer the official trackDurationMs if available, because rawDurationMs might be 0 until Spotify connects.
    val durationSec = if (isPreviewMode) {
        30f
    } else if (trackDurationMs > 0) {
        trackDurationMs / 1000f
    } else if (rawDurationMs > 0) {
        rawDurationMs / 1000f
    } else {
        30f
    }

    // Clip duration state (adjustable 1s - 30s)
    var showDurationPicker by remember { mutableStateOf(false) }
    var selectedDurationSec by remember { mutableIntStateOf(minOf(durationSec.toInt(), 30)) }
    val clipDuration = minOf(durationSec, selectedDurationSec.toFloat())
    
    // Clip start state
    var clipStart by remember(durationSec) { mutableFloatStateOf(0f) }
    // Ensure clipStart remains valid when duration changes
    clipStart = clipStart.coerceIn(0f, maxOf(0f, durationSec - clipDuration))
    
    var clipRange = clipStart..(clipStart + clipDuration)
    val clipStartMs = (clipStart * 1000).toLong()
    val clipEndMs = ((clipStart + clipDuration) * 1000).toLong()

    // Drag state
    var isDragging by remember { mutableStateOf(false) }

    // Playhead progress 0..1 within clip
    val playheadProgress = if (selectedDurationSec > 0 && positionMs >= clipStartMs) {
        ((positionMs - clipStartMs).coerceIn(0, clipEndMs - clipStartMs).toFloat() /
                (clipEndMs - clipStartMs).toFloat())
    } else 0f

    // Seek automatically when clipStartMs or clipEndMs changes, but NOT while dragging
    // We seek even when paused so the playhead accurately snaps to the new beginning!
    LaunchedEffect(clipStartMs, clipEndMs, isDragging) {
        if (!isDragging) {
            if (isPreviewMode) {
                // mediaPlayer.seekTo takes milliseconds, but in AudioPlayerManager we don't expose manual seek unless it's playing.
                // However playPreview will start from the correct startMs when play is clicked.
            } else {
                spotifyAppRemoteManager.seekTo(clipStartMs, clipEndMs)
            }
        }
    }

    // Waveform bars — randomly generated once per track (stable)
    val waveformBars = remember(trackId) {
        val rng = Random(trackId.hashCode())
        (0 until 60).map { 0.2f + rng.nextFloat() * 0.8f }
    }

    // Cleanup on exit
    DisposableEffect(Unit) {
        onDispose {
            audioPlayerManager.stop()
            spotifyAppRemoteManager.pauseAndDisconnect()
        }
    }

    fun togglePlayback() {
        // If we are not connected, we MUST pass clipStartMs so it starts from the right place instead of 0:00.
        // If the playhead is outside the clip boundaries, restart from the beginning of the clip.
        val startMs = if ((!isConnected && !isPreviewMode) || positionMs < clipStartMs || positionMs >= clipEndMs - 200) clipStartMs else null

        if (isPreviewMode) {
            if (isPlaying) audioPlayerManager.pause()
            else audioPlayerManager.playPreview(previewUrl, startMs ?: positionMs, clipEndMs)
        } else {
            if (isPlaying) spotifyAppRemoteManager.pause()
            else {
                if (isConnected) {
                    spotifyAppRemoteManager.playTrack(trackId, startMs, clipEndMs)
                } else {
                    val hasToken = spotifyAuthManager.accessToken.value != null
                    if (hasToken) {
                        spotifyAppRemoteManager.connect(
                            context = context as android.app.Activity,
                            clientId = com.linker.app.BuildConfig.SPOTIFY_CLIENT_ID,
                            onConnected = {
                                spotifyAppRemoteManager.playTrack(
                                    trackId,
                                    startMs,
                                    clipEndMs
                                )
                            },
                            onError = {}
                        )
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0A0A0A), Color(0xFF121212), Color(0xFF1A1A2E))
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ── Top Bar ──────────────────────────────────────────────────────
            TopAppBar(
                title = {
                    Text(
                        "Kırpma Seç",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Geri",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Album Art ────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF2C2C2C))
                    .border(
                        width = if (isPlaying) 2.dp else 0.dp,
                        brush = Brush.linearGradient(listOf(Color(0xFF1DB954), Color(0xFF1565C0))),
                        shape = RoundedCornerShape(20.dp)
                    )
            ) {
                if (albumArtUrl.isNotBlank()) {
                    AsyncImage(
                        model = albumArtUrl,
                        contentDescription = "Albüm Kapağı",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                // Pulsing overlay when playing
                if (isPlaying) {
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0f, targetValue = 0.3f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ), label = "alpha"
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Color(0xFF1DB954).copy(alpha = alpha),
                                RoundedCornerShape(20.dp)
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Track Info ───────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                Text(
                    text = trackName,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isExplicit) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF888888).copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "E",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = artistName,
                color = Color(0xFFAAAAAA),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ── Unified Scrolling Waveform & Clipper ─────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .padding(horizontal = 24.dp)
            ) {
                ScrollingWaveformClipper(
                    durationSec = durationSec,
                    clipDurationSec = clipDuration,
                    clipStart = clipStart,
                    onClipStartChange = { clipStart = it },
                    onDragStateChange = { isDragging = it },
                    playheadProgress = if (isPlaying || positionMs > 0) playheadProgress else -1f,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            // ── Time labels & Duration Button ────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 34.dp), // Adjust for the slider padding
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatSeconds(clipRange.start.toInt()),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                
                // Duration Picker Button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .border(1.dp, Color(0xFF444444), androidx.compose.foundation.shape.CircleShape)
                        .clickable { showDurationPicker = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = selectedDurationSec.toString(),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = formatSeconds(clipRange.endInclusive.toInt()),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            
            if (showDurationPicker) {
                DurationPickerDialog(
                    initialValue = selectedDurationSec,
                    maxDuration = minOf(durationSec.toInt(), 30),
                    onDismiss = { showDurationPicker = false },
                    onConfirm = { 
                        selectedDurationSec = it
                        showDurationPicker = false
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Synced Lyrics Toggle ─────────────────────────────────────────
            var showLyrics by remember { mutableStateOf(false) }
            val lyricsWeight by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (showLyrics) 1f else 0f
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                TextButton(
                    onClick = { showLyrics = !showLyrics },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF1DB954))
                ) {
                    Icon(
                        imageVector = if (showLyrics) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (showLyrics) "Şarkı Sözlerini Gizle" else "Şarkı Sözlerini Göster")
                }

                if (lyricsWeight > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(lyricsWeight)
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (uiState.isLoadingLyrics) {
                            CircularProgressIndicator(color = Color(0xFF1DB954))
                        } else if (uiState.lyricsError != null) {
                            Text(text = uiState.lyricsError ?: "", color = Color(0xFF888888), fontSize = 14.sp)
                        } else if (uiState.lyrics.isNotEmpty()) {
                            com.linker.app.presentation.components.SyncedLyricsView(
                                lyrics = uiState.lyrics,
                                currentPositionMs = positionMs,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(text = "Şarkı sözü bulunamadı", color = Color(0xFF888888), fontSize = 14.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Play & Confirm Controls ──────────────────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isPreviewMode) "Ses Kaynağı: Önizleme" else "Ses Kaynağı: Spotify Premium",
                    color = Color(0xFF888888),
                    fontSize = 12.sp
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Play/Pause Button
                    IconButton(
                        onClick = { togglePlayback() },
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                brush = Brush.linearGradient(listOf(Color(0xFF1DB954), Color(0xFF17A349))),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Duraklat" else "Oynat",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Confirm Button
                    Button(
                        onClick = {
                            if (isPlaying) {
                                if (isPreviewMode) audioPlayerManager.stop()
                                else spotifyAppRemoteManager.pauseAndDisconnect()
                            }
                            onClipConfirmed(clipStartMs, clipEndMs)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1DB954)
                        )
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Bu Kısmı Seç",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ── Waveform Composable ──────────────────────────────────────────────────────

@Composable
private fun ScrollingWaveformClipper(
    durationSec: Float,
    clipDurationSec: Float,
    clipStart: Float,
    onClipStartChange: (Float) -> Unit,
    onDragStateChange: (Boolean) -> Unit,
    playheadProgress: Float, // 0..1 within clip, -1 = hidden
    modifier: Modifier = Modifier
) {
    val pxPerSec = with(androidx.compose.ui.platform.LocalDensity.current) { 
        (if (clipDurationSec > 0) 200.dp.toPx() / clipDurationSec else 10f) 
    }
    
    val currentClipStart by androidx.compose.runtime.rememberUpdatedState(clipStart)
    
    val scrollState = androidx.compose.foundation.gestures.rememberScrollableState { dragAmount ->
        val newStart = (currentClipStart - dragAmount / pxPerSec).coerceIn(0f, maxOf(0f, durationSec - clipDurationSec))
        val consumedStart = currentClipStart - newStart
        onClipStartChange(newStart)
        consumedStart * pxPerSec
    }
    
    // Notify about drag/fling state changes so audio player pauses seeking until the wheel completely stops
    androidx.compose.runtime.LaunchedEffect(scrollState.isScrollInProgress) {
        onDragStateChange(scrollState.isScrollInProgress)
    }
    
    androidx.compose.foundation.Canvas(
        modifier = modifier.scrollable(
            state = scrollState,
            orientation = androidx.compose.foundation.gestures.Orientation.Horizontal
        )
    ) {
        val center = size.width / 2
        val boxWidth = pxPerSec * clipDurationSec
        val boxStart = center - boxWidth / 2
        val boxEnd = center + boxWidth / 2
        
        val waveformStartX = boxStart - (clipStart * pxPerSec)
        
        // 1. Draw artificial waveform bars
        for (sec in 0..durationSec.toInt()) {
            val x = waveformStartX + sec * pxPerSec
            // Only draw visible lines
            if (x < -10f || x > size.width + 10f) continue
            
            val isLong = sec % 5 == 0
            val h = if (isLong) size.height * 0.7f else size.height * 0.3f
            val color = if (x in boxStart..boxEnd) Color.White else Color(0xFF444444)
            
            drawLine(
                color = color,
                start = Offset(x, (size.height - h) / 2),
                end = Offset(x, (size.height + h) / 2),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        
        // 2. Draw stationary glowing box
        val strokeWidth = 3.dp.toPx()
        val cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
        val gradient = Brush.linearGradient(
            colors = listOf(Color(0xFFFFEA00), Color(0xFFFF1744), Color(0xFFD500F9))
        )
        
        drawRoundRect(
            brush = gradient,
            topLeft = Offset(boxStart, 0f),
            size = Size(boxWidth, size.height),
            cornerRadius = cornerRadius,
            style = Stroke(width = strokeWidth)
        )
        
        drawRoundRect(
            color = Color.White.copy(alpha = 0.1f),
            topLeft = Offset(boxStart, 0f),
            size = Size(boxWidth, size.height),
            cornerRadius = cornerRadius
        )
        
        // 3. Draw playhead inside the box
        if (playheadProgress in 0f..1f) {
            val px = boxStart + playheadProgress * boxWidth
            drawLine(
                color = Color.White,
                start = Offset(px, -4.dp.toPx()),
                end = Offset(px, size.height + 4.dp.toPx()),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun formatSeconds(totalSec: Int): String {
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}
