package com.eventos.banana.ui.ranking

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.eventos.banana.domain.model.UserProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingScreen(
    viewModel: RankingViewModel,
    onBack: () -> Unit,
    onUserClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("🏆 Más Activos", "⭐ Mejor Calificados")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ranking Global", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title, style = MaterialTheme.typography.labelLarge) }
                    )
                }
            }

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.errorMessage != null) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        uiState.errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                val usersToShow = if (selectedTabIndex == 0) uiState.topUsersByScore else uiState.topUsersByRating
                
                if (usersToShow.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("Aún no hay suficientes datos para el ranking.", style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(usersToShow) { index, user ->
                            // INFINITE SCROLL TRIGGER
                            if (index >= usersToShow.size - 3) {
                                LaunchedEffect(index, selectedTabIndex) {
                                    viewModel.loadMore(isScoreMode = selectedTabIndex == 0)
                                }
                            }

                            RankingUserItem(
                                user = user,
                                position = index + 1,
                                isScoreMode = selectedTabIndex == 0,
                                onClick = { onUserClick(user.uid) }
                            )
                        }
                        
                        item {
                            val isLoadingMore = if (selectedTabIndex == 0) uiState.isLoadingMoreScore else uiState.isLoadingMoreRating
                            if (isLoadingMore) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                }
                            } else {
                                val hasMore = if (selectedTabIndex == 0) uiState.hasMoreScore else uiState.hasMoreRating
                                if (!hasMore && usersToShow.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No hay más usuarios por ahora.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
}

@Composable
fun RankingUserItem(
    user: UserProfile,
    position: Int,
    isScoreMode: Boolean,
    onClick: () -> Unit
) {
    // Colores para el Top 3 (Podio)
    val (podiumColor, positionText) = when (position) {
        1 -> Color(0xFFFFD700) to "🥇" // Oro
        2 -> Color(0xFFC0C0C0) to "🥈" // Plata
        3 -> Color(0xFFCD7F32) to "🥉" // Bronce
        else -> MaterialTheme.colorScheme.surfaceVariant to "#$position"
    }

    val isTop3 = position <= 3
    val containerColor = if (isTop3) podiumColor.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface
    val borderColor = if (isTop3) podiumColor else Color.Transparent

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = if (isTop3) androidx.compose.foundation.BorderStroke(2.dp, borderColor) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Posición
            Text(
                text = positionText,
                style = if (isTop3) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(40.dp),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.width(12.dp))

            // Avatar
            if (!user.profilePictureUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = user.profilePictureUrl,
                    contentDescription = "Avatar de ${user.nickname}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        user.nickname.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Info de usuario
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = user.nickname,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (user.isFounder) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("🚀", style = MaterialTheme.typography.bodySmall)
                    } else if (user.isGoldStored) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("✨", style = MaterialTheme.typography.bodySmall)
                    }
                }
                
                if (user.commune != null) {
                    Text(
                        text = user.commune,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Métrica
            Column(horizontalAlignment = Alignment.End) {
                if (isScoreMode) {
                    Text(
                        text = user.score.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text("pts", style = MaterialTheme.typography.labelSmall)
                } else {
                    val avg = if (user.ratingCount > 0) user.ratingSum / user.ratingCount else 0.0
                    Text(
                        text = String.format("%.1f", avg),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFB300)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, "Estrella", tint = Color(0xFFFFB300), modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("(${user.ratingCount})", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
