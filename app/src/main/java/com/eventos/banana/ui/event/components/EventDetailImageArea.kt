package com.eventos.banana.ui.event.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.eventos.banana.ui.components.shimmerEffect

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun EventDetailImageArea(
    imageUrl: String?,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val imageModifier = Modifier
        .fillMaxWidth()
        .height(350.dp) // Taller to extend behind header

    if (!imageUrl.isNullOrBlank()) {
        var showImageDialog by remember { mutableStateOf(false) }

        Box(modifier = imageModifier) {
            with(sharedTransitionScope) {
                SubcomposeAsyncImage(
                    model = imageUrl,
                    contentDescription = "Foto del evento",
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { showImageDialog = true },
                    contentScale = ContentScale.Crop,
                    loading = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .shimmerEffect()
                        )
                    }
                )
            }

            // Bottom Gradient for text readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)),
                            startY = 500f // Start lower
                        )
                    )
            )

            // Full Screen Image Dialog
            if (showImageDialog) {
                androidx.compose.ui.window.Dialog(
                    onDismissRequest = { showImageDialog = false },
                    properties = androidx.compose.ui.window.DialogProperties(
                        usePlatformDefaultWidth = false
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                    ) {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = "Foto completa",
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable { showImageDialog = false },
                            contentScale = ContentScale.Fit
                        )
                        IconButton(
                            onClick = { showImageDialog = false },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    } else {
        // Placeholder
        Box(
            modifier = imageModifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text("🍌", style = MaterialTheme.typography.displayLarge)
        }
    }
}
