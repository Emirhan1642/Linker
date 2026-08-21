package com.linker.app.presentation.screens.note

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linker.app.domain.model.SpotifyAlbumDomain
import com.linker.app.presentation.theme.Black
import com.linker.app.presentation.theme.DarkGray
import com.linker.app.presentation.theme.TextPrimary
import com.linker.app.presentation.theme.TextSecondary
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistProfileScreen(
    artistId: String,
    onNavigateBack: () -> Unit,
    onTrackSelected: (SpotifyTrack) -> Unit,
    onAlbumSelected: (String) -> Unit,
    viewModel: ArtistProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isPlaying by viewModel.audioPlayerManager.isPlaying.collectAsStateWithLifecycle()
    val isRemotePlaying by viewModel.spotifyAppRemoteManager.isPlaying.collectAsStateWithLifecycle()
    val currentRemoteTrackId by viewModel.currentRemoteTrackId.collectAsStateWithLifecycle()
    val loadingPreviewTrackId by viewModel.loadingPreviewTrackId.collectAsStateWithLifecycle()
    val playingPreviewTrackId = remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current as Activity

    LaunchedEffect(artistId) {
        viewModel.loadProfile(artistId)
    }

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
            onError = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
        )
    }

    Scaffold(
        containerColor = Black,
        topBar = {
            TopAppBar(
                title = { Text(uiState.profile?.artist?.name ?: "", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Black)
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (uiState.error != null) {
                Text(
                    text = uiState.error ?: "",
                    color = Color.Red,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (uiState.profile != null) {
                val profile = uiState.profile ?: return@Box
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    // Artist Header
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(200.dp)
                                    .clip(CircleShape)
                                    .background(DarkGray),
                                contentAlignment = Alignment.Center
                            ) {
                                if (profile.artist.imageUrl != null) {
                                    coil3.compose.AsyncImage(
                                        model = profile.artist.imageUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = profile.artist.name,
                                color = TextPrimary,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            val followerStr = NumberFormat.getNumberInstance(Locale("tr", "TR")).format(profile.artist.followerCount)
                            Text(
                                text = "$followerStr aylık dinleyici/takipçi",
                                color = TextSecondary,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }

                    // Top Tracks
                    if (profile.topTracks.isNotEmpty()) {
                        item {
                            Text(
                                text = "Popüler Şarkılar",
                                color = TextPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        items(profile.topTracks.size) { index ->
                            val track = profile.topTracks[index]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { handleTrackSelected(track) }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = (index + 1).toString(),
                                    color = TextSecondary,
                                    fontSize = 16.sp,
                                    modifier = Modifier.width(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(DarkGray)
                                        .clickable { playPreviewTrack(track) },
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
                                        Icon(imageVector = Icons.Default.MusicNote, contentDescription = null, tint = TextSecondary)
                                    }
                                    
                                    val isThisPlaying =
                                        (isPlaying && playingPreviewTrackId.value == track.id) ||
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
                                                    .background(Color(0xFF888888).copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
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
                                }
                            }
                        }
                    }

                    // Album Sections
                    item {
                        AlbumSection("Popüler Çıkışlar", profile.popularReleases, onAlbumSelected)
                        AlbumSection("Albümler", profile.albums, onAlbumSelected)
                        AlbumSection("Single'lar ve EP'ler", profile.singles, onAlbumSelected)
                        AlbumSection("Derlemeler", profile.compilations, onAlbumSelected)
                    }
                }
            }
        }
    }
}

@Composable
fun AlbumSection(title: String, albums: List<SpotifyAlbumDomain>, onAlbumSelected: (String) -> Unit) {
    if (albums.isNotEmpty()) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(albums) { album ->
                AlbumItem(album, onAlbumSelected)
            }
        }
    }
}

@Composable
fun AlbumItem(album: SpotifyAlbumDomain, onAlbumSelected: (String) -> Unit) {
    Column(
        modifier = Modifier.width(120.dp).clickable { onAlbumSelected(album.id) },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DarkGray),
            contentAlignment = Alignment.Center
        ) {
            if (album.imageUrl != null) {
                coil3.compose.AsyncImage(
                    model = album.imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(imageVector = Icons.Default.MusicNote, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(48.dp))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = album.name,
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (album.releaseYear != null) {
            Text(
                text = album.releaseYear,
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
    }
}
