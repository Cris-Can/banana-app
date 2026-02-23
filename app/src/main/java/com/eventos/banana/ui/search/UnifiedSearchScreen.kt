package com.eventos.banana.ui.search

import androidx.hilt.navigation.compose.hiltViewModel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import com.eventos.banana.ui.util.*
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.eventos.banana.domain.model.Event
import com.eventos.banana.domain.model.EventType
import com.eventos.banana.domain.model.UserProfile
import com.eventos.banana.ui.event.EventListViewModel
import com.eventos.banana.ui.profile.UserViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedSearchScreen(
    navController: NavController,
    eventListViewModel: EventListViewModel = hiltViewModel(),
    userViewModel: UserViewModel = hiltViewModel(),
    currentUserId: String
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val eventsTabLabel = stringResource(com.eventos.banana.R.string.search_tab_events)
    val usersTabLabel = stringResource(com.eventos.banana.R.string.search_tab_users)
    val tabs = listOf(eventsTabLabel, usersTabLabel)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(stringResource(com.eventos.banana.R.string.search_placeholder)) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                        ),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(com.eventos.banana.R.string.common_clear))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(com.eventos.banana.R.string.common_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTabIndex) {
                0 -> EventsSearchTab(
                    query = searchQuery,
                    viewModel = eventListViewModel,
                    onEventClick = { eventId -> navController.navigate("event_detail/$eventId") }
                )
                1 -> UsersSearchTab(
                    query = searchQuery,
                    viewModel = userViewModel,
                    currentUserId = currentUserId,
                    onUserClick = { userId -> 
                         if (userId == currentUserId) {
                             navController.navigate("profile")
                         } else {
                             navController.navigate("public_profile/$userId")
                         }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EventsSearchTab(
    query: String,
    viewModel: EventListViewModel,
    onEventClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedEventType by remember { mutableStateOf<EventType?>(null) }
    
    // Simple filter logic for demonstration (in real app, use Firestore queries)
    val filteredEvents = remember(uiState, query, selectedEventType) {
        if (uiState is com.eventos.banana.domain.model.EventListUiState.Success) {
            (uiState as com.eventos.banana.domain.model.EventListUiState.Success).events.filter { event ->
                val matchesQuery = query.isBlank() || 
                    event.title.contains(query, ignoreCase = true) ||
                    event.description.contains(query, ignoreCase = true) ||
                    event.region.contains(query, ignoreCase = true) ||
                    event.commune.contains(query, ignoreCase = true)
                
                val matchesType = selectedEventType == null || event.eventType == selectedEventType
                
                matchesQuery && matchesType
            }
        } else {
            emptyList()
        }
    }

    Column {
        // Event Type Filter Chips
        FlowRow(
            modifier = Modifier.padding(8.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
             FilterChip(
                selected = selectedEventType == null,
                onClick = { selectedEventType = null },
                label = { Text(stringResource(com.eventos.banana.R.string.common_all)) }
            )
            EventType.values().forEach { type ->
                FilterChip(
                    selected = selectedEventType == type,
                    onClick = { selectedEventType = type },
                    label = { Text("${type.emoji} ${type.localizedName()}") }
                )
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(filteredEvents) { event ->
                EventSearchCard(event, onEventClick)
            }
        }
    }
}

@Composable
fun EventSearchCard(event: Event, onClick: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick(event.id) },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
             if (!event.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = event.imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(16.dp))
            }
            
            Column {
                Text("${event.title} ${event.eventType.emoji}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${event.commune}, ${event.region}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun UsersSearchTab(
    query: String,
    viewModel: UserViewModel,
    currentUserId: String,
    onUserClick: (String) -> Unit
) {
    var users by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    val scope = rememberCoroutineScope()
    
    // Debounce search or immediate for demo
    LaunchedEffect(query) {
        if (query.length >= 3) {
            users = viewModel.searchUsers(query, currentUserId) 
        } else {
             users = emptyList()
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(users) { user ->
            UserSearchCard(user) { onUserClick(user.uid) }
        }
    }
}

@Composable
fun UserSearchCard(user: UserProfile, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
             AsyncImage(
                model = user.profilePictureUrl ?: "https://via.placeholder.com/150",
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))
            Text(user.nickname ?: stringResource(com.eventos.banana.R.string.common_user), style = MaterialTheme.typography.bodyLarge)
        }
    }
}
