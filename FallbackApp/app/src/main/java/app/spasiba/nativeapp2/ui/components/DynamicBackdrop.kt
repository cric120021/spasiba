package app.spasiba.nativeapp2.ui.components

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberPaletteColors(
    imageUrl: String?,
    defaultDominant: Color = Color(0xFF1E1C1A),
    defaultDark: Color = Color(0xFF0F0E0C)
): Pair<Color, Color> {
    val context = LocalContext.current
    var dominantColor by remember(imageUrl) { mutableStateOf(defaultDominant) }
    var darkMutedColor by remember(imageUrl) { mutableStateOf(defaultDark) }

    LaunchedEffect(imageUrl) {
        if (!imageUrl.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val loader = ImageLoader(context)
                    val request = ImageRequest.Builder(context)
                        .data(imageUrl)
                        .allowHardware(false)
                        .build()
                    val result = loader.execute(request)
                    if (result is SuccessResult) {
                        val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                        if (bitmap != null) {
                            val palette = Palette.from(bitmap).generate()

                            // Get the most populated swatch (dominant color by pixel count in artwork)
                            val dominantSwatch = palette.dominantSwatch
                                ?: palette.swatches.maxByOrNull { it.population }
                                ?: palette.vibrantSwatch

                            val rawColorInt = dominantSwatch?.rgb ?: palette.getDominantColor(defaultDominant.hashCode())

                            // Extract Hue and Saturation from dominant artwork area
                            val hsv = FloatArray(3)
                            android.graphics.Color.colorToHSV(rawColorInt, hsv)

                            // YouTube Music Ambient Player Brightness Normalization:
                            // 1. Preserve original Hue (hsv[0])
                            // 2. Normalize Saturation to 0.30f - 0.50f for smooth ambient tone
                            hsv[1] = hsv[1].coerceIn(0.25f, 0.50f)
                            // 3. Set Brightness to 0.16f for top gradient (perfect dark ambient mode)
                            hsv[2] = 0.16f

                            val topColorInt = android.graphics.Color.HSVToColor(hsv)

                            // Bottom shadow color (same hue, deeper dark brightness 0.05f)
                            val darkHsv = hsv.clone()
                            darkHsv[2] = 0.05f
                            val bottomColorInt = android.graphics.Color.HSVToColor(darkHsv)

                            dominantColor = Color(topColorInt)
                            darkMutedColor = Color(bottomColorInt)
                        }
                    }
                }
            }
        }
    }

    val animDominant by animateColorAsState(
        targetValue = dominantColor,
        animationSpec = tween(600),
        label = "DominantColorAnim"
    )
    val animDark by animateColorAsState(
        targetValue = darkMutedColor,
        animationSpec = tween(600),
        label = "DarkColorAnim"
    )

    return Pair(animDominant, animDark)
}

@Composable
fun DynamicBackdrop(
    backdropUrl: String? = null,
    progress: Float = 0f,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val (dominantColor, darkMutedColor) = rememberPaletteColors(backdropUrl)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0.0f to dominantColor,
                    0.45f to dominantColor.copy(alpha = 0.90f),
                    0.80f to darkMutedColor,
                    1.0f to Color.Black
                )
            )
    ) {
        content()
    }
}
