package com.eventos.banana.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun ConfettiEffect(
    modifier: Modifier = Modifier.fillMaxSize(),
    durationMillis: Long = 4000,
    particleCount: Int = 100,
    onComplete: () -> Unit = {}
) {
    val particles = remember {
        List(particleCount) {
            ConfettiParticle(
                color = ConfettiDefaults.colors.random(),
                startX = Random.nextFloat(), // 0.0 to 1.0
                startY = -0.2f - Random.nextFloat() * 0.5f,
                velocityX = (Random.nextFloat() - 0.5f) * 2f * 300f, // Spread sideways
                velocityY = 200f + Random.nextFloat() * 600f, // Initial down speed
                rotationSpeed = (Random.nextFloat() - 0.5f) * 360f * 2f,
                shape = if (Random.nextBoolean()) ConfettiShape.Rect else ConfettiShape.Circle,
                size = 15f + Random.nextFloat() * 15f
            )
        }
    }

    var time by remember { mutableFloatStateOf(0f) }
    
    LaunchedEffect(Unit) {
        val startTime = withFrameNanos { it }
        while (true) {
             val playTime = withFrameNanos { it } - startTime
             val millis = playTime / 1_000_000
             time = millis / 1000f // seconds
            
            if (millis > durationMillis) {
                onComplete()
                break
            }
        }
    }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        particles.forEach { particle ->
            val t = time
            
             // Physics: x = x0 + vx*t, y = y0 + vy*t + 0.5*g*t^2
            val x = particle.startX * width + particle.velocityX * t
            val y = particle.startY * height + particle.velocityY * t + 0.5f * 1500f * t * t 

            // 3D Rotation Animation (Simulated by scaling width)
            val rotation = (t * particle.rotationSpeed) % 360f
            // Oscillate scaleX between -1 and 1 to look like flipping
            val scaleX = cos(Math.toRadians(rotation.toDouble() * 3)).toFloat() 

            if (y > -50f && y < height + 50f && x > -50f && x < width + 50f) {
                 withTransform({
                     translate(x, y)
                     rotate(rotation)
                     scale(scaleX, 1f)
                 }) {
                     if (particle.shape == ConfettiShape.Rect) {
                         drawRect(
                             color = particle.color,
                             topLeft = Offset(-particle.size / 2, -particle.size / 2),
                             size = Size(particle.size, particle.size * 0.6f)
                         )
                     } else {
                         drawCircle(
                             color = particle.color,
                             radius = particle.size / 2
                         )
                     }
                 }
            }
        }
    }
}

data class ConfettiParticle(
    val color: Color,
    val startX: Float,
    val startY: Float,
    val velocityX: Float,
    val velocityY: Float,
    val rotationSpeed: Float,
    val shape: ConfettiShape,
    val size: Float
)

enum class ConfettiShape {
    Rect, Circle
}

object ConfettiDefaults {
    val colors = listOf(
        Color(0xFFFFC107), // Amber
        Color(0xFF2196F3), // Blue
        Color(0xFF4CAF50), // Green
        Color(0xFFE91E63), // Pink
        Color(0xFF9C27B0), // Purple
        Color(0xFFFF5722), // Orange
        Color(0xFF00BCD4)  // Cyan
    )
}
