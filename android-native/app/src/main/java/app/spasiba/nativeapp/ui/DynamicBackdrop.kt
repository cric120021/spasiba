package app.spasiba.nativeapp.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import app.spasiba.nativeapp.theme.Slate950

@Composable
fun DynamicBackdrop(
    backdropUrl: String?,
    progress: Float = 0f,
    modifier: Modifier = Modifier
) {
    val animatedAlpha by animateFloatAsState(
        targetValue = if (backdropUrl != null) (1f - progress * 0.7f).coerceIn(0.15f, 1f) else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "backdropAlpha"
    )

    Box(modifier = modifier.fillMaxSize().background(Slate950)) {
        if (!backdropUrl.isNullOrBlank()) {
            AsyncImage(
                model = backdropUrl,
                contentDescription = "Category Backdrop",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(animatedAlpha)
                    .blur(28.dp)
            )
        }

        // Gradient overlay matching Spasiba web aesthetic
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Slate950.copy(alpha = 0.85f),
                            Slate950
                        )
                    )
                )
        )
    }
}
