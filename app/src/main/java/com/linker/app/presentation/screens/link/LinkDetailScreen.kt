package com.linker.app.presentation.screens.link

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import com.linker.app.R
import com.linker.app.presentation.components.UserActionSheet
import com.linker.app.presentation.theme.Black
import com.linker.app.presentation.theme.DarkGray
import com.linker.app.presentation.theme.TextPrimary
import com.linker.app.presentation.theme.TextSecondary
import com.linker.app.domain.model.ReportableContentType

@Composable
fun LinkDetailScreen(
    linkId: String,
    onNavigateBack: () -> Unit,
    viewModel: LinkDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showActionSheet by remember { mutableStateOf(false) }
    var showCommentSheet by remember { mutableStateOf(false) }

    LaunchedEffect(linkId) {
        viewModel.loadLink(linkId)
    }

    if (showActionSheet) {
        UserActionSheet(
            isOwnContent = false, // TODO: Check if current user is author
            contentType = ReportableContentType.LINK,
            onReport = { reason ->
                viewModel.reportLink(reason)
            },
            onDismiss = { showActionSheet = false }
        )
    }

    if (showCommentSheet) {
        CommentSheet(targetId = linkId, onDismiss = { showCommentSheet = false })
    }

    Scaffold(
        containerColor = Black,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
                Text(
                    text = "Post",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = { showActionSheet = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More",
                        tint = TextPrimary
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (uiState.error != null) {
                Text(
                    text = uiState.error ?: "Gönderi yüklenemedi",
                    color = TextSecondary,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (uiState.link != null) {
                val post = uiState.link!!
                val rawPrimaryUrl = post.primaryMedia.url
                val cleanPrimaryUrl = com.linker.app.core.util.MediaUtils.sanitizeMediaUrl(rawPrimaryUrl)
                val isVideo = post.linkType == com.linker.app.domain.model.LinkType.VIDEO ||
                        post.linkType == com.linker.app.domain.model.LinkType.REEL ||
                        com.linker.app.core.util.MediaUtils.isVideoUrl(cleanPrimaryUrl)

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    // Author Header
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            com.linker.app.presentation.components.LinkerAvatar(
                                imageUrl = post.author.profileImageUrl,
                                size = 40.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = post.author.displayName,
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "@${post.author.username}",
                                    color = com.linker.app.presentation.theme.LinkerPrimary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    // Media Content
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 250.dp, max = 520.dp)
                                .background(DarkGray),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isVideo && cleanPrimaryUrl.isNotBlank()) {
                                com.linker.app.presentation.screens.home.VideoPlayerView(
                                    videoUrl = cleanPrimaryUrl,
                                    isPlaying = true,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else if (cleanPrimaryUrl.isNotBlank()) {
                                coil3.compose.AsyncImage(
                                    model = cleanPrimaryUrl,
                                    contentDescription = post.description,
                                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    // Description and Actions
                    item {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            if (!post.description.isNullOrBlank()) {
                                Text(
                                    text = post.description ?: "",
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    lineHeight = 22.sp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { showCommentSheet = true }) {
                                    Text(
                                        text = if (post.engagement.commentsCount > 0)
                                            "${post.engagement.commentsCount} Yorumun Tümünü Gör"
                                        else "Yorum Yap...",
                                        color = com.linker.app.presentation.theme.LinkerPrimary,
                                        fontWeight = FontWeight.SemiBold
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
