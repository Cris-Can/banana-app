package com.eventos.banana.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.random.Random

@Composable
fun WaveformVisualizer(
    modifier: Modifier = Modifier,
    amplitudes: List<Float> = emptyList(),
    color: Color = Color.White,
    seed: String = ""
) {
    // Generate pseudo-random amplitudes if none provided
    val data = if (amplitudes.isEmpty()) {
        val random = Random(seed.hashCode())
        List(30) { random.nextFloat() * 0.8f + 0.1f }
    } else {
        amplitudes
    }

    Canvas(modifier = modifier.height(30.dp).fillMaxWidth()) {
        val width = size.width
        val height = size.height
        val barWidth = width / (data.size * 2f)
        val centerY = height / 2

        data.forEachIndexed { index, amplitude ->
            val x = (index * 2f * barWidth) + barWidth
            val barHeight = amplitude * height
            
            drawLine(
                color = color.copy(alpha = 0.8f),
                start = Offset(x, centerY - barHeight / 2),
                end = Offset(x, centerY + barHeight / 2),
                strokeWidth = barWidth,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
    }
}
