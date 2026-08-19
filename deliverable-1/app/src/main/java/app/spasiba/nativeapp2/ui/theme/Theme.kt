package app.spasiba.nativeapp2.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

enum class UiThemeMode(val title: String, val badge: String) {
    MODERN_GLASS("Modern Glass", "GLASS"),
    OLED_DARK("OLED Dark", "OLED"),
    CLASSIC_MIDNIGHT("Classic Midnight", "NAVY"),
    NEON_CYBERPUNK("Neon Cyberpunk", "CYBER")
}

// Colors for default Modern Glass
val UiBackground = Color(0xFF040712)
val UiBackgroundMid = Color(0xFF081325)
val UiBackgroundEnd = Color(0xFF02030A)
val UiSurface = Color(0xFF0C0C0E)
val UiSurfaceElevated = Color(0xFF141418)
val UiBorder = Color(0x1CFFFFFF)

val AccentTeal = Color.White
val AccentTealStrong = Color.White
val AccentGlow = Color(0x33FFFFFF)

val TextStrong = Color(0xFFF8FAFC)
val TextMuted = Color(0xB8E2E8F0)
val TextFaint = Color(0x9994A3B8)

fun getThemeColorScheme(mode: UiThemeMode) = darkColorScheme(
    primary = Color.White,
    secondary = Color.White,
    background = UiBackground,
    surface = UiSurface,
    surfaceVariant = UiSurfaceElevated,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = TextStrong,
    onSurface = TextStrong
)

fun getBackgroundGradient(mode: UiThemeMode): Brush = Brush.verticalGradient(
    colors = listOf(Color.Black, Color.Black)
)

fun getCategoryAccentColor(id: String): Color = when (id.lowercase()) {
    "workout" -> Color(0xFFFB923C)
    "relax" -> Color(0xFF22D3EE)
    "energize" -> Color(0xFFF43F5E)
    "feelgood" -> Color(0xFF84CC16)
    "romance" -> Color(0xFFFB7185)
    "party" -> Color(0xFFD946EF)
    "commute" -> Color(0xFFF87171)
    "sleep" -> Color(0xFF818CF8)
    "focus" -> Color(0xFF38BDF8)
    "gaming" -> Color(0xFFA855F7)
    "podcasts" -> Color(0xFF94A3B8)
    "hiphop" -> Color(0xFFEAB308)
    "rock" -> Color(0xFFEF4444)
    "bollywood" -> Color(0xFFFF007F)
    "chill" -> Color(0xFF2DD4BF)
    "dance" -> Color(0xFF06B6D4)
    else -> AccentTeal
}

@Composable
fun SpasibaNative2Theme(
    mode: UiThemeMode = UiThemeMode.MODERN_GLASS,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = getThemeColorScheme(mode),
        content = content
    )
}

