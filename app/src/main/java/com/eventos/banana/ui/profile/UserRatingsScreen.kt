package com.eventos.banana.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eventos.banana.R
import com.eventos.banana.ui.components.BananaCard
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserRatingsScreen(
    viewModel: UserRatingsViewModel,
    billingViewModel: com.eventos.banana.ui.monetization.BillingViewModel,
    onBack: () -> Unit,
    onNavigateToGold: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? android.app.Activity

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.rating_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp)
                )
            } else if (uiState.ratings.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Aún no tienes calificaciones",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "¡Participa en eventos para recibir feedback!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val listState = rememberLazyListState()
                
                // Detect near-end of scroll
                val shouldLoadMore by remember {
                    derivedStateOf {
                        val layoutInfo = listState.layoutInfo
                        val totalItems = layoutInfo.totalItemsCount
                        val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        totalItems > 0 && lastVisibleItem >= totalItems - 5
                    }
                }

                LaunchedEffect(shouldLoadMore) {
                    if (shouldLoadMore && uiState.hasMore && !uiState.isPaginatedLoading) {
                        viewModel.loadRatings(isInitial = false)
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = "Historial de Calificaciones",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            text = "Aquí puedes ver lo que otros opinan de tu participación.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        if (uiState.viewerProfile?.isGold != true) {
                            // Banner de Saldo
                            val expiryDate = if (uiState.creditsExpiry > 0) {
                                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(uiState.creditsExpiry))
                            } else {
                                "-"
                            }
                            
                            BananaCard(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Te quedan ${uiState.credits} créditos",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        if (uiState.credits > 0) {
                                            Text(
                                                text = "Expiran el $expiryDate",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                    if (uiState.credits == 0) {
                                        Button(onClick = { activity?.let { billingViewModel.buyCredits(it) } }) {
                                            Text("Comprar")
                                        }
                                    }
                                }
                            }

                            // Banner Gold
                            BananaCard(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.padding(bottom = 16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Solo Gold ve quién calificó sin gastar créditos.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                                    )
                                    Button(onClick = onNavigateToGold) {
                                        Text("Hazte Gold")
                                    }
                                }
                            }
                        }
                    }

                    items(uiState.ratings) { item ->
                        RatingItemCard(
                            item = item,
                            credits = uiState.credits,
                            isViewerPremium = uiState.viewerProfile?.isGold == true,
                            currentUserId = uiState.viewerProfile?.uid ?: "",
                            onReveal = { viewModel.revealRater(item.rating.ratingId) },
                            onAnonymize = { viewModel.anonymizeMyRating(item.rating.ratingId) },
                            onBuyCredits = { activity?.let { billingViewModel.buyCredits(it) } },
                            onGoGold = onNavigateToGold
                        )
                    }

                    if (uiState.isPaginatedLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RatingItemCard(
    item: RatingWithUser,
    credits: Int,
    isViewerPremium: Boolean,
    currentUserId: String,
    onReveal: () -> Unit,
    onAnonymize: () -> Unit,
    onBuyCredits: () -> Unit,
    onGoGold: () -> Unit
) {
    val rating = item.rating
    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val dateStr = dateFormat.format(Date(rating.timestamp))

    BananaCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val displayName = if (!isViewerPremium && item.fromUserNickname == null) {
                    "🔒 Anónimo"
                } else if (isViewerPremium && rating.isAnonymous) {
                    "🔒 Usuario prefirió ocultar su nombre"
                } else {
                    item.fromUserNickname ?: "Usuario Anónimo"
                }
                
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (displayName.startsWith("🔒")) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                )
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (!isViewerPremium && item.fromUserNickname == null) {
                if (credits > 0) {
                    TextButton(onClick = onReveal, modifier = Modifier.padding(top = 4.dp)) {
                        Text("Revelar (1 crédito)")
                    }
                } else {
                    TextButton(onClick = onBuyCredits, modifier = Modifier.padding(top = 4.dp)) {
                        Text("Comprar créditos $1.990")
                    }
                }
            }
            
            if (rating.fromUserId == currentUserId && !rating.isAnonymous) {
                TextButton(onClick = onAnonymize, modifier = Modifier.padding(top = 4.dp)) {
                    Text("Ocultar nombre (gasta 1 crédito)")
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                repeat(5) { index ->
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = if (index < rating.score.toInt()) Color(0xFFFFC107) else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = rating.score.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (!rating.comment.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = rating.comment,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}
