package com.eventos.banana.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eventos.banana.R
import com.eventos.banana.domain.model.EventType
import com.eventos.banana.domain.model.DateFilter
import com.eventos.banana.ui.util.localizedName

@Composable
fun FilterBar(
    selectedCategory: EventType?,
    selectedDateFilter: DateFilter,
    searchRadiusKm: Int,
    onCategoryClick: (EventType?) -> Unit,
    onDateFilterClick: (DateFilter) -> Unit,
    onRadiusClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // ---------- FILTRO CATEGORÍAS (Horizontales) ----------
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { onCategoryClick(null) },
                    label = { Text(stringResource(R.string.home_filter_all)) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        selectedContainerColor = MaterialTheme.colorScheme.onSurface,
                        selectedLabelColor = MaterialTheme.colorScheme.surface
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selectedCategory == null,
                        borderColor = MaterialTheme.colorScheme.outlineVariant,
                        selectedBorderColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
            
            items(EventType.values().toList(), key = { it.name }) { type ->
                val isSelected = selectedCategory == type
                FilterChip(
                    selected = isSelected,
                    onClick = { onCategoryClick(type) },
                    label = { Text("${type.emoji} ${type.localizedName()}") },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surface, // Clean unselected
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    ),
                     border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = MaterialTheme.colorScheme.outlineVariant, // Subtle border
                        selectedBorderColor = MaterialTheme.colorScheme.primary // No border or matching color
                    )
                )
            }
        }
        
        HorizontalDivider()

        // ---------- FILTRO FECHA ----------
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 📍 Radius Filter
            item {
                FilterChip(
                    selected = true, // Always active if using GPS
                    onClick = onRadiusClick,
                    label = { Text("📍 ${searchRadiusKm} km") },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.primary,
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = true,
                        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                )
            }

            items(DateFilter.values().toList(), key = { it.name }) { filter ->
                FilterChip(
                    selected = selectedDateFilter == filter,
                    onClick = { onDateFilterClick(filter) },
                    label = { Text(filter.localizedName()) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                )
            }
        }
        HorizontalDivider()
    }
}
