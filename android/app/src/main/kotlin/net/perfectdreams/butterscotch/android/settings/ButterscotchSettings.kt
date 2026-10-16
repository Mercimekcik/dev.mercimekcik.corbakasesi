package net.perfectdreams.butterscotch.android.settings

import kotlinx.serialization.Serializable
import net.perfectdreams.butterscotch.android.InputRemap
import net.perfectdreams.butterscotch.android.theme.AppThemes
import net.perfectdreams.butterscotch.android.theme.ThemeColors

@Serializable
data class ButterscotchSettings(
    // Bump this when we need to migrate an old settings.json to a newer shape
    val version: Int = 1,
    val enableHapticFeedback: Boolean = true,
    // Vibration strength as a 10..100 percentage (the UI clamps the minimum to 10)
    val hapticStrength: Int = 100,
    val hideVirtualGamepadWhenUsingPhysicalController: Boolean = true,
    val hideVirtualGamepadWhenUsingPhysicalKeyboard: Boolean = true,
    // Flipped by the DirectionalSwap gamepad button: joysticks render as d-pads and d-pads as joysticks
    val swapDirectionalControls: Boolean = false,
    // Shows the in-game cheats, logs and GML profiler entries. Off for players, toggled from the start screen settings
    val developerMode: Boolean = false,
    // How the on-screen gamepad is drawn, picked in the start screen settings
    val gamepadStyle: GamepadStyle = GamepadStyle.NORMAL,
    // Global opacity of the on-screen gamepad (0.1..1), multiplied with each element's own opacity from the layout
    val gamepadOpacity: Float = 1f,
    // Universal remap rules for physical keyboards and controllers
    val remaps: List<InputRemap> = emptyList(),
    // App theme: one of AppThemes.presets ids, or AppThemes.CUSTOM_ID to use customTheme
    val themeId: String = AppThemes.DEFAULT_ID,
    val customTheme: ThemeColors = AppThemes.presets.first().colors,
)

@Serializable
enum class GamepadStyle {
    NORMAL,
    PIXEL_ART,
}
