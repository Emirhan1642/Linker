package com.linker.app.presentation.screens.link

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.linker.app.R
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.graphics.Brush
import com.linker.app.presentation.animation.bouncyClick
import com.linker.app.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkEditorScreen(
    linkId: String?,
    initialDescription: String?,
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: LinkEditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.onMediaSelected(uris)
        }
    }

    var showLocationPicker by remember { mutableStateOf(false) }

    LaunchedEffect(linkId, initialDescription) {
        viewModel.initialize(linkId, initialDescription)
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            onSaved()
        }
    }

    if (showLocationPicker) {
        LocationPickerScreen(
            locationService = viewModel.locationService,
            onLocationSelected = { loc ->
                viewModel.setLocation(loc)
                showLocationPicker = false
            },
            onDismiss = { showLocationPicker = false }
        )
        return
    }

    Scaffold(
        containerColor = Black,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (linkId == null) "Yeni gönderi" else "Düzenle",
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
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Black)
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Black)
                    .padding(16.dp)
            ) {
                if (uiState.error != null) {
                    Text(
                        text = uiState.error ?: "",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Taslaklar (Drafts) Button
                    Box(
                        modifier = Modifier
                            .weight(0.35f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(DarkGrayTransparent)
                            .border(1.dp, GlassCardBorder, RoundedCornerShape(14.dp))
                            .bouncyClick { onNavigateBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Taslaklar", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                    
                    // Paylaş (Share) Button
                    val canShare = !uiState.isSaving && (uiState.description.isNotBlank() || uiState.mediaUris.isNotEmpty())
                    Box(
                        modifier = Modifier
                            .weight(0.65f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .then(
                                if (canShare) Modifier.background(Brush.horizontalGradient(LinkerBrandGradient))
                                else Modifier.background(DarkGray)
                            )
                            .bouncyClick(enabled = canShare) { viewModel.saveLink() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                text = "Paylaş",
                                color = if (canShare) Color.White else TextSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Media Preview Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 14.dp)
                    .aspectRatio(1f) // Square box for image preview
                    .clip(RoundedCornerShape(20.dp))
                    .background(DarkGrayTransparent)
                    .border(1.dp, GlassCardBorder, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.mediaUris.isNotEmpty()) {
                    val pagerState = rememberPagerState(pageCount = { uiState.mediaUris.size })
                    val context = androidx.compose.ui.platform.LocalContext.current
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val uri = uiState.mediaUris[page]
                        val isVideo = com.linker.app.core.util.MediaUtils.isVideoUri(context, uri)
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (isVideo) {
                                com.linker.app.presentation.screens.home.VideoPlayerView(
                                    videoUrl = uri.toString(),
                                    isPlaying = pagerState.currentPage == page,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = "Selected Media",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            // Delete button on current item
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(12.dp)
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Black.copy(alpha = 0.7f))
                                    .border(1.dp, GlassCardBorder, CircleShape)
                                    .bouncyClick { viewModel.removeMediaAt(page) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Kaldır",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Add more media button
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Black.copy(alpha = 0.75f))
                            .border(1.dp, GlassCardBorder, RoundedCornerShape(12.dp))
                            .bouncyClick {
                                mediaPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Text("Ekle", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    
                    // Pager Indicators
                    if (uiState.mediaUris.size > 1) {
                        Row(
                            Modifier
                                .wrapContentHeight()
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            repeat(uiState.mediaUris.size) { iteration ->
                                val isCurrent = pagerState.currentPage == iteration
                                Box(
                                    modifier = Modifier
                                        .padding(3.dp)
                                        .clip(CircleShape)
                                        .background(if (isCurrent) LinkerPrimary else Color.White.copy(alpha = 0.5f))
                                        .size(if (isCurrent) 8.dp else 6.dp)
                                )
                            }
                        }
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxSize()
                            .bouncyClick {
                                mediaPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                            },
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(LinkerPrimary.copy(alpha = 0.15f))
                                .border(1.dp, LinkerPrimary.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Add", tint = LinkerPrimary, modifier = Modifier.size(32.dp))
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Görsel veya Video Ekle", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Akışta veya Keşfet'te paylaşın", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }

            // Description Input
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                if (uiState.description.isEmpty()) {
                    Text(
                        text = "Bir açıklama veya düşünceni yaz...",
                        color = TextSecondary,
                        fontSize = 15.sp
                    )
                }
                BasicTextField(
                    value = uiState.description,
                    onValueChange = { viewModel.onDescriptionChange(it) },
                    textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp),
                    cursorBrush = SolidColor(LinkerPrimary),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Quick Actions (#, @, AI)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SmallIconButton(text = "#") { viewModel.appendToDescription("#") }
                SmallIconButton(text = "@") { viewModel.appendToDescription("@") }
            }

            HorizontalDivider(color = GlassCardBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

            // Action Rows
            ActionRow(
                icon = Icons.Default.LocationOn,
                title = "Konum",
                subtitle = uiState.location ?: "Mekan veya konum seç",
                onClick = { showLocationPicker = true }
            )

            if (!uiState.location.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(GradientBlue.copy(alpha = 0.15f))
                            .border(1.dp, GradientBlue.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = GradientBlue,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = uiState.location ?: "",
                                color = GradientBlue,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Kaldır",
                                tint = GradientBlue,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { viewModel.setLocation(null) }
                            )
                        }
                    }
                }
            }
            
            Text(
                text = "Paylaştığınız konum, akışta ve harita aramasında kullanıcılara gösterilir.",
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            HorizontalDivider(color = GlassCardBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

            // AI Label Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "AI",
                    tint = LinkerPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                ) {
                    Text("Yapay zeka etiketi ekle", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(
                        text = "Yapay zekayla oluşturulan gerçekçi içerikleri etiketlemenizi öneriyoruz.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = uiState.aiLabelEnabled,
                    onCheckedChange = { viewModel.onAiLabelToggled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = LinkerPrimary,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = DarkGray
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SmallIconButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(DarkGrayTransparent)
            .border(1.dp, GlassCardBorder, CircleShape)
            .bouncyClick { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClick { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = title, tint = LinkerPrimary, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                color = GradientBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Go",
            tint = TextSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun LocationChip(
    text: String,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (isSelected) Modifier.background(Brush.horizontalGradient(LinkerBrandGradient))
                else Modifier.background(DarkGrayTransparent).border(1.dp, GlassCardBorder, RoundedCornerShape(10.dp))
            )
            .bouncyClick { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = if (isSelected) Color.White else TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
