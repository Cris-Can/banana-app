package com.eventos.banana.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import androidx.compose.ui.draw.clip
import com.eventos.banana.viewmodel.PublicProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendListScreen(
    currentUserId: String,
    onBack: () -> Unit,
    onUserClick: (String) -> Unit
) {
    // We can reuse PublicProfileViewModel or create a specific FriendsViewModel.
    // Since PublicProfileViewModel is focused on ONE user, we might need a SessionViewModel-based approach or a new one.
    // But Friend List is usually part of "My Profile".
    // Let's assume we can fetch data directly or use a new ViewModel.
    // For simplicity, let's make a FriendListViewModel.
    
    val viewModel: FriendListViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(currentUserId) {
        viewModel.loadFriends(currentUserId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mis Amigos") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            
            // Tab for Requests vs Friends
            var selectedTab by remember { mutableStateOf(0) }
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Amigos") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Solicitudes") })
            }

            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp)) {
                    if (selectedTab == 0) {
                        items(uiState.friends) { friend ->
                            FriendItem(
                                user = friend,
                                onClick = { onUserClick(friend.uid) },
                                action = null
                            )
                        }
                        if (uiState.friends.isEmpty()) {
                            item { Text("No tienes amigos confirmados aún.", modifier = Modifier.padding(16.dp)) }
                        }
                    } else {
                        items(uiState.requests) { request ->
                            FriendItem(
                                user = request,
                                onClick = { onUserClick(request.uid) },
                                action = {
                                    Button(onClick = { viewModel.acceptRequest(currentUserId, request.uid) }) {
                                        Text("Aceptar")
                                    }
                                }
                            )
                        }
                        if (uiState.requests.isEmpty()) {
                            item { Text("No tienes solicitudes pendientes.", modifier = Modifier.padding(16.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FriendItem(
    user: com.eventos.banana.domain.model.UserProfile,
    onClick: () -> Unit,
    action: (@Composable () -> Unit)?
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onClick() },
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = user.profilePictureUrl,
                contentDescription = null,
                modifier = Modifier.size(50.dp).clip(androidx.compose.foundation.shape.CircleShape),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(user.nickname, style = MaterialTheme.typography.titleMedium)
                // Text(user.email, style = MaterialTheme.typography.bodySmall) // Optional
            }
            if (action != null) {
                action()
            }
        }
    }
}
