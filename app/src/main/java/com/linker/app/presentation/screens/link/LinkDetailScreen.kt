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

import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.outlined.ModeComment

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
        val isAuthor = uiState.link?.author?.userId == viewModel.currentUserId
        UserActionSheet(
            isOwnContent = isAuthor,
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
                        contentDescription = androidx.compose.ui.res.stringResource(R.string.action_back),
                        tint = TextPrimary
                    )
                }
                Text(
                    text = androidx.compose.ui.res.stringResource(R.string.link_detail_post_title),
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

                    // Engagement Action Row (Like, Comment, Relink, Save)
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Like button
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.clickable { viewModel.toggleLike() }
                                ) {
                                    Icon(
                                        imageVector = if (post.engagement.isLiked) androidx.compose.material.icons.Icons.Default.Favorite
                                                      else androidx.compose.material.icons.Icons.Outlined.FavoriteBorder,
                                        contentDescription = "Beğen",
                                        tint = if (post.engagement.isLiked) Color(0xFFFF4B4B) else TextPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    if (post.engagement.likesCount > 0) {
                                        Text(
                                            text = post.engagement.likesCount.toString(),
                                            color = TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                // Comment button
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.clickable { showCommentSheet = true }
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ModeComment,
                                        contentDescription = "Yorumlar",
                                        tint = TextPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    if (post.engagement.commentsCount > 0) {
                                        Text(
                                            text = post.engagement.commentsCount.toString(),
                                            color = TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                // Relink button
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.clickable { viewModel.toggleRelink() }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Repeat,
                                        contentDescription = "Yeniden Paylaş",
                                        tint = if (post.engagement.isRelinked) com.linker.app.presentation.theme.LinkerPrimary else TextPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    if (post.engagement.relinksCount > 0) {
                                        Text(
                                            text = post.engagement.relinksCount.toString(),
                                            color = TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            // Save button
                            IconButton(onClick = { viewModel.toggleSave() }) {
                                Icon(
                                    imageVector = if (post.engagement.isSaved) Icons.Default.Bookmark
                                                  else Icons.Outlined.BookmarkBorder,
                                    contentDescription = "Kaydet",
                                    tint = if (post.engagement.isSaved) com.linker.app.presentation.theme.LinkerPrimary else TextPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    // Description and Actions
                    item {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
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
