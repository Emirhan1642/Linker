package com.linker.app.presentation.screens.note

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.linker.app.presentation.theme.Black
import com.linker.app.presentation.theme.DarkGray
import com.linker.app.presentation.theme.TextPrimary
import com.linker.app.presentation.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    albumId: String,
    viewModel: AlbumDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onTrackSelected: (SpotifyTrack) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val isPlaying by viewModel.audioPlayerManager.isPlaying.collectAsState()
    val isRemotePlaying by viewModel.spotifyAppRemoteManager.isPlaying.collectAsState()
    val currentRemoteTrackId by viewModel.currentRemoteTrackId.collectAsState()
    val loadingPreviewTrackId by viewModel.loadingPreviewTrackId.collectAsState()
    val playingPreviewTrackId = remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current as Activity

    val handleTrackSelected: (SpotifyTrack) -> Unit = { track ->
        viewModel.saveRecentTrack(track)
        onTrackSelected(track)
    }

    val playPreviewTrack: (SpotifyTrack) -> Unit = { track ->
        viewModel.saveRecentTrack(track)
        playingPreviewTrackId.value = track.id
        viewModel.playTrack(
            context = context,
            track = track,
            onAuthRequired = { viewModel.spotifyAuthManager.openLoginInBrowser(context) },
            onError = { msg -> android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show() }
        )
    }

    LaunchedEffect(albumId) {
        viewModel.fetchAlbumProfile(albumId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Albüm", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Geri",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.background(Color.Transparent)
            )
        },
        containerColor = Black
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (uiState.error != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = uiState.error ?: "", color = Color.Red, modifier = Modifier.padding(16.dp))
            }
        } else if (uiState.profile != null) {
            val profile = uiState.profile ?: return@Scaffold
            val album = profile.album
            
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    // Album Header
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                    ) {
                        // Blurred background
                        if (album.imageUrl != null) {
                            coil3.compose.AsyncImage(
                                model = album.imageUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .blur(50.dp)
                                    .background(Color.Black.copy(alpha = 0.5f)),
                                contentScale = ContentScale.Crop
                            )
                        }
                        
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            coil3.compose.AsyncImage(
                                model = album.imageUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(160.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = album.name,
                                color = TextPrimary,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            val artistNames = profile.artists.joinToString(", ") { it.name }
                            Text(
                                text = "Sanatçı: $artistNames • ${album.releaseYear ?: ""}",
                                color = TextSecondary,
                                fontSize = 14.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
                
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Şarkılar",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                items(profile.tracks) { track ->
                    AlbumTrackItem(
                        track = track,
                        onTrackSelected = handleTrackSelected,
                        playPreview = playPreviewTrack,
                        isPlaying = isPlaying,
                        playingPreviewTrackId = playingPreviewTrackId.value,
                        isRemotePlaying = isRemotePlaying,
                        currentRemoteTrackId = currentRemoteTrackId,
                        loadingPreviewTrackId = loadingPreviewTrackId
                    )
                }
            }
        }
    }
}

@Composable
fun AlbumTrackItem(
    track: SpotifyTrack,
    onTrackSelected: (SpotifyTrack) -> Unit,
    playPreview: (SpotifyTrack) -> Unit,
    isPlaying: Boolean,
    playingPreviewTrackId: String?,
    isRemotePlaying: Boolean,
    currentRemoteTrackId: String?,
    loadingPreviewTrackId: String?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTrackSelected(track) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DarkGray)
                .clickable { playPreview(track) },
            contentAlignment = Alignment.Center
        ) {
            if (track.albumArtUrl != null) {
                coil3.compose.AsyncImage(
                    model = track.albumArtUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = TextSecondary
                )
            }

            // Play/Pause Overlay
            val isThisPlaying =
                (isPlaying && playingPreviewTrackId == track.id) ||
                        (isRemotePlaying && currentRemoteTrackId == track.id)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isThisPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.name,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (track.isExplicit) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                Color(0xFF888888).copy(alpha = 0.3f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(
                                horizontal = 4.dp,
                                vertical = 2.dp
                            )
                    ) {
                        Text(
                            text = "E",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (loadingPreviewTrackId == track.id) {
                    Spacer(modifier = Modifier.width(4.dp))
                    CircularProgressIndicator(
                        color = Color(0xFF1DB954),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Text(
                text = track.artistName,
                color = TextSecondary,
                fontSize = 14.sp,
                maxLines = 1
            )
        }
    }
}
