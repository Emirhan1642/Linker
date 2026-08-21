package com.linker.app.presentation.screens.note

import kotlinx.coroutines.delay
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import com.linker.app.R

import android.Manifest
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.linker.app.domain.model.NoteType
import com.linker.app.presentation.components.WheelTimePicker
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun NoteEditorScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSpotifySearch: () -> Unit,
    onNavigateToLocationMap: (lat: Double, lon: Double, placeName: String) -> Unit,
    navController: NavController,
    viewModel: NoteEditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Timer state
    var showTimerPicker by remember { mutableStateOf(false) }
    var days by remember { mutableStateOf(0) }
    var hours by remember { mutableStateOf(0) }
    var minutes by remember { mutableStateOf(0) }

    // GIF state
    var showGifPicker by remember { mutableStateOf(false) }

    // Location permission
    val locationPermission = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    // Automatically fetch location when permission is granted and LOCATION type is selected
    LaunchedEffect(locationPermission.status.isGranted) {
        if (locationPermission.status.isGranted &&
            uiState.selectedType == NoteType.LOCATION &&
            uiState.latitude == null &&
            !uiState.isLocationLoading
        ) {
            delay(100) // Small delay to let UI settle
            viewModel.fetchCurrentLocation()
        }
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            onNavigateBack()
        }
    }

    // Spotify track saved state
    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle

    LaunchedEffect(Unit) {
        savedStateHandle?.run {
            val selectedTrackId = get<String>("selected_track_id")
            val selectedTrackName = get<String>("selected_track_name")
            val selectedTrackArtist = get<String>("selected_track_artist")
            val selectedTrackArt = get<String>("selected_track_art")
            val selectedTrackPreview = get<String>("selected_track_preview")
            val selectedClipStartMs = get<Long>("selected_clip_start_ms") ?: 0L
            val selectedClipEndMs = get<Long>("selected_clip_end_ms") ?: 30_000L

            if (selectedTrackId != null && selectedTrackName != null && selectedTrackArtist != null) {
                viewModel.selectType(NoteType.MUSIC)
                viewModel.onMusicChange(
                    id = selectedTrackId,
                    name = selectedTrackName,
                    artist = selectedTrackArtist,
                    artUrl = selectedTrackArt,
                    preview = selectedTrackPreview,
                    caption = uiState.textContent
                )
                viewModel.onMusicClipChange(selectedClipStartMs, selectedClipEndMs)
                remove<String>("selected_track_id")
                remove<String>("selected_track_name")
                remove<String>("selected_track_artist")
                remove<String>("selected_track_art")
                remove<String>("selected_track_preview")
                remove<Long>("selected_clip_start_ms")
                remove<Long>("selected_clip_end_ms")
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        // Top Close Button
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopStart)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }

        // Top Right Edit Color Button
        IconButton(
            onClick = { viewModel.toggleColorPicker() },
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopEnd)
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Edit Color",
                tint = if (uiState.isColorPickerVisible) Color(0xFF3F51B5) else Color.White,
                modifier = Modifier.size(32.dp)
            )
        }

        // Center Content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.Center)
        ) {
            // Note bubble
            val bubbleScale by animateFloatAsState(targetValue = if (uiState.isColorPickerVisible) 1.2f else 1.0f, label = "bubbleScale")
            
            val bubbleBgColor = uiState.selectedBackgroundColor?.let {
                try { 
                    Color(android.graphics.Color.parseColor(it)) 
                } catch (e: IllegalArgumentException) {
                    android.util.Log.w("NoteEditor", "Invalid background color hex: $it", e)
                    Color(0xFF2C2C2C).copy(alpha = 0.8f) 
                } catch (e: Exception) {
                    android.util.Log.e("NoteEditor", "Unexpected error parsing color: $it", e)
                    Color(0xFF2C2C2C).copy(alpha = 0.8f)
                }
            } ?: Color(0xFF2C2C2C).copy(alpha = 0.8f)

            val bubbleTextColor = uiState.selectedTextColor?.let {
                try { 
                    Color(android.graphics.Color.parseColor(it)) 
                } catch (e: IllegalArgumentException) {
                    android.util.Log.w("NoteEditor", "Invalid text color hex: $it", e)
                    Color.White 
                } catch (e: Exception) {
                    android.util.Log.e("NoteEditor", "Unexpected error parsing color: $it", e)
                    Color.White
                }
            } ?: Color.White

            Box(modifier = Modifier.padding(bottom = 16.dp).scale(bubbleScale)) {
                Box(
                    modifier = Modifier
                        .background(bubbleBgColor, shape = RoundedCornerShape(24.dp))
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    when {
                    uiState.selectedType == NoteType.MUSIC && uiState.trackName.isNotBlank() -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = uiState.trackName,
                                color = bubbleTextColor,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = uiState.artistName,
                                color = Color.Gray,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            
                            if (uiState.textContent.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = uiState.textContent,
                                    color = bubbleTextColor,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    uiState.selectedType == NoteType.LOCATION -> {
                        LocationNoteBubble(
                            isLoading = uiState.isLocationLoading,
                            placeName = uiState.placeName,
                            locationUpdatedAt = uiState.locationUpdatedAt,
                            error = uiState.locationError,
                            onTapToView = {
                                val lat = uiState.latitude
                                val lon = uiState.longitude
                                if (lat != null && lon != null && uiState.placeName.isNotBlank()) {
                                    onNavigateToLocationMap(lat, lon, uiState.placeName)
                                }
                            }
                        )
                    }
                    uiState.selectedType == NoteType.GIF && !uiState.gifUrl.isNullOrBlank() -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(IntrinsicSize.Min)
                        ) {
                            val gifUrl = uiState.gifUrl ?: ""
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.DarkGray)
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalPlatformContext.current)
                                        .data(gifUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Seçili GIF",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                IconButton(
                                    onClick = { viewModel.clearGifSelection() },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(24.dp)
                                        .padding(2.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                ) {
                                    Icon(Icons.Default.Close, "Kaldır", tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                    uiState.selectedType == NoteType.COUNTDOWN && uiState.targetTime != null -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val targetTime = uiState.targetTime ?: 0L
                            val remaining = (targetTime - System.currentTimeMillis()).coerceAtLeast(0L)
                            val days = (remaining / 86400000L).coerceAtLeast(0)
                            val hours = ((remaining % 86400000L) / 3600000L).coerceAtLeast(0)
                            val mins = ((remaining % 3600000L) / 60000L).coerceAtLeast(0)
                            
                            val timeString = buildString {
                                if (days > 0) append("${days}g ")
                                if (hours > 0 || days > 0) append("${hours}s ")
                                append("${mins}d")
                            }
                            
                            Text(
                                text = "⏳ $timeString",
                                color = bubbleTextColor,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            BasicTextField(
                                value = uiState.textContent,
                                onValueChange = { viewModel.onTextChange(it) },
                                textStyle = TextStyle(
                                    color = bubbleTextColor,
                                    fontSize = 18.sp,
                                    textAlign = TextAlign.Center
                                ),
                                cursorBrush = SolidColor(bubbleTextColor),
                                decorationBox = { innerTextField ->
                                    if (uiState.textContent.isEmpty()) {
                                        Text("Not...", color = Color.Gray, fontSize = 18.sp, textAlign = TextAlign.Center)
                                    }
                                    innerTextField()
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    else -> {
                        if (uiState.selectedType == NoteType.GIF) {
                            Text(
                                text = "Lütfen bir GIF seçin",
                                color = Color.Gray,
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            BasicTextField(
                                value = uiState.textContent,
                                onValueChange = { viewModel.onTextChange(it) },
                                textStyle = TextStyle(
                                    color = bubbleTextColor,
                                    fontSize = 18.sp,
                                    textAlign = TextAlign.Center
                                ),
                                cursorBrush = SolidColor(bubbleTextColor),
                                decorationBox = { innerTextField ->
                                    if (uiState.textContent.isEmpty()) {
                                        Text("Not...", color = Color.Gray, fontSize = 18.sp, textAlign = TextAlign.Center)
                                    }
                                    innerTextField()
                                },
                                modifier = Modifier.width(IntrinsicSize.Min)
                            )
                        }
                    }
                }
            }
        }

        // Profile Picture
        val profilePictureUrl = remember(uiState.authorProfilePictureUrl) {
            val url = uiState.authorProfilePictureUrl
            if (!url.isNullOrBlank()) {
                url
            } else {
                "https://ui-avatars.com/api/?name=User&background=random"
            }
        }

        AsyncImage(
                model = profilePictureUrl,
                contentDescription = "Profil Resmi",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color.DarkGray, CircleShape)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons Row
            if (!uiState.isColorPickerVisible) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                // 1. Current Song (Headphones)
                CircularIconButton(
                    icon = Icons.Default.Headphones,
                    tint = Color(0xFFFF9800),
                    onClick = { viewModel.fetchCurrentlyPlayingTrack() },
                    contentDescription = stringResource(R.string.note_btn_current_song)
                )

                // 2. Search Song (Music Note)
                CircularIconButton(
                    icon = Icons.Default.MusicNote,
                    tint = Color(0xFFE91E63),
                    onClick = {
                        viewModel.selectType(NoteType.MUSIC)
                        onNavigateToSpotifySearch()
                    },
                    contentDescription = stringResource(R.string.note_btn_search_song)
                )

                // 3. Location — requests permission then fetches live location
                CircularIconButton(
                    icon = Icons.Default.LocationOn,
                    tint = if (uiState.selectedType == NoteType.LOCATION && uiState.latitude != null)
                               Color(0xFF9C27B0) else Color(0xFF9C27B0).copy(alpha = 0.6f),
                    onClick = {
                        viewModel.selectType(NoteType.LOCATION)
                        if (locationPermission.status.isGranted) {
                            viewModel.fetchCurrentLocation()
                        } else {
                            locationPermission.launchPermissionRequest()
                        }
                    },
                    contentDescription = stringResource(R.string.note_btn_live_location)
                )

                // 4. GIF
                CircularIconButton(
                    icon = Icons.Default.Gif,
                    tint = Color(0xFF4CAF50),
                    onClick = { showGifPicker = true },
                    contentDescription = stringResource(R.string.note_btn_gif)
                )

                // 5. Timer
                CircularIconButton(
                    icon = Icons.Default.Timer,
                    tint = Color(0xFF03A9F4),
                    onClick = {
                        viewModel.selectType(NoteType.COUNTDOWN)
                        showTimerPicker = true
                    },
                    contentDescription = stringResource(R.string.note_btn_countdown)
                )
            }
        } else {
            // Color Picker Row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 24.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) {
                items(NoteThemes.themes) { theme ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(theme.backgroundColor)
                            .border(
                                width = if (uiState.selectedBackgroundColor == theme.bgHex) 2.dp else 1.dp,
                                color = if (uiState.selectedBackgroundColor == theme.bgHex) Color.White else Color.DarkGray,
                                shape = CircleShape
                            )
                            .clickable {
                                viewModel.onColorSelected(theme.bgHex, theme.textHex)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Aa", color = theme.textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
        } // End of Column
        
        // Error Message Display
        val displayError = uiState.error ?: uiState.locationError
        if (displayError != null) {
            Text(
                text = displayError,
                color = Color.Red,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp, start = 16.dp, end = 16.dp),
                textAlign = TextAlign.Center,
                fontSize = 14.sp
            )
        }

        // Bottom Bar
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, contentDescription = "Audience", tint = Color.Gray, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Hedef kitle: arkadaşlar >", color = Color.Gray, fontSize = 14.sp)
            }

            Button(
                onClick = { viewModel.saveNote() },
                enabled = !uiState.isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3F51B5)),
                shape = RoundedCornerShape(20.dp)
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                } else {
                    Text("Paylaş", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Timer Bottom Sheet
        if (showTimerPicker) {
            ModalBottomSheet(
                onDismissRequest = { showTimerPicker = false },
                containerColor = Color(0xFF1E1E1E)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Zamanlayıcı Ayarla", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Text("Gün", color = Color.Gray)
                        Text("Saat", color = Color.Gray)
                        Text("Dakika", color = Color.Gray)
                    }

                    WheelTimePicker(
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                        days = days,
                        hours = hours,
                        minutes = minutes,
                        onDaysChange = { days = it },
                        onHoursChange = { hours = it },
                        onMinutesChange = { minutes = it }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val timeMs = System.currentTimeMillis() + (days * 86400000L) + (hours * 3600000L) + (minutes * 60000L)
                            viewModel.onCountdownChange(uiState.textContent.ifBlank { "Sayac" }, timeMs)
                            showTimerPicker = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3F51B5)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Ayarla", color = Color.White)
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }

        // GIF Picker Bottom Sheet
        if (showGifPicker) {
            GifPickerBottomSheet(
                onDismissRequest = { showGifPicker = false },
                onGifSelected = { url, ratio ->
                    viewModel.onGifSelected(url, ratio)
                }
            )
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// Location Bubble
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LocationNoteBubble(
    isLoading: Boolean,
    placeName: String,
    locationUpdatedAt: Long?,
    error: String?,
    onTapToView: () -> Unit
) {
    when {
        isLoading -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color(0xFF9C27B0),
                    strokeWidth = 2.dp
                )
                Text("Konum alınıyor...", color = Color.Gray, fontSize = 15.sp)
            }
        }
        error != null -> {
            Text(
                text = "⚠ $error",
                color = Color(0xFFFF5252),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
        placeName.isNotBlank() -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onTapToView() }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFF9C27B0),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = placeName,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
                if (locationUpdatedAt != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "● Canlı · ${formatRelativeTime(locationUpdatedAt)}",
                        color = Color(0xFF9C27B0),
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Haritada gör →",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }
        else -> {
            Text("Konum bekleniyor...", color = Color.Gray, fontSize = 15.sp)
        }
    }
}

/** Returns a human-readable relative time string (e.g. "2dk önce", "az önce"). */
private fun formatRelativeTime(epochMs: Long): String {
    val diffMs = System.currentTimeMillis() - epochMs
    val seconds = TimeUnit.MILLISECONDS.toSeconds(diffMs)
    return when {
        seconds < 10 -> "az önce"
        seconds < 60 -> "${seconds}sn önce"
        seconds < 3600 -> "${TimeUnit.MILLISECONDS.toMinutes(diffMs)}dk önce"
        else -> "${TimeUnit.MILLISECONDS.toHours(diffMs)}sa önce"
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared Circular Icon Button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun CircularIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit,
    contentDescription: String = "İşlem düğmesi"
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .border(1.dp, Color.DarkGray, CircleShape)
            .clip(CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
    }
}
