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
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import androidx.compose.ui.draw.clip
import com.eventos.banana.ui.profile.FriendListViewModel
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.foundation.background

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendListScreen(
    currentUserId: String,
    onBack: () -> Unit,
    onUserClick: (String) -> Unit,
    initialTab: Int = 0,
    viewModel: FriendListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    LaunchedEffect(currentUserId) {
        viewModel.observeData(currentUserId)
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
                             placeholder = { Text(stringResource(com.eventos.banana.R.string.friends_search_placeholder)) },
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
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(com.eventos.banana.R.string.common_back))
                        }
                    },
                    actions = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { 
                                searchQuery = ""
                                viewModel.searchUsers("", currentUserId)
                            }) {
                                Icon(Icons.Default.Close, stringResource(com.eventos.banana.R.string.common_close))
                            }
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(com.eventos.banana.R.string.friends_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(com.eventos.banana.R.string.common_back))
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, stringResource(com.eventos.banana.R.string.home_cd_search))
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
                            stringResource(com.eventos.banana.R.string.friends_search_results), 
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) 
                    }
                    
                    // 1. Found within Friends
                    if (uiState.friends.isNotEmpty()) {
                        item { Text(stringResource(com.eventos.banana.R.string.friends_your_friends), color = MaterialTheme.colorScheme.primary) }
                        items(uiState.friends) { friend ->
                            FriendItem(user = friend, onClick = { onUserClick(friend.uid) }, action = null)
                        }
                    }

                    // 2. Global Results
                    if (uiState.searchResults.isNotEmpty()) {
                        item { Spacer(Modifier.height(16.dp)) }
                        item { Text(stringResource(com.eventos.banana.R.string.friends_community), color = MaterialTheme.colorScheme.primary) }
                        items(uiState.searchResults) { user ->
                            FriendItem(
                                user = user,
                                onClick = { onUserClick(user.uid) },
                                action = {
                                    com.eventos.banana.ui.components.BananaButton(
                                        onClick = { viewModel.sendFriendRequest(currentUserId, user.uid) },
                                        text = stringResource(com.eventos.banana.R.string.friends_add),
                                        modifier = Modifier.width(100.dp)
                                    )
                                }
                            )
                        }
                    }
                    
                    if (uiState.friends.isEmpty() && uiState.searchResults.isEmpty() && !uiState.isLoading) {
                        item { 
                            Text(
                                stringResource(com.eventos.banana.R.string.friends_no_results),
                                modifier = Modifier.padding(top = 24.dp).fillMaxWidth(),
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            ) 
                        }
                    }
                }

            } else {
                // 📋 TABS VIEW (Friends / Requests / Suggestions)
                var selectedTab by remember { mutableIntStateOf(initialTab) }
                // 🔢 Paginación: mostrar de 30 en 30
                var displayLimit by remember { mutableIntStateOf(30) }
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = MaterialTheme.colorScheme.primary // Gold/Primary
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0, 
                        onClick = { selectedTab = 0 }, 
                        text = { Text(stringResource(com.eventos.banana.R.string.friends_tab_friends), fontWeight = if(selectedTab == 0) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal) },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Tab(
                        selected = selectedTab == 1, 
                        onClick = { selectedTab = 1 }, 
                        text = { Text(stringResource(com.eventos.banana.R.string.friends_tab_requests), fontWeight = if(selectedTab == 1) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal) },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Tab(
                        selected = selectedTab == 2, 
                        onClick = { selectedTab = 2 }, 
                        text = { Text(stringResource(com.eventos.banana.R.string.friends_tab_suggestions), fontWeight = if(selectedTab == 2) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal) },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
    
                if (uiState.isLoading) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        repeat(3) {
                            com.eventos.banana.ui.components.SkeletonCard()
                        }
                        
                        var showFallback by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            kotlinx.coroutines.delay(2000)
                            showFallback = true
                        }
                        if (showFallback) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        when (selectedTab) {
                            0 -> { // Friends
                                val visibleFriends = uiState.friends.take(displayLimit)
                                items(visibleFriends) { friend ->
                                    var showRemoveDialog by remember { mutableStateOf(false) }

                                    FriendItem(
                                        user = friend,
                                        onClick = { onUserClick(friend.uid) },
                                        action = {
                                            IconButton(onClick = { showRemoveDialog = true }) {
                                                Icon(
                                                    androidx.compose.material.icons.Icons.Default.Close,
                                                    contentDescription = "Eliminar amigo",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    )

                                    if (showRemoveDialog) {
                                        AlertDialog(
                                            onDismissRequest = { showRemoveDialog = false },
                                            title = { Text("Eliminar amigo") },
                                            text = { Text("¿Estás seguro de que deseas eliminar a ${friend.nickname} de tu lista de amigos?") },
                                            confirmButton = {
                                                TextButton(
                                                    onClick = {
                                                        viewModel.removeFriend(currentUserId, friend.uid)
                                                        showRemoveDialog = false
                                                    }
                                                ) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
                                            },
                                            dismissButton = {
                                                TextButton(onClick = { showRemoveDialog = false }) { Text("Cancelar") }
                                            }
                                        )
                                    }
                                }
                                if (uiState.friends.size > displayLimit) {
                                    item {
                                        OutlinedButton(
                                            onClick = { displayLimit += 30 },
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                                        ) {
                                            Text("Cargar más (${uiState.friends.size - displayLimit} restantes)")
                                        }
                                    }
                                }
                                if (uiState.friends.isEmpty()) {
                                    item { 
                                        Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                                            Text(stringResource(com.eventos.banana.R.string.friends_empty_friends), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
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
                                                text = stringResource(com.eventos.banana.R.string.friends_accept),
                                                modifier = Modifier.width(100.dp).height(36.dp)
                                            )
                                        }
                                    )
                                }
                                 if (uiState.requests.isEmpty()) {
                                    item { 
                                        Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                                            Text(stringResource(com.eventos.banana.R.string.friends_empty_requests), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
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
                                                text = stringResource(com.eventos.banana.R.string.friends_add),
                                                modifier = Modifier.width(120.dp).height(36.dp)
                                            )
                                        }
                                    )
                                }
                                 if (uiState.suggestions.isEmpty()) {
                                    item {
                                        Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(stringResource(com.eventos.banana.R.string.friends_empty_suggestions), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                            Spacer(Modifier.height(8.dp))
                                            Text(stringResource(com.eventos.banana.R.string.friends_invite), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        elevation = CardDefaults.cardElevation(0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.5f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = user.profilePictureUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(50.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        user.nickname, 
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    )
                    if (user.isGold) {
                        Spacer(Modifier.width(4.dp))
                        Text("👑", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                // Optional: Common friends count or location could go here
                val commune = user.commune // Assuming it's String or String?
                if (!commune.isNullOrEmpty()) {
                    Text(
                        commune, 
                        style = MaterialTheme.typography.bodySmall, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (action != null) {
                Spacer(Modifier.width(8.dp))
                action()
            }
        }
    }
}
