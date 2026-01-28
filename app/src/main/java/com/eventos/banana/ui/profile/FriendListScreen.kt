package com.eventos.banana.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import androidx.compose.ui.draw.clip
import com.eventos.banana.viewmodel.PublicProfileViewModel
import com.eventos.banana.viewmodel.FriendListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendListScreen(
    currentUserId: String,
    onBack: () -> Unit,
    onUserClick: (String) -> Unit
) {
    val viewModel: FriendListViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    LaunchedEffect(currentUserId) {
        viewModel.loadData(currentUserId)
    }

    Scaffold(
        topBar = {
            if (isSearchActive) {
                TopAppBar(
                    title = {
                         TextField(
                             value = searchQuery,
                             onValueChange = { 
                                 searchQuery = it
                                 viewModel.searchUsers(it, currentUserId)
                             },
                             placeholder = { Text("Buscar personas...") },
                             singleLine = true,
                             modifier = Modifier.fillMaxWidth(),
                             colors = TextFieldDefaults.colors(
                                focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                            )
                         )
                    },
                    navigationIcon = {
                        IconButton(onClick = { 
                            isSearchActive = false
                            searchQuery = ""
                            viewModel.searchUsers("", currentUserId)
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                        }
                    },
                    actions = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { 
                                searchQuery = ""
                                viewModel.searchUsers("", currentUserId)
                            }) {
                                Icon(Icons.Default.Close, "Borrar")
                            }
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("Amigos y Comunidad") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, "Buscar")
                        }
                    }
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            
            if (isSearchActive && searchQuery.isNotEmpty()) {
                // 🔍 SEARCH RESULTS VIEW
                LazyColumn(contentPadding = PaddingValues(16.dp)) {
                    item { 
                        Text(
                            "Resultados de búsqueda", 
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) 
                    }
                    
                    // 1. Found within Friends
                    if (uiState.friends.isNotEmpty()) {
                        item { Text("Tus amigos", color = MaterialTheme.colorScheme.primary) }
                        items(uiState.friends) { friend ->
                            FriendItem(user = friend, onClick = { onUserClick(friend.uid) }, action = null)
                        }
                    }

                    // 2. Global Results
                    if (uiState.searchResults.isNotEmpty()) {
                        item { Spacer(Modifier.height(16.dp)) }
                        item { Text("Comunidad", color = MaterialTheme.colorScheme.primary) }
                        items(uiState.searchResults) { user ->
                            FriendItem(
                                user = user,
                                onClick = { onUserClick(user.uid) },
                                action = {
                                    com.eventos.banana.ui.components.BananaButton(
                                        onClick = { viewModel.sendFriendRequest(currentUserId, user.uid) },
                                        text = "Agregar",
                                        modifier = Modifier.width(100.dp)
                                    )
                                }
                            )
                        }
                    }
                    
                    if (uiState.friends.isEmpty() && uiState.searchResults.isEmpty() && !uiState.isLoading) {
                        item { 
                            Text(
                                "No se encontraron usuarios.",
                                modifier = Modifier.padding(top = 24.dp).fillMaxWidth(),
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            ) 
                        }
                    }
                }

            } else {
                // 📋 TABS VIEW (Friends / Requests / Suggestions)
                var selectedTab by remember { mutableIntStateOf(0) }
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Amigos") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Solicitudes") })
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Sugerencias") })
                }
    
                if (uiState.isLoading) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                } else {
                    LazyColumn(contentPadding = PaddingValues(16.dp)) {
                        when (selectedTab) {
                            0 -> { // Friends
                                items(uiState.friends) { friend ->
                                    FriendItem(user = friend, onClick = { onUserClick(friend.uid) }, action = null)
                                }
                                if (uiState.friends.isEmpty()) {
                                    item { Text("No tienes amigos confirmados aún.", modifier = Modifier.padding(16.dp)) }
                                }
                            }
                            1 -> { // Requests
                                 items(uiState.requests) { request ->
                                    FriendItem(
                                        user = request,
                                        onClick = { onUserClick(request.uid) },
                                        action = {
                                            com.eventos.banana.ui.components.BananaButton(
                                                onClick = { viewModel.acceptRequest(currentUserId, request.uid) },
                                                text = "Aceptar",
                                                modifier = Modifier.width(100.dp)
                                            )
                                        }
                                    )
                                }
                                 if (uiState.requests.isEmpty()) {
                                    item { Text("No tienes solicitudes pendientes.", modifier = Modifier.padding(16.dp)) }
                                }
                            }
                            2 -> { // Suggestions
                                 items(uiState.suggestions) { suggestion ->
                                    FriendItem(
                                        user = suggestion,
                                        onClick = { onUserClick(suggestion.uid) },
                                        action = {
                                            com.eventos.banana.ui.components.BananaButton(
                                                onClick = { viewModel.sendFriendRequest(currentUserId, suggestion.uid) },
                                                text = "Agregar",
                                                modifier = Modifier.width(100.dp)
                                            )
                                        }
                                    )
                                }
                                 if (uiState.suggestions.isEmpty()) {
                                    item {
                                        Column(Modifier.padding(16.dp)) {
                                            Text("No hay sugerencias en tu región por ahora.")
                                            Text("Invita más gente a usar Banana!", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
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
