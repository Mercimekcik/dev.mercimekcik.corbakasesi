package net.perfectdreams.butterscotch.android.theme

import android.app.Activity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import net.perfectdreams.butterscotch.android.Libraries
import net.perfectdreams.butterscotch.android.settings.ButterscotchSettings

private val DarkColorScheme = darkColorScheme(
    primary = ButterscotchPrimary,
    onPrimary = ButterscotchOnPrimary,
    secondary = ButterscotchSecondary,
    onSecondary = ButterscotchOnSecondary,
    tertiary = ButterscotchTertiary,
    onTertiary = ButterscotchOnTertiary,
    background = ButterscotchBackground,
    onBackground = ButterscotchOnBackground,
    surface = ButterscotchSurface,
    onSurface = ButterscotchOnSurface,
    surfaceVariant = ButterscotchSurfaceVariant,
    onSurfaceVariant = ButterscotchOnSurfaceVariant,
    // Material 3's default dark containers are purple tinted, pin them to the neutral greys
    surfaceTint = ButterscotchSurface,
    surfaceDim = ButterscotchBackground,
    surfaceBright = ButterscotchSurfaceVariant,
    surfaceContainerLowest = ButterscotchBackground,
    surfaceContainerLow = ButterscotchSurface,
    surfaceContainer = ButterscotchSurface,
    surfaceContainerHigh = ButterscotchSurfaceVariant,
    surfaceContainerHighest = ButterscotchSurfaceVariant,
    secondaryContainer = ButterscotchPrimaryContainer,
    onSecondaryContainer = ButterscotchOnPrimaryContainer,
    tertiaryContainer = ButterscotchPrimaryContainer,
    onTertiaryContainer = ButterscotchOnPrimaryContainer,
    outline = ButterscotchOutline,
    outlineVariant = ButterscotchSurfaceVariant,
    inverseSurface = ButterscotchOnSurface,
    inverseOnSurface = ButterscotchBackground,
    inversePrimary = ButterscotchPrimaryContainer,
    primaryContainer = ButterscotchPrimaryContainer,
    onPrimaryContainer = ButterscotchOnPrimaryContainer,
)

/** Color scheme for the theme picked in settings; the default theme keeps its hand tuned scheme above. */
fun schemeFor(settings: ButterscotchSettings): ColorScheme = when (settings.themeId) {
    AppThemes.DEFAULT_ID -> DarkColorScheme
    AppThemes.CUSTOM_ID -> if (settings.customTheme.isValid()) settings.customTheme.toColorScheme() else DarkColorScheme
    else -> AppThemes.preset(settings.themeId)?.colors?.toColorScheme() ?: DarkColorScheme
}

@Composable
fun ButterscotchAndroidTheme(
    content: @Composable () -> Unit
) {
    val settings = Libraries.loadSettingsStore(LocalContext.current).settings
    val colorScheme = schemeFor(settings)

    // Light themes need dark status/navigation bar icons, otherwise they vanish on the light background
    val view = LocalView.current
    val lightBars = colorScheme.background.luminance() > 0.5f
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = lightBars
                isAppearanceLightNavigationBars = lightBars
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
