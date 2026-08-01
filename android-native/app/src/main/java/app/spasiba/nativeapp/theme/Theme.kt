package app.spasiba.nativeapp.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Slate950 = Color(0xFF030612)
val Slate900 = Color(0xFF07101B)
val GlassSurface = Color(0xFA07101B)
val GlassBorder = Color(0x1AFFFFFF)

val TextPrimary = Color(0xDEFFFFFF)
val TextMuted = Color(0x99FFFFFF)
val AccentWhite = Color(0xFFFFFFFF)
val AccentBlack = Color(0xFF000000)

private val DarkColorScheme = darkColorScheme(
    primary = Color.White,
    secondary = Color.White,
    background = Slate950,
    surface = Slate900,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun SpasibaTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
