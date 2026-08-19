package app.spasiba.nativeapp2.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.spasiba.nativeapp2.ui.theme.AccentTeal

@Composable
fun EqualizerWaveformAnimation(isPlaying: Boolean) {
    val transition = rememberInfiniteTransition(label = "equalizer")

    val bar1Height by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar1"
    )

    val bar2Height by transition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar2"
    )

    val bar3Height by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar3"
    )

    Row(
        modifier = Modifier
            .width(16.dp)
            .height(16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        val h1 = if (isPlaying) bar1Height else 0.3f
        val h2 = if (isPlaying) bar2Height else 0.6f
        val h3 = if (isPlaying) bar3Height else 0.4f

        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight(h1)
                .clip(RoundedCornerShape(2.dp))
                .background(AccentTeal)
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight(h2)
                .clip(RoundedCornerShape(2.dp))
                .background(AccentTeal)
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight(h3)
                .clip(RoundedCornerShape(2.dp))
                .background(AccentTeal)
        )
    }
}
