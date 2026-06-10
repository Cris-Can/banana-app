package com.eventos.banana.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ThemeColorGrid(
    selectedColor: String?,
    onColorSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = listOf(
        "#F44336", "#E91E63", "#9C27B0", "#673AB7", "#3F51B5", "#2196F3",
        "#03A9F4", "#00BCD4", "#009688", "#4CAF50", "#8BC34A", "#FFEB3B",
        "#FFC107", "#FF9800", "#FF5722", "#795548", "#9E9E9E", "#607D8B",
        "#FFFFFF", "#000000", "#FFD600", "#FF6B6B", "#4ECDC4", "#E8D5F5"
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        colors.chunked(6).forEach { rowColors ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowColors.forEach { hex ->
                    val isSelected = selectedColor?.uppercase() == hex.uppercase()
                    val composeColor = Color(android.graphics.Color.parseColor(hex))
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(composeColor)
                            .clickable { onColorSelected(hex) }
                            .then(
                                if (isSelected) Modifier.border(2.dp, Color.White, CircleShape) else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            val iconColor = if (hex.uppercase() in listOf("#FFFFFF", "#FFEB3B", "#FFD600")) Color.Black else Color.White
                            Icon(Icons.Default.Check, null, tint = iconColor, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}
