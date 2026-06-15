package com.linker.app.presentation.screens.note

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.linker.app.domain.model.NoteType
import com.linker.app.presentation.components.WheelTimePicker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSpotifySearch: () -> Unit,
    onNavigateToLocationPicker: () -> Unit,
    navController: NavController,
    viewModel: NoteEditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // Timer state
    var showTimerPicker by remember { mutableStateOf(false) }
    var hours by remember { mutableStateOf(0) }
    var minutes by remember { mutableStateOf(0) }
    var seconds by remember { mutableStateOf(0) }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            onNavigateBack()
        }
    }
    
    // Check for saved state results (from Spotify or Location)
    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
    
    val selectedTrackId = savedStateHandle?.get<String>("selected_track_id")
    LaunchedEffect(selectedTrackId) {
        if (selectedTrackId != null) {
            viewModel.selectType(NoteType.MUSIC)
            // Fetch track details or set it (Needs proper implementation in ViewModel)
            savedStateHandle.remove<String>("selected_track_id")
        }
    }

    val selectedLat = savedStateHandle?.get<Double>("selected_location_lat")
    val selectedLon = savedStateHandle?.get<Double>("selected_location_lon")
    val selectedPlace = savedStateHandle?.get<String>("selected_location_name")
    LaunchedEffect(selectedLat, selectedLon, selectedPlace) {
        if (selectedLat != null && selectedLon != null && selectedPlace != null) {
            viewModel.selectType(NoteType.LOCATION)
            viewModel.onLocationChange(selectedLat, selectedLon, selectedPlace)
            savedStateHandle.remove<Double>("selected_location_lat")
            savedStateHandle.remove<Double>("selected_location_lon")
            savedStateHandle.remove<String>("selected_location_name")
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

        // Center Content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.Center)
        ) {
            // Note Text Input
            Box(
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .background(Color(0xFF2C2C2C).copy(alpha = 0.8f), shape = RoundedCornerShape(24.dp))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                BasicTextField(
                    value = uiState.textContent,
                    onValueChange = { viewModel.onTextChange(it) },
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    ),
                    cursorBrush = SolidColor(Color.White),
                    decorationBox = { innerTextField ->
                        if (uiState.textContent.isEmpty()) {
                            Text("Not...", color = Color.Gray, fontSize = 18.sp, textAlign = TextAlign.Center)
                        }
                        innerTextField()
                    },
                    modifier = Modifier.width(IntrinsicSize.Min)
                )
            }

            // Profile Picture
            AsyncImage(
                model = "https://ui-avatars.com/api/?name=User&background=random", // Placeholder
                contentDescription = "Profile Picture",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color.DarkGray, CircleShape)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons Row
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Current Song (Headphones)
                CircularIconButton(
                    icon = Icons.Default.Headphones,
                    tint = Color(0xFFFF9800),
                    onClick = { viewModel.selectType(NoteType.MUSIC) } // Placeholder functionality
                )
                
                // 2. Search Song (Music Note)
                CircularIconButton(
                    icon = Icons.Default.MusicNote,
                    tint = Color(0xFFE91E63),
                    onClick = {
                        viewModel.selectType(NoteType.MUSIC)
                        onNavigateToSpotifySearch()
                    }
                )

                // 3. Location
                CircularIconButton(
                    icon = Icons.Default.LocationOn,
                    tint = Color(0xFF9C27B0),
                    onClick = {
                        viewModel.selectType(NoteType.LOCATION)
                        onNavigateToLocationPicker()
                    }
                )

                // 4. GIF
                CircularIconButton(
                    icon = Icons.Default.Gif,
                    tint = Color(0xFF4CAF50),
                    onClick = { /* TODO GIF Picker */ }
                )

                // 5. Timer
                CircularIconButton(
                    icon = Icons.Default.Timer,
                    tint = Color(0xFF03A9F4),
                    onClick = {
                        viewModel.selectType(NoteType.COUNTDOWN)
                        showTimerPicker = true
                    }
                )
            }
        }

        // Error Message Display
        if (uiState.error != null) {
            Text(
                text = uiState.error!!,
                color = Color.Red,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp, start = 16.dp, end = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
                        Text("Saat", color = Color.Gray)
                        Text("Dakika", color = Color.Gray)
                        Text("Saniye", color = Color.Gray)
                    }
                    
                    WheelTimePicker(
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                        hours = hours,
                        minutes = minutes,
                        seconds = seconds,
                        onHoursChange = { hours = it },
                        onMinutesChange = { minutes = it },
                        onSecondsChange = { seconds = it }
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = {
                            val timeMs = System.currentTimeMillis() + (hours * 3600000) + (minutes * 60000) + (seconds * 1000)
                            viewModel.onCountdownChange("Sayac", timeMs)
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
    }
}

@Composable
fun CircularIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit
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
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
    }
}
