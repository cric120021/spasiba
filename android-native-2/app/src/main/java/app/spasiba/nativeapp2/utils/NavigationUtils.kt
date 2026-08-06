package app.spasiba.nativeapp2.utils

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class AppNavigationType {
    THREE_BUTTON,
    TWO_BUTTON,
    GESTURE_NAVIGATION,
    UNKNOWN
}

object NavigationUtils {

    fun getSystemNavigationType(context: Context): AppNavigationType {
        return try {
            val navMode = Settings.Secure.getInt(context.contentResolver, "navigation_mode", -1)
            when (navMode) {
                0 -> AppNavigationType.THREE_BUTTON
                1 -> AppNavigationType.TWO_BUTTON
                2 -> AppNavigationType.GESTURE_NAVIGATION
                else -> {
                    val resourceId = context.resources.getIdentifier("config_navBarInteractions", "integer", "android")
                    if (resourceId > 0) {
                        when (context.resources.getInteger(resourceId)) {
                            2 -> AppNavigationType.GESTURE_NAVIGATION
                            1 -> AppNavigationType.TWO_BUTTON
                            0 -> AppNavigationType.THREE_BUTTON
                            else -> AppNavigationType.UNKNOWN
                        }
                    } else AppNavigationType.UNKNOWN
                }
            }
        } catch (e: Exception) {
            Log.e("NavigationUtils", "Error detecting system navigation mode", e)
            AppNavigationType.UNKNOWN
        }
    }
}

@Composable
fun rememberNavigationType(): AppNavigationType {
    val context = LocalContext.current
    return remember(context) {
        NavigationUtils.getSystemNavigationType(context)
    }
}

/**
 * Custom Bottom Navigation Padding Rule:
 * - If Gesture/Swipe navigation: exactly 3.dp
 * - Else (3-Button/2-Button navigation): exact system 3-button panel height
 */
@Composable
fun getCustomBottomNavigationPadding(): Dp {
    val context = LocalContext.current
    val navType = remember(context) { NavigationUtils.getSystemNavigationType(context) }
    val systemNavBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    return if (navType == AppNavigationType.GESTURE_NAVIGATION) {
        3.dp
    } else {
        systemNavBarHeight
    }
}
