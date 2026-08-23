package com.linker.app.presentation.screens.story

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.linker.app.R
import com.linker.app.domain.model.StoryMediaType
import com.linker.app.domain.repository.StoryPrivacy
import com.linker.app.presentation.animation.bouncyClick
import com.linker.app.presentation.screens.home.VideoPlayerView
import com.linker.app.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryEditorScreen(
    onNavigateBack: () -> Unit,
    onStoryPublished: () -> Unit,
    viewModel: StoryEditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val uriStr = uri.toString().lowercase()
            val isVideo = uriStr.endsWith(".mp4") || uriStr.endsWith(".mov") || uriStr.contains("video")
            viewModel.onMediaSelected(uri, isVideo)
        }
    }

    LaunchedEffect(uiState.isPublished) {
        if (uiState.isPublished) {
            onStoryPublished()
        }
    }

    Scaffold(
        containerColor = Black,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Hikaye Oluştur",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Geri",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    if (uiState.selectedMediaUri != null) {
                        IconButton(onClick = { viewModel.clearMedia() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Temizle",
                                tint = TextSecondary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Black)
            )
        },
        bottomBar = {
            if (uiState.selectedMediaUri != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Black)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (uiState.error != null) {
                        Text(
                            text = uiState.error ?: "",
                            color = ErrorRed,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Privacy selector row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StoryPrivacyChip(
                            title = "Herkes",
                            isSelected = uiState.privacy == StoryPrivacy.PUBLIC,
                            onClick = { viewModel.onPrivacyChange(StoryPrivacy.PUBLIC) }
                        )
                        StoryPrivacyChip(
                            title = "Takipçiler",
                            isSelected = uiState.privacy == StoryPrivacy.FOLLOWERS_ONLY,
                            onClick = { viewModel.onPrivacyChange(StoryPrivacy.FOLLOWERS_ONLY) }
                        )
                        StoryPrivacyChip(
                            title = "Yakın Arkadaşlar",
                            isSelected = uiState.privacy == StoryPrivacy.CLOSE_FRIENDS,
                            onClick = { viewModel.onPrivacyChange(StoryPrivacy.CLOSE_FRIENDS) }
                        )
                    }

                    // Publish Button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brush.horizontalGradient(LinkerBrandGradient))
                            .bouncyClick(enabled = !uiState.isPublishing) {
                                viewModel.publishStory()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (uiState.isPublishing) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                text = "Hikayende Paylaş",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            val mediaUri = uiState.selectedMediaUri
            if (mediaUri != null) {
                // Media preview (9:16 aspect ratio box)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(DarkGrayTransparent)
                        .border(1.dp, GlassCardBorder, RoundedCornerShape(20.dp))
                ) {
                    if (uiState.mediaType == StoryMediaType.VIDEO) {
                        VideoPlayerView(
                            videoUrl = mediaUri.toString(),
                            isPlaying = true,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        AsyncImage(
                            model = mediaUri,
                            contentDescription = "Story Preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Floating Caption Input
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(16.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Black.copy(alpha = 0.65f))
                            .border(1.dp, GlassCardBorder, RoundedCornerShape(16.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        if (uiState.caption.isEmpty()) {
                            Text(
                                text = "Hikayene bir not veya başlık ekle...",
                                color = TextSecondary,
                                fontSize = 14.sp
                            )
                        }
                        BasicTextField(
                            value = uiState.caption,
                            onValueChange = { viewModel.onCaptionChange(it) },
                            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                            cursorBrush = SolidColor(LinkerPrimary),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else {
                // Media Picker Prompt
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(LinkerPrimary.copy(alpha = 0.15f))
                            .border(1.dp, LinkerPrimary.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = null,
                            tint = LinkerPrimary,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Text(
                        text = "24 Saatlik Hikaye Paylaş",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Takipçilerine gününden kesitler aktar. Hikayeler 24 saat sonra otomatik olarak kaybolur.",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Choose Media Button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brush.horizontalGradient(LinkerBrandGradient))
                            .bouncyClick {
                                mediaPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color.White)
                            Text("Galeriden Seç", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StoryPrivacyChip(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (isSelected) Modifier.background(Brush.horizontalGradient(LinkerBrandGradient))
                else Modifier.background(DarkGrayTransparent).border(1.dp, GlassCardBorder, RoundedCornerShape(12.dp))
            )
            .bouncyClick { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = title,
            color = if (isSelected) Color.White else TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
