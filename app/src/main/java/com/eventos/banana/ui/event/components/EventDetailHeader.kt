package com.eventos.banana.ui.event.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eventos.banana.domain.model.Event

@Composable
fun EventDetailHeader(
    event: Event,
    isDetailsTab: Boolean,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    isSaved: Boolean,
    onToggleSave: () -> Unit,
    headerBackground: Color,
    onHeaderHeightMeasured: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tabs = listOf("Detalles", "Muro")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(headerBackground)
            .onGloballyPositioned { coordinates ->
                onHeaderHeightMeasured(coordinates.size.height.toFloat())
            }
    ) {
        // Header Content
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                    color = if (isDetailsTab) Color.White else MaterialTheme.colorScheme.onSurface
                )

                // ACTIONS ROW
                Row {
                    IconButton(onClick = onToggleSave) {
                        Icon(
                            imageVector = if (isSaved) Icons.Filled.Star else Icons.Filled.Add,
                            contentDescription = stringResource(com.eventos.banana.R.string.event_detail_cd_save_event),
                            tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val imageLoader = coil.compose.LocalImageLoader.current
                    val shareHelper = remember(imageLoader) { com.eventos.banana.util.ShareHelper(context, imageLoader) }
                    IconButton(onClick = { shareHelper.shareEvent(event) }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(com.eventos.banana.R.string.event_detail_cd_share_event),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Text(
                "${event.region} • ${event.commune}",
                color = if (isDetailsTab) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Tabs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = if (isDetailsTab) Color.White else MaterialTheme.colorScheme.primary
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { onTabSelected(index) },
                    text = { Text(title) }
                )
            }
        }
    }
}
