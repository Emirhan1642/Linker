package com.linker.app.presentation.screens.note

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linker.app.presentation.theme.Black
import com.linker.app.presentation.theme.DarkGray
import com.linker.app.presentation.theme.TextPrimary
import com.linker.app.presentation.theme.TextSecondary
import com.linker.app.core.util.AudioPlayerManager
import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.Person
import androidx.compose.foundation.shape.CircleShape
import com.linker.app.domain.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifySearchScreen(
    onNavigateBack: () -> Unit,
    onTrackSelected: (SpotifyTrack) -> Unit,
    onArtistSelected: (String) -> Unit,
    onAlbumSelected: (String) -> Unit,
    viewModel: SpotifySearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isPlaying by viewModel.audioPlayerManager.isPlaying.collectAsStateWithLifecycle()
    val isRemotePlaying by viewModel.spotifyAppRemoteManager.isPlaying.collectAsStateWithLifecycle()
    val currentRemoteTrackId by viewModel.currentRemoteTrackId.collectAsStateWithLifecycle()
    val playingPreviewTrackId = remember { mutableStateOf<String?>(null) }
    val loadingPreviewTrackId by viewModel.loadingPreviewTrackId.collectAsStateWithLifecycle()
    
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
            onError = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
        )
    }

    Scaffold(
        containerColor = Black,
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = uiState.query,
                        onValueChange = { viewModel.onQueryChange(it) },
                        placeholder = {
                            Text(
                                "Şarkı, sanatçı veya albüm ara...",
                                color = TextSecondary
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Geri",
                            tint = TextPrimary
                        )
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
            } else if (uiState.query.isBlank()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (uiState.error != null) {
                        Text(
                            text = uiState.error ?: "",
                            color = Color.Red,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                    SpotifyDashboard(
                        uiState = uiState,
                        onTrackSelected = handleTrackSelected,
                        playPreview = playPreviewTrack,
                        isPlaying = isPlaying,
                        playingPreviewTrackId = playingPreviewTrackId.value,
                        isRemotePlaying = isRemotePlaying,
                        currentRemoteTrackId = currentRemoteTrackId,
                        loadingPreviewTrackId = loadingPreviewTrackId,
                        onShowMoreClick = { category ->
                            viewModel.loadMore(category)
                        }
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Category Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SpotifySearchType.entries.forEach { type ->
                            val text = when (type) {
                                SpotifySearchType.ALL -> "Tümü"
                                SpotifySearchType.TRACKS -> "Şarkılar"
                                SpotifySearchType.ARTISTS -> "Sanatçılar"
                                SpotifySearchType.ALBUMS -> "Albümler"
                            }
                            val isSelected = uiState.searchType == type
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else DarkGray)
                                    .clickable { viewModel.setSearchType(type) }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = text,
                                    color = if (isSelected) Color.Black else TextPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    if (uiState.error != null) {
                        Text(
                            text = uiState.error ?: "",
                            color = Color.Red,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(uiState.results) { item ->
                            when (item) {
                                is SpotifySearchResultItem.Track -> {
                                    val track = item.track
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { handleTrackSelected(track) }
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
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
                                                Icon(
                                                    imageVector = Icons.Default.MusicNote,
                                                    contentDescription = null,
                                                    tint = TextSecondary
                                                )
                                            }

                                            // Play/Pause Overlay
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

                                is SpotifySearchResultItem.Artist -> {
                                    val artist = item.artist
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onArtistSelected(artist.id) }
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .background(DarkGray),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (artist.imageUrl != null) {
                                                coil3.compose.AsyncImage(
                                                    model = artist.imageUrl,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.Person,
                                                    contentDescription = null,
                                                    tint = TextSecondary
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(16.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = artist.name,
                                                color = TextPrimary,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 16.sp,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = "Sanatçı",
                                                color = TextSecondary,
                                                fontSize = 14.sp,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }

                                is SpotifySearchResultItem.Album -> {
                                    val album = item.album
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onAlbumSelected(album.id) }
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
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
                                                Icon(
                                                    imageVector = Icons.Default.MusicNote,
                                                    contentDescription = null,
                                                    tint = TextSecondary
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(16.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = album.name,
                                                color = TextPrimary,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 16.sp,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = "Albüm" + if (album.releaseYear != null) " • ${album.releaseYear}" else "",
                                                color = TextSecondary,
                                                fontSize = 14.sp,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            if (uiState.results.isNotEmpty() && uiState.results.size >= 10) {
                                TextButton(
                                    onClick = { viewModel.loadMoreSearchResults() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    if (uiState.isLoadingMoreSearch) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        Text(
                                            "Daha Fazla Göster",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SpotifyDashboard(
    uiState: SpotifySearchUiState,
    onTrackSelected: (SpotifyTrack) -> Unit,
    playPreview: (SpotifyTrack) -> Unit,
    isPlaying: Boolean,
    playingPreviewTrackId: String?,
    isRemotePlaying: Boolean,
    currentRemoteTrackId: String?,
    loadingPreviewTrackId: String?,
    onShowMoreClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        if (uiState.recentTracks.isNotEmpty()) {
            item {
                DashboardSection(
                    "Son Dinlenenler",
                    uiState.recentTracks,
                    onTrackSelected,
                    playPreview,
                    isPlaying,
                    playingPreviewTrackId,
                    isRemotePlaying,
                    currentRemoteTrackId,
                    loadingPreviewTrackId = loadingPreviewTrackId
                )
            }
        }

        if (uiState.recommendedTracks.isNotEmpty()) {
            item {
                val cat = "Önerilenler"
                DashboardSection(
                    cat,
                    uiState.recommendedTracks,
                    onTrackSelected,
                    playPreview,
                    isPlaying,
                    playingPreviewTrackId,
                    isRemotePlaying,
                    currentRemoteTrackId,
                    loadingPreviewTrackId = loadingPreviewTrackId,
                    onShowMoreClick = { onShowMoreClick(cat) },
                    isLoadingMore = uiState.loadingMoreCategory == cat
                )
            }
        }

        if (uiState.top50Turkey.isNotEmpty()) {
            item {
                val cat = "TOP 50 Türkiye"
                DashboardSection(
                    cat,
                    uiState.top50Turkey,
                    onTrackSelected,
                    playPreview,
                    isPlaying,
                    playingPreviewTrackId,
                    isRemotePlaying,
                    currentRemoteTrackId,
                    loadingPreviewTrackId = loadingPreviewTrackId,
                    onShowMoreClick = { onShowMoreClick(cat) },
                    isLoadingMore = uiState.loadingMoreCategory == cat
                )
            }
        }

        if (uiState.top50Global.isNotEmpty()) {
            item {
                val cat = "TOP 50 Global"
                DashboardSection(
                    cat,
                    uiState.top50Global,
                    onTrackSelected,
                    playPreview,
                    isPlaying,
                    playingPreviewTrackId,
                    isRemotePlaying,
                    currentRemoteTrackId,
                    loadingPreviewTrackId = loadingPreviewTrackId,
                    onShowMoreClick = { onShowMoreClick(cat) },
                    isLoadingMore = uiState.loadingMoreCategory == cat
                )
            }
        }

        uiState.moodPlaylists.forEach { (mood, tracks) ->
            if (tracks.isNotEmpty()) {
                item {
                    DashboardSection(
                        mood,
                        tracks,
                        onTrackSelected,
                        playPreview,
                        isPlaying,
                        playingPreviewTrackId,
                        isRemotePlaying,
                        currentRemoteTrackId,
                        loadingPreviewTrackId = loadingPreviewTrackId,
                        onShowMoreClick = { onShowMoreClick(mood) },
                        isLoadingMore = uiState.loadingMoreCategory == mood
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardSection(
    title: String,
    tracks: List<SpotifyTrack>,
    onTrackSelected: (SpotifyTrack) -> Unit,
    playPreview: (SpotifyTrack) -> Unit,
    isPlaying: Boolean,
    playingPreviewTrackId: String?,
    isRemotePlaying: Boolean,
    currentRemoteTrackId: String?,
    loadingPreviewTrackId: String?,
    onShowMoreClick: (() -> Unit)? = null,
    isLoadingMore: Boolean = false
) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 24.dp)) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(tracks) { track ->
                DashboardTrackItem(
                    track = track,
                    onTrackSelected = onTrackSelected,
                    playPreview = { playPreview(track) },
                    isPlaying = isPlaying,
                    playingPreviewTrackId = playingPreviewTrackId,
                    isRemotePlaying = isRemotePlaying,
                    currentRemoteTrackId = currentRemoteTrackId,
                    isLoading = loadingPreviewTrackId == track.id
                )
            }
            if (onShowMoreClick != null && tracks.size < 30) {
                item {
                    Box(
                        modifier = Modifier
                            .size(width = 80.dp, height = 120.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkGray)
                            .clickable(enabled = !isLoadingMore) { onShowMoreClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoadingMore) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Daha Fazla",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Daha Fazla", color = TextPrimary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardTrackItem(
    track: SpotifyTrack,
    onTrackSelected: (SpotifyTrack) -> Unit,
    playPreview: (SpotifyTrack) -> Unit,
    isPlaying: Boolean,
    playingPreviewTrackId: String?,
    isRemotePlaying: Boolean,
    currentRemoteTrackId: String?,
    isLoading: Boolean = false
) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .clickable { onTrackSelected(track) },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
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
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = TextSecondary
                )
            }

            val isThisPlaying =
                (isPlaying && playingPreviewTrackId == track.id) || (isRemotePlaying && currentRemoteTrackId == track.id)
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
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = track.name,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (track.isExplicit) {
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .background(
                            Color(0xFF888888).copy(alpha = 0.3f),
                            RoundedCornerShape(2.dp)
                        )
                        .padding(horizontal = 2.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "E",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (isLoading) {
                Spacer(modifier = Modifier.width(2.dp))
                CircularProgressIndicator(
                    color = Color(0xFF1DB954),
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(10.dp)
                )
            }
        }
        Text(
            text = track.artistName,
            color = TextSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}