package com.muvusoft.agentfarm.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.muvusoft.agentfarm.core.theme.Palette
import com.muvusoft.agentfarm.core.theme.Tokens

/** The states Material's scheme has no slot for: a running turn and a turn that waits for the user. */
@Immutable
data class StatusColors(val running: Color, val waiting: Color, val waitingContainer: Color, val onWaitingContainer: Color)

val LocalStatusColors = staticCompositionLocalOf { statusOf(Tokens.LIGHT) }

private fun statusOf(p: Palette) =
    StatusColors(Color(p.running), Color(p.waiting), Color(p.waitingContainer), Color(p.onWaitingContainer))

private fun schemeOf(p: Palette, dark: Boolean): ColorScheme {
    val c = { v: Long -> Color(v) }
    return if (dark) {
        darkColorScheme(
            primary = c(p.primary), onPrimary = c(p.onPrimary), primaryContainer = c(p.primaryContainer), onPrimaryContainer = c(p.onPrimaryContainer),
            secondary = c(p.secondary), onSecondary = c(p.onSecondary), secondaryContainer = c(p.secondaryContainer), onSecondaryContainer = c(p.onSecondaryContainer),
            background = c(p.background), onBackground = c(p.onBackground), surface = c(p.background), onSurface = c(p.onBackground),
            surfaceVariant = c(p.surfaceVariant), onSurfaceVariant = c(p.onSurfaceVariant), surfaceTint = c(p.primary),
            surfaceContainerLowest = c(p.containerLowest), surfaceContainerLow = c(p.containerLow), surfaceContainer = c(p.container),
            surfaceContainerHigh = c(p.containerHigh), surfaceContainerHighest = c(p.containerHighest),
            outline = c(p.outline), outlineVariant = c(p.outlineVariant),
            error = c(p.error), onError = c(p.onError), errorContainer = c(p.errorContainer), onErrorContainer = c(p.onErrorContainer),
        )
    } else {
        lightColorScheme(
            primary = c(p.primary), onPrimary = c(p.onPrimary), primaryContainer = c(p.primaryContainer), onPrimaryContainer = c(p.onPrimaryContainer),
            secondary = c(p.secondary), onSecondary = c(p.onSecondary), secondaryContainer = c(p.secondaryContainer), onSecondaryContainer = c(p.onSecondaryContainer),
            background = c(p.background), onBackground = c(p.onBackground), surface = c(p.background), onSurface = c(p.onBackground),
            surfaceVariant = c(p.surfaceVariant), onSurfaceVariant = c(p.onSurfaceVariant), surfaceTint = c(p.primary),
            surfaceContainerLowest = c(p.containerLowest), surfaceContainerLow = c(p.containerLow), surfaceContainer = c(p.container),
            surfaceContainerHigh = c(p.containerHigh), surfaceContainerHighest = c(p.containerHighest),
            outline = c(p.outline), outlineVariant = c(p.outlineVariant),
            error = c(p.error), onError = c(p.onError), errorContainer = c(p.errorContainer), onErrorContainer = c(p.onErrorContainer),
        )
    }
}

private val typography = Typography().let { t ->
    t.copy(
        titleLarge = t.titleLarge.copy(fontSize = 20.sp),
        titleMedium = t.titleMedium.copy(fontSize = Tokens.TITLE_SP.sp, lineHeight = 24.sp),
        bodyLarge = t.bodyLarge.copy(fontSize = Tokens.BODY_LARGE_SP.sp, lineHeight = 23.sp),
        bodyMedium = t.bodyMedium.copy(fontSize = Tokens.BODY_SP.sp, lineHeight = 20.sp),
        bodySmall = t.bodySmall.copy(fontSize = Tokens.META_SP.sp, lineHeight = 18.sp),
        labelSmall = t.labelSmall.copy(fontSize = Tokens.LABEL_SP.sp),
    )
}

/** Inline code and code blocks. */
val CodeFont = FontFamily.Monospace

private val shapes = Shapes(medium = RoundedCornerShape(Tokens.CARD_RADIUS_DP.dp), large = RoundedCornerShape(20.dp))

/**
 * Agent Farm's look, following the device's light or dark live: the activity is not recreated on a theme
 * change (configChanges uiMode, so an open farm page is not reloaded), so the system bars are re-coloured
 * here on every change. `dark` is given only where a scene is drawn in both on purpose.
 */
@Suppress("DEPRECATION")
@Composable
fun AppTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val palette = if (dark) Tokens.DARK else Tokens.LIGHT
    val colors = schemeOf(palette, dark)
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val bar = colors.background.toArgb()
        window.statusBarColor = bar
        window.navigationBarColor = bar
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    CompositionLocalProvider(LocalStatusColors provides statusOf(palette)) {
        MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes, content = content)
    }
}
