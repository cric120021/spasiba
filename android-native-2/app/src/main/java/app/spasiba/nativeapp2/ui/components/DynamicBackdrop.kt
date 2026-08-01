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
fun rememberPaletteColors(imageUrl: String?, defaultDominant: Color = Color(0xFF28183D), defaultDark: Color = Color(0xFF150C24)): Pair<Color, Color> {
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
                            val domInt = palette.getVibrantColor(
                                palette.getDominantColor(defaultDominant.hashCode())
                            )
                            val darkInt = palette.getDarkMutedColor(
                                palette.getDarkVibrantColor(defaultDark.hashCode())
                            )
                            dominantColor = Color(domInt)
                            darkMutedColor = Color(darkInt)
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
                    0.0f to dominantColor.copy(alpha = 0.95f),
                    0.35f to dominantColor.copy(alpha = 0.85f),
                    0.70f to darkMutedColor.copy(alpha = 0.90f),
                    1.0f to darkMutedColor.copy(alpha = 0.95f)
                )
            )
    ) {
        content()
    }
}
