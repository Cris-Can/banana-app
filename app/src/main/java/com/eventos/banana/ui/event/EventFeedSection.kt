package com.eventos.banana.ui.event

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.MoreVert // Explicit import
import androidx.compose.material3.*
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.eventos.banana.domain.model.FeedPost
import androidx.hilt.navigation.compose.hiltViewModel
import com.eventos.banana.ui.home.FeedViewModel
import com.eventos.banana.ui.home.FeedUiState

@Composable
fun EventFeedSection(
    eventId: String,
    currentUserId: String, // Restored parameter
    onUserClick: (String) -> Unit,
    viewModel: FeedViewModel = hiltViewModel<FeedViewModel, FeedViewModel.Factory>(
        creationCallback = { factory -> factory.create(eventId) }
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    var postContent by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // LISTA DE POSTS - usando Box para forzar visibilidad
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (uiState.posts.isEmpty()) {
                // Placeholder cuando no hay posts
                Text(
                    stringResource(com.eventos.banana.R.string.feed_no_posts),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Gray
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(
                        count = uiState.posts.size,
                        key = { index -> uiState.posts[index].id }
                    ) { index ->
                        val post = uiState.posts[index]
                        PostItem(
                            post = post,
                            currentUserId = currentUserId,
                            onUserClick = { onUserClick(post.userId) },
                            onReply = { viewModel.setReplyingTo(post) },
                            onBlock = { viewModel.blockUser(currentUserId, post.userId) },
                            onReport = { reason -> viewModel.reportPost(currentUserId, post, reason) }
                        )
                    }
                }
            }
        }

        uiState.error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (uiState.isLoading || uiState.isUploading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        // REPLY BANNER
        uiState.replyingTo?.let { replyingPost ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Respondiendo a @${replyingPost.userNickname}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.cancelReply() }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cancelar", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // INPUT AREA
        HorizontalDivider()
        Row(
            modifier = Modifier
                .navigationBarsPadding() // Fix system navbar overlap
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { launcher.launch("image/*") }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(com.eventos.banana.R.string.feed_add_photo))
            }

            OutlinedTextField(
                value = postContent,
                onValueChange = { postContent = it },
                placeholder = { Text(stringResource(com.eventos.banana.R.string.feed_write_something)) },
                modifier = Modifier.weight(1f),
                maxLines = 3
            )

            FilledIconButton(
                onClick = {
                    scope.launch {
                        val bytes = selectedImageUri?.let {
                            context.contentResolver.openInputStream(it)?.readBytes()
                        }
                        viewModel.createPost(currentUserId, postContent, bytes)
                        postContent = ""
                        selectedImageUri = null
                    }
                },
                modifier = Modifier.padding(start = 4.dp),
                enabled = (postContent.isNotBlank() || selectedImageUri != null) && !uiState.isUploading,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send, 
                    contentDescription = stringResource(com.eventos.banana.R.string.common_send)
                )
            }
        }

        // PREVIEW IMAGEN SELECCIONADA (E5 Fix)
        selectedImageUri?.let { uri ->
            Box(
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                    .size(100.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            ) {
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Surface(
                    onClick = { selectedImageUri = null },
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.5f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remover",
                        modifier = Modifier.size(20.dp).padding(4.dp),
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun PostItem(
    post: FeedPost,
    currentUserId: String,
    onUserClick: () -> Unit,
    onReply: () -> Unit,
    onBlock: () -> Unit,
    onReport: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    post.userNickname,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onUserClick() }
                )
                if (post.isUserVerified) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = stringResource(com.eventos.banana.R.string.feed_verified),
                        tint = Color(0xFF2196F3),
                        modifier = Modifier
                            .size(16.dp)
                            .padding(start = 4.dp)
                    )
                }

                Spacer(Modifier.weight(1f))

                // 🛡️ Post Options (Round 49)
                if (post.userId != currentUserId) {
                    var showMenu by remember { mutableStateOf(false) }
                    var showReportDialog by remember { mutableStateOf(false) }

                    Box {
                        IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp)) {
                            // Use imported MoreVert or fully qualified
                            Icon(Icons.Filled.MoreVert, stringResource(com.eventos.banana.R.string.public_profile_options), tint = Color.Gray)
                        }

                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(com.eventos.banana.R.string.feed_report)) },
                                onClick = { showMenu = false; showReportDialog = true }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(com.eventos.banana.R.string.feed_block_user)) },
                                onClick = { showMenu = false; onBlock() }
                            )
                        }
                    }

                    if (showReportDialog) {
                        AlertDialog(
                            onDismissRequest = { showReportDialog = false },
                            title = { Text(stringResource(com.eventos.banana.R.string.feed_report_post)) },
                            text = { Text(stringResource(com.eventos.banana.R.string.feed_report_why)) },
                            confirmButton = {
                                TextButton(onClick = {
                                    onReport("Contenido Inapropiado")
                                    showReportDialog = false
                                }) { Text(stringResource(com.eventos.banana.R.string.feed_inappropriate), color = MaterialTheme.colorScheme.error) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showReportDialog = false }) { Text(stringResource(com.eventos.banana.R.string.common_cancel)) }
                            }
                        )
                    }
                }
            }

            // QUOTED CONTENT (Reply)
            if (post.replyToNickname != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.padding(8.dp)) {
                        Text(
                            "@${post.replyToNickname}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            post.replyToContent ?: "Imagen",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (post.content.isNotBlank()) {
                Text(post.content, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
            }

            post.imageUrl?.let { url ->
                var showFullscreenImage by remember { mutableStateOf(false) }

                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(url)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Post image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { showFullscreenImage = true }
                )

                // Dialog con imagen fullscreen
                if (showFullscreenImage) {
                    Dialog(onDismissRequest = { showFullscreenImage = false }) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(url)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = stringResource(com.eventos.banana.R.string.feed_full_image),
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable { showFullscreenImage = false }
                            )
                        }
                    }
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { onReply() },
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Responder", style = MaterialTheme.typography.labelLarge)
                }
                
                Text(
                    text = post.timestampAsDate?.let { java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault()).format(it) } ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
        }
    }
}
