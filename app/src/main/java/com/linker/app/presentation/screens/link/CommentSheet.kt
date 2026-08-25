package com.linker.app.presentation.screens.link

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linker.app.domain.model.Comment
import com.linker.app.presentation.components.LinkerAvatar
import com.linker.app.presentation.components.StoryState
import com.linker.app.presentation.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private val commentTimeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
private val commentHistoryDateFormat = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentSheet(
    targetId: String,
    onDismiss: () -> Unit,
    viewModel: CommentSheetViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var commentText by remember { mutableStateOf("") }

    LaunchedEffect(targetId) {
        viewModel.observeComments(targetId)
    }

    // If edit comment changes, pre-fill text
    LaunchedEffect(uiState.editComment) {
        if (uiState.editComment != null) {
            commentText = uiState.editComment?.content ?: ""
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val coroutineScope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkGray,
        dragHandle = { BottomSheetDefaults.DragHandle(color = TextSecondary) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Yorumlar (${uiState.comments.size})",
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Kapat",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            HorizontalDivider(
                color = Color.White.copy(alpha = 0.08f),
                modifier = Modifier.padding(bottom = 4.dp)
            )

            // Comments List
            Box(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
            ) {
                if (uiState.isLoading && uiState.comments.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = LinkerPrimary, modifier = Modifier.size(32.dp))
                    }
                } else if (uiState.comments.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("💬", fontSize = 36.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Henüz yorum yok",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "İlk yorumu sen yaz!",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(uiState.comments, key = { it.commentId }) { comment ->
                            val isExpanded = uiState.expandedReplyParentIds.contains(comment.commentId)
                            val replies = uiState.repliesMap[comment.commentId] ?: emptyList()

                            Column {
                                CommentItem(
                                    comment = comment,
                                    isReply = false,
                                    onReplyClick = {
                                        viewModel.setReplyTo(comment)
                                        coroutineScope.launch { sheetState.expand() }
                                    },
                                    onLikeClick = { viewModel.toggleLike(comment.commentId) },
                                    onEditClick = {
                                        viewModel.setEditComment(comment)
                                        coroutineScope.launch { sheetState.expand() }
                                    },
                                    onDeleteClick = { viewModel.deleteComment(comment.commentId) },
                                    onHistoryClick = { viewModel.loadCommentHistory(comment.commentId) }
                                )

                                if (comment.repliesCount > 0) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .padding(start = 48.dp)
                                            .clickable {
                                                if (isExpanded) {
                                                    viewModel.toggleReplies(comment.commentId)
                                                } else {
                                                    viewModel.loadReplies(comment.commentId)
                                                }
                                            }
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(24.dp)
                                                .height(1.dp)
                                                .background(TextSecondary.copy(alpha = 0.5f))
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (isExpanded) "Yanıtları gizle" else "${comment.repliesCount} yanıtı gör",
                                            color = LinkerPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Render nested replies
                                AnimatedVisibility(
                                    visible = isExpanded,
                                    enter = fadeIn(),
                                    exit = fadeOut()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(start = 44.dp, top = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        if (replies.isEmpty()) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.padding(vertical = 4.dp)
                                            ) {
                                                CircularProgressIndicator(
                                                    color = LinkerPrimary,
                                                    modifier = Modifier.size(14.dp),
                                                    strokeWidth = 1.5.dp
                                                )
                                                Text("Yanıtlar yükleniyor...", color = TextSecondary, fontSize = 11.sp)
                                            }
                                        } else {
                                            replies.forEach { reply ->
                                                CommentItem(
                                                    comment = reply,
                                                    isReply = true,
                                                    onReplyClick = { viewModel.setReplyTo(comment) },
                                                    onLikeClick = { viewModel.toggleLike(reply.commentId) },
                                                    onEditClick = { viewModel.setEditComment(reply) },
                                                    onDeleteClick = { viewModel.deleteComment(reply.commentId) },
                                                    onHistoryClick = { viewModel.loadCommentHistory(reply.commentId) }
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

            // Modern Input Bar pinned at bottom
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkGray)
                    .border(1.dp, GlassCardBorder)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Reply / Edit Banner
                if (uiState.replyToComment != null || uiState.editComment != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (uiState.editComment != null) "✏️ Yorum düzenleniyor"
                            else "↩️ Yanıtlanıyor: @${uiState.replyToComment?.author?.username}",
                            color = LinkerPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        IconButton(
                            onClick = {
                                viewModel.setReplyTo(null)
                                viewModel.setEditComment(null)
                                commentText = ""
                            },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "İptal",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Input Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextField(
                        value = commentText,
                        onValueChange = { commentText = it },
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Black.copy(alpha = 0.6f))
                            .border(1.dp, GlassCardBorder, RoundedCornerShape(24.dp)),
                        placeholder = {
                            Text(
                                text = if (uiState.replyToComment != null) "Yanıtını yaz..." else "Bir yorum ekle...",
                                color = TextSecondary,
                                fontSize = 14.sp
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
                        singleLine = false,
                        maxLines = 4
                    )

                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                if (commentText.isNotBlank()) Brush.horizontalGradient(LinkerBrandGradient)
                                else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.1f), Color.White.copy(alpha = 0.1f)))
                            )
                            .clickable(enabled = commentText.isNotBlank() && !uiState.isSending) {
                                viewModel.submitComment(commentText.trim())
                                commentText = ""
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (uiState.isSending) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Gönder",
                                tint = if (commentText.isNotBlank()) Color.White else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Comment Edit History Modal
    if (uiState.commentHistory != null) {
        val historySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { viewModel.clearCommentHistory() },
            sheetState = historySheetState,
            containerColor = DarkGray,
            dragHandle = { BottomSheetDefaults.DragHandle(color = TextSecondary) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Düzenleme Geçmişi",
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                val history = uiState.commentHistory ?: emptyList()
                LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(history) { version ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Black.copy(alpha = 0.4f))
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Versiyon ${version.version}",
                                    color = LinkerPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = commentHistoryDateFormat.format(Date(version.editedAt)),
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = version.content,
                                color = TextPrimary,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CommentItem(
    comment: Comment,
    isReply: Boolean,
    onReplyClick: () -> Unit,
    onLikeClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onHistoryClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Author Avatar
        LinkerAvatar(
            imageUrl = comment.author.profileImageUrl,
            size = if (isReply) 28.dp else 36.dp,
            storyState = StoryState.NONE
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = comment.author.displayName.ifBlank { comment.author.username.ifBlank { "Kullanıcı" } },
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (isReply) 13.sp else 14.sp
                )
                Text(
                    text = "@${comment.author.username}",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
                Text(
                    text = "•",
                    color = TextSecondary,
                    fontSize = 10.sp
                )
                Text(
                    text = commentTimeFormat.format(Date(comment.createdAt)),
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            com.linker.app.presentation.components.LinkerFormattedText(
                text = comment.content,
                color = TextPrimary.copy(alpha = 0.95f),
                fontSize = if (isReply) 13.sp else 14.sp,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Yanıtla",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onReplyClick() }
                )

                if (comment.editCount > 0) {
                    Text(
                        text = "Düzenlendi",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.clickable { onHistoryClick() }
                    )
                }

                Text(
                    text = "Düzenle",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.clickable { onEditClick() }
                )

                Text(
                    text = "Sil",
                    color = ErrorRed.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    modifier = Modifier.clickable { onDeleteClick() }
                )
            }
        }

        // Like Button & Counter
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 2.dp)
        ) {
            IconButton(
                onClick = onLikeClick,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = if (comment.isLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Beğen",
                    tint = if (comment.isLiked) ErrorRed else TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
            if (comment.likesCount > 0) {
                Text(
                    text = comment.likesCount.toString(),
                    color = TextSecondary,
                    fontSize = 10.sp
                )
            }
        }
    }
}
