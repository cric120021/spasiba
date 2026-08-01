package app.spasiba.nativeapp2.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.random.Random

@Composable
fun AudioVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barColor: Color = Color(0xFF1DB954),
    barCount: Int = 24
) {
    val transition = rememberInfiniteTransition(label = "visualizerTransition")

    val phase1 by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 450, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "phase1"
    )

    val phase2 by transition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 350, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "phase2"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
    ) {
        val width = size.width
        val height = size.height
        val totalSpacing = width * 0.4f
        val barWidth = (width - totalSpacing) / barCount
        val gap = totalSpacing / (barCount - 1)

        for (i in 0 until barCount) {
            val factor = if (i % 2 == 0) phase1 else phase2
            val dynamicHeight = if (isPlaying) {
                val seed = (i * 17) % 100 / 100.0f
                (height * (0.15f + 0.85f * factor * ((i % 3 + 1) / 3.0f))).coerceIn(8.dp.toPx(), height)
            } else {
                6.dp.toPx()
            }

            val x = i * (barWidth + gap)
            val y = (height - dynamicHeight) / 2f

            drawRoundRect(
                color = barColor.copy(alpha = if (isPlaying) 0.85f else 0.3f),
                topLeft = Offset(x, y),
                size = Size(barWidth, dynamicHeight),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
            )
        }
    }
}
