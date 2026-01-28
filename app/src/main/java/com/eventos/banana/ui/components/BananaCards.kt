package com.eventos.banana.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// 🎨 STANDARDIZED CARDS - FRONTEND BONITO
// Guidelines:
// - Shapes: RoundedCorner (16.dp)
// - Elevation: 2.dp default, 4.dp pressed/active?
// - Padding: Internal 16.dp
// - Colors: Surface or SurfaceVariant depending on hierarchy

private val DefaultBananaCardShape = RoundedCornerShape(16.dp)
private val DefaultBananaCardElevation = 2.dp
private val DefaultBananaContentPadding = 16.dp

@Composable
fun BananaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = DefaultBananaCardShape,
    elevation: Dp = DefaultBananaCardElevation,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentPadding: Dp = DefaultBananaContentPadding,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardModifier = if (onClick != null) {
        modifier.clickable { onClick() }
    } else {
        modifier
    }

    Card(
        modifier = cardModifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        Column(
            modifier = Modifier
                .padding(contentPadding)
                .fillMaxWidth()
        ) {
            content()
        }
    }
}
