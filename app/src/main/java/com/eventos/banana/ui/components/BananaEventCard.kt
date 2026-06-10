package com.eventos.banana.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import com.eventos.banana.ui.util.*
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.eventos.banana.domain.model.Event
import com.eventos.banana.domain.model.EventStatus
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun BananaEventCard(
    event: Event,
    creatorName: String,
    creatorRating: Double,
    creatorRatingCount: Int,
    isCreatorIdentityVerified: Boolean = false,
    onClick: () -> Unit,
    userLocation: com.eventos.banana.domain.model.ExactLocation? = null,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    // Calculate Distance
    val distanceKmString = remember(event.latitude, event.longitude, userLocation) {
        if (event.latitude != null && event.longitude != null && userLocation != null) {
            val distMeters = com.eventos.banana.util.LocationHelper.calculateDistance(
                userLocation.latitude, userLocation.longitude,
                event.latitude, event.longitude
            )
            val km = distMeters / 1000f
            if (km < 1) {
                "${distMeters.toInt()} m"
            } else {
                String.format("%.1f km", km)
            }
        } else null
    }

    with(sharedTransitionScope) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .animateContentSize(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp) // Slightly more elevation for premium feel
        ) {
            Column {
                // 🖼️ Header Image
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    if (!event.imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = event.imageUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                    /*.sharedElement(
                                        sharedContentState = rememberSharedContentState(key = "event_image_${event.id}"),
                                        animatedVisibilityScope = animatedVisibilityScope,
                                        zIndexInOverlay = 1f,
                                        boundsTransform = { _, _ -> 
                                            androidx.compose.animation.core.tween(durationMillis = 500)
                                        }
                                    )*/,
                                contentScale = ContentScale.Crop
                            )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                         // Fallback or Placeholder
                         Text("🍌", style = MaterialTheme.typography.displayLarge)
                    }
                }
                
                // 🏷️ Category Badge
                Surface(
                    modifier = Modifier
                        .padding(12.dp)
                        .align(Alignment.TopEnd),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Text(
                        text = "${event.eventType.emoji} ${event.eventType.localizedName()}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // 🔞 +18 Badge
                if (event.isAdultContent) {
                    Surface(
                        modifier = Modifier
                            .padding(12.dp)
                            .align(Alignment.TopEnd)
                            .padding(top = 32.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f),
                        contentColor = MaterialTheme.colorScheme.onError
                    ) {
                        Text(
                            text = "🔞 +18",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // 🔴 Status Badge (if Closed/Finished)
                if (event.status != EventStatus.OPEN) {
                     Surface(
                        modifier = Modifier
                            .padding(12.dp)
                            .align(Alignment.TopStart),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f),
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ) {
                        Text(
                            text = if (event.status == EventStatus.FULL) stringResource(com.eventos.banana.R.string.event_status_full) else stringResource(com.eventos.banana.R.string.event_status_finished),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // 📝 Content
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp) // Grid Fixed: 12dp
            ) {
                // Title
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = MaterialTheme.typography.titleLarge.lineHeight
                )

                Spacer(Modifier.height(4.dp))

                // Date & Time (High Visibility)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val dateFormat = java.text.SimpleDateFormat("EEE d MMM • HH:mm", Locale("es", "ES"))
                    val dateStr = dateFormat.format(java.util.Date(event.startAt))
                        .uppercase()

                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Location
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${event.commune}, ${event.region}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    
                    if (distanceKmString != null) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ) {
                            Text(
                                text = "📍 $distanceKmString",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                
                // Divider (Subtle)
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )

                // Footer: Creator
                Row(verticalAlignment = Alignment.CenterVertically) {
                     Text(
                        text = stringResource(com.eventos.banana.R.string.event_card_by),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = creatorName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isCreatorIdentityVerified) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Identidad verificada",
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    
                    if (creatorRatingCount > 0) {
                        Spacer(Modifier.weight(1f)) // Push rating to end
                        Icon(
                             Icons.Filled.Star,
                             contentDescription = null,
                             modifier = Modifier.size(12.dp),
                             tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            String.format("%.1f", creatorRating),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
        }
    }
    }
}
