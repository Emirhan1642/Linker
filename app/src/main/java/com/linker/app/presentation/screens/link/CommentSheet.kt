package com.linker.app.presentation.screens.link

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import com.linker.app.domain.model.Comment
import com.linker.app.presentation.theme.DarkGray
import com.linker.app.presentation.theme.TextPrimary
import com.linker.app.presentation.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        } else if (uiState.replyToComment == null) {
            commentText = ""
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkGray,
        dragHandle = { BottomSheetDefaults.DragHandle(color = TextSecondary) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            Text(
                text = "Yorumlar",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )

            Divider(color = Color.White.copy(alpha = 0.1f))

            if (uiState.isLoading) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            } else if (uiState.comments.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("Henüz yorum yok.", color = TextSecondary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    items(uiState.comments, key = { it.commentId }) { comment ->
                        CommentItem(
                            comment = comment,
                            onReplyClick = { viewModel.setReplyTo(comment) },
                            onLikeClick = { viewModel.toggleLike(comment.commentId) },
                            onEditClick = { viewModel.setEditComment(comment) },
                            onDeleteClick = { viewModel.deleteComment(comment.commentId) },
                            onHistoryClick = { viewModel.loadCommentHistory(comment.commentId) }
                        )
                    }
                }
            }

            // Input Area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .padding(16.dp)
            ) {
                if (uiState.replyToComment != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Yanıtlanıyor: ${uiState.replyToComment?.author?.displayName}",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel reply",
                            tint = TextSecondary,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { viewModel.setReplyTo(null) }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                if (uiState.editComment != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Yorum düzenleniyor",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel edit",
                            tint = TextSecondary,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { viewModel.setEditComment(null) }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = commentText,
                        onValueChange = { commentText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Yorum yaz...", color = TextSecondary) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = DarkGray,
                            unfocusedContainerColor = DarkGray,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = MaterialTheme.shapes.medium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (commentText.isNotBlank()) {
                                viewModel.submitComment(commentText)
                                commentText = ""
                            }
                        },
                        enabled = commentText.isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (commentText.isNotBlank()) MaterialTheme.colorScheme.primary else TextSecondary
                        )
                    }
                }
            }
        }
    }

    // Show History Sheet
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
                    .padding(16.dp)
            ) {
                Text(
                    text = "Düzenleme Geçmişi",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                val history = uiState.commentHistory ?: emptyList()
                LazyColumn {
                    items(history) { version ->
                        Column(modifier = Modifier.padding(bottom = 16.dp)) {
                            Text(
                                text = "Versiyon ${version.version}",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            Text(
                                text = version.content,
                                color = TextPrimary,
                                fontSize = 15.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Text(
                                text = commentHistoryDateFormat.format(Date(version.editedAt)),
                                color = TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp)
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
    onReplyClick: () -> Unit,
    onLikeClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onHistoryClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(Color.Gray, shape = MaterialTheme.shapes.small)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.author.displayName,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = commentTimeFormat.format(Date(comment.createdAt)),
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = comment.content,
                color = TextPrimary,
                fontSize = 15.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Yanıtla",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { onReplyClick() }
                )
                
                if (comment.editCount > 0) {
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Düzenlendi",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.clickable { onHistoryClick() }
                    )
                }

                // TODO: Show Edit/Delete only if current user is author
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Düzenle",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { onEditClick() }
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Sil",
                    color = Color.Red.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { onDeleteClick() }
                )
            }
        }
        
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = if (comment.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Like",
                tint = if (comment.isLiked) Color.Red else TextSecondary,
                modifier = Modifier
                    .size(20.dp)
                    .clickable { onLikeClick() }
            )
            if (comment.likesCount > 0) {
                Text(
                    text = comment.likesCount.toString(),
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}
