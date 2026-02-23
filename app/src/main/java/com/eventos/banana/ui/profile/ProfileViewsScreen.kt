package com.eventos.banana.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.eventos.banana.ui.components.BananaButton
import com.eventos.banana.ui.profile.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileViewsScreen(
    profileViewModel: ProfileViewModel,
    currentUserUid: String,
    isGold: Boolean,
    onBack: () -> Unit,
    onNavigateToGold: () -> Unit,
    onUserClick: (String) -> Unit
) {
    val views by profileViewModel.profileViews.collectAsState()
    val uiState by profileViewModel.uiState.collectAsState()

    LaunchedEffect(currentUserUid) {
        profileViewModel.loadProfileViews(currentUserUid)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(com.eventos.banana.R.string.profile_views_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(com.eventos.banana.R.string.common_back_nav))
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            
            if (isGold) {
                // 💎 GOLD USER: Full Access
                if (views.isEmpty()) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text(
                            stringResource(com.eventos.banana.R.string.profile_views_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(16.dp)) {
                        item {
                            Text(
                                stringResource(com.eventos.banana.R.string.profile_views_recent, views.size),
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }
                        
                        items(views) { view ->
                            ProfileViewItemRow(view, onUserClick)
                        }
                    }
                }
            } else {
                // 🔒 FREE USER: Blured Teaser
                Column(Modifier.fillMaxSize()) {
                    // BLURRED FAKE LIST
                    Box(Modifier.weight(1f).blur(10.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            repeat(8) {
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        Modifier.size(50.dp).clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    )
                                    Spacer(Modifier.width(16.dp))
                                    Column {
                                        Box(
                                            Modifier.height(16.dp).width(120.dp)
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Box(
                                            Modifier.height(12.dp).width(80.dp)
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                        )
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.5f))
                            }
                        }
                    }
                }

                // CTA OVERLAY
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha=0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(0.9f).padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                "👁️ " + stringResource(com.eventos.banana.R.string.profile_views_spy_title),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            Text(
                                stringResource(com.eventos.banana.R.string.profile_views_spy_body),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            
                            BananaButton(
                                onClick = onNavigateToGold,
                                text = stringResource(com.eventos.banana.R.string.profile_views_get_gold),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
            
            if (uiState is com.eventos.banana.ui.profile.ProfileUiState.Loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }
}

@Composable
fun ProfileViewItemRow(
    item: com.eventos.banana.ui.profile.ProfileViewModel.ProfileViewItem,
    onClick: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = { onClick(item.user.uid) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = item.user.profilePictureUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
            
            Spacer(Modifier.width(16.dp))
            
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.user.nickname,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                // Time Ago logic (Simple java formatting)
                val timeAgo = android.text.format.DateUtils.getRelativeTimeSpanString(
                    item.timestamp,
                    System.currentTimeMillis(),
                    android.text.format.DateUtils.MINUTE_IN_MILLIS
                )
                
                Text(
                    text = stringResource(com.eventos.banana.R.string.profile_views_seen, timeAgo),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (item.user.isGold) {
                Text("👑")
            }
        }
    }
}
