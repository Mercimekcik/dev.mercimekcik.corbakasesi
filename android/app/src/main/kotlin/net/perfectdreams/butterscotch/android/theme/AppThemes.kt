package net.perfectdreams.butterscotch.android.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import kotlinx.serialization.Serializable
import java.util.Locale

/**
 * The four colors a theme is made of, as "#RRGGBB" strings (that's also how custom themes are stored
 * and exported). Everything else in the Material color scheme is derived from these.
 */
@Serializable
data class ThemeColors(
    val background: String,
    val surface: String,
    val accent: String,
    val text: String,
)

class AppTheme(val id: String, val name: String, val colors: ThemeColors)

object AppThemes {
    const val DEFAULT_ID = "default"
    const val CUSTOM_ID = "custom"

    val presets = listOf(
        // The original neutral dark look; its scheme is the hand tuned one in Theme.kt, these colors are only for the swatches
        AppTheme(DEFAULT_ID, "Koyu", ThemeColors("#0E0E10", "#17171A", "#E6E6E9", "#E6E6E9")),
        AppTheme("amoled", "AMOLED Siyah", ThemeColors("#000000", "#0D0D0E", "#FFFFFF", "#EDEDED")),
        AppTheme("soup", "Çorba", ThemeColors("#1A1210", "#261A16", "#E07A5F", "#F3E6DC")),
        AppTheme("night", "Gece Mavisi", ThemeColors("#0B1220", "#131C2E", "#6EA8FE", "#E3EAF5")),
        AppTheme("forest", "Orman", ThemeColors("#0E1511", "#16211A", "#7CC28A", "#E2EEE5")),
        AppTheme("lavender", "Lavanta", ThemeColors("#120F1A", "#1C1726", "#B79CFF", "#ECE6F7")),
        AppTheme("light", "Açık", ThemeColors("#F2F2F4", "#FFFFFF", "#2B2B30", "#1A1A1D")),
    )

    fun preset(id: String): AppTheme? = presets.firstOrNull { it.id == id }
}

fun parseHexColor(hex: String): Color? {
    val clean = hex.trim().removePrefix("#")
    if (clean.length != 6 || !clean.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
    return Color(0xFF000000 or clean.toLong(16))
}

fun Color.toHex(): String = String.format(Locale.ROOT, "#%06X", toArgb() and 0xFFFFFF)

fun ThemeColors.isValid(): Boolean = listOf(background, surface, accent, text).all { parseHexColor(it) != null }

fun ThemeColors.isDark(): Boolean = (parseHexColor(background) ?: Color.Black).luminance() < 0.5f

/** Builds a full Material color scheme from the four theme colors. */
fun ThemeColors.toColorScheme(): ColorScheme {
    val bg = parseHexColor(background) ?: ButterscotchBackground
    val surface = parseHexColor(surface) ?: ButterscotchSurface
    val accent = parseHexColor(accent) ?: ButterscotchPrimary
    val text = parseHexColor(text) ?: ButterscotchOnSurface

    // Text on the accent color: whichever of near black / white reads better
    val onAccent = if (accent.luminance() > 0.45f) Color(0xFF121214) else Color.White
    val surfaceVariant = lerp(surface, text, 0.07f)
    val subtleText = lerp(text, bg, 0.35f)
    val outline = lerp(surface, text, 0.18f)
    val container = lerp(surface, accent, 0.22f)

    val base = if (isDark()) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = container,
        onPrimaryContainer = text,
        inversePrimary = container,
        secondary = lerp(accent, text, 0.35f),
        onSecondary = onAccent,
        secondaryContainer = container,
        onSecondaryContainer = text,
        tertiary = lerp(accent, subtleText, 0.5f),
        onTertiary = onAccent,
        tertiaryContainer = container,
        onTertiaryContainer = text,
        background = bg,
        onBackground = text,
        surface = surface,
        onSurface = text,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = subtleText,
        // Pin every container tone to our surfaces so Material's default (purple) tints never show up
        surfaceTint = surface,
        surfaceDim = bg,
        surfaceBright = surfaceVariant,
        surfaceContainerLowest = bg,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceVariant,
        surfaceContainerHighest = surfaceVariant,
        outline = outline,
        outlineVariant = surfaceVariant,
        inverseSurface = text,
        inverseOnSurface = bg,
    )
}
