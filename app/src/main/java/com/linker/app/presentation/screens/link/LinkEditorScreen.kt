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
import coil3.compose.AsyncImage
import com.linker.app.R
import com.linker.app.presentation.theme.Black
import com.linker.app.presentation.theme.DarkGray
import com.linker.app.presentation.theme.TextPrimary
import com.linker.app.presentation.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkEditorScreen(
    linkId: String?,
    initialDescription: String?,
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: LinkEditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.onMediaSelected(uris)
        }
    }

    LaunchedEffect(linkId, initialDescription) {
        viewModel.initialize(linkId, initialDescription)
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            onSaved()
        }
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
                    Button(
                        onClick = { onNavigateBack() },
                        colors = ButtonDefaults.buttonColors(containerColor = DarkGray),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(0.4f)
                            .height(48.dp)
                    ) {
                        Text("Taslaklar", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    }
                    
                    // Paylaş (Share) Button
                    Button(
                        onClick = { viewModel.saveLink() },
                        enabled = !uiState.isSaving && (uiState.description.isNotBlank() || uiState.mediaUris.isNotEmpty()),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(0.6f)
                            .height(48.dp)
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Paylaş", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
                    .padding(horizontal = 32.dp, vertical = 16.dp)
                    .aspectRatio(1f) // Square box for image preview
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkGray),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.mediaUris.isNotEmpty()) {
                    val pagerState = rememberPagerState(pageCount = { uiState.mediaUris.size })
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        AsyncImage(
                            model = uiState.mediaUris[page],
                            contentDescription = "Selected Media",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    
                    // Pager Indicators
                    if (uiState.mediaUris.size > 1) {
                        Row(
                            Modifier
                                .wrapContentHeight()
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            repeat(uiState.mediaUris.size) { iteration ->
                                val color = if (pagerState.currentPage == iteration) MaterialTheme.colorScheme.primary else Color.LightGray
                                Box(
                                    modifier = Modifier
                                        .padding(2.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .size(6.dp)
                                )
                            }
                        }
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            mediaPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                        }
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add", tint = TextSecondary, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Görsel veya Video Ekle", color = TextSecondary, fontSize = 14.sp)
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
                        text = "Bir açıklama ekle...",
                        color = TextSecondary,
                        fontSize = 15.sp
                    )
                }
                BasicTextField(
                    value = uiState.description,
                    onValueChange = { viewModel.onDescriptionChange(it) },
                    textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Quick Actions (#, @, AI)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickActionButton(text = "Anket", icon = null)
                QuickActionButton(text = "İstem", icon = null)
                Spacer(modifier = Modifier.weight(1f))
                SmallIconButton(text = "#")
                SmallIconButton(text = "@")
            }

            Divider(color = DarkGray, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

            // Action Rows
            ActionRow(
                icon = Icons.Default.MusicNote,
                title = "Müzik ekle",
                subtitle = uiState.music,
                onClick = { /* TODO: Navigate to Music Picker */ }
            )
            ActionRow(
                icon = Icons.Default.PersonAdd,
                title = "Kişileri etiketle",
                subtitle = if (uiState.taggedUsers.isNotEmpty()) "${uiState.taggedUsers.size} kişi" else null,
                onClick = { /* TODO: Navigate to Tagging */ }
            )
            ActionRow(
                icon = Icons.Default.LocationOn,
                title = "Konum ekle",
                subtitle = uiState.location,
                onClick = { /* TODO: Navigate to Location Picker */ }
            )

            // Location Chips (Mock)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LocationChip("Bursa")
                LocationChip("İstanbul")
                LocationChip("Kadıköy")
            }
            
            Text(
                text = "Bu içeriği paylaştığınız kişiler, etiketlediğiniz konumu görebilir ve bu içeriği haritada görüntüleyebilir.",
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Divider(color = DarkGray, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

            // AI Label Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "AI",
                    tint = TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                ) {
                    Text("Yapay zeka etiketi ekle", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(
                        text = "Yapay zekayla oluşturulan belirli gerçekçi içerikleri etiketlemenizi zorunlu tutuyoruz.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = uiState.aiLabelEnabled,
                    onCheckedChange = { viewModel.onAiLabelToggled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = DarkGray
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(40.dp)) // Extra padding at bottom
        }
    }
}

@Composable
private fun QuickActionButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector?) {
    Row(
        modifier = Modifier
            .background(DarkGray, RoundedCornerShape(8.dp))
            .clickable { }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(text = text, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SmallIconButton(text: String) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(DarkGray, CircleShape)
            .clickable { },
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = title, tint = TextPrimary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
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
                color = TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Go",
            tint = TextSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun LocationChip(text: String) {
    Box(
        modifier = Modifier
            .background(DarkGray, RoundedCornerShape(8.dp))
            .clickable { }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text = text, color = TextSecondary, fontSize = 12.sp)
    }
}
