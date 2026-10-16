package net.perfectdreams.butterscotch.android

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import net.perfectdreams.butterscotch.android.layouts.Gamepad
import net.perfectdreams.butterscotch.android.layouts.GmlKey
import net.perfectdreams.butterscotch.android.layouts.InputBinding

/**
 * One universal remap rule for physical input: whenever [from] is pressed on a real keyboard or
 * controller, the game sees [to] instead. Both sides are game-level inputs (a GameMaker vk_ key or a
 * canonical gamepad button), so keyboard -> keyboard, keyboard -> gamepad, gamepad -> keyboard and
 * gamepad -> gamepad all work. For a gamepad [from], the device field is ignored (any controller).
 */
@Serializable
data class InputRemap(val from: InputBinding, val to: InputBinding)

/** Human readable name of a game-level input, e.g. "Klavye: Z" or "Gamepad: A". */
fun InputBinding.displayName(): String = when (this) {
    is InputBinding.Keyboard -> "Klavye: ${keyboardKeyName(vk)}"
    is InputBinding.GamepadButton -> "Gamepad: ${gamepadButtonName(button)}"
}

/** Turkish name for a GameMaker vk_ key, letters/digits/F keys stay as they are. */
fun keyboardKeyName(vk: Int): String {
    val key = GmlKey.fromCode(vk) ?: return "vk $vk"
    return when (key) {
        GmlKey.LEFT -> "Sol Ok"
        GmlKey.UP -> "Yukarı Ok"
        GmlKey.RIGHT -> "Sağ Ok"
        GmlKey.DOWN -> "Aşağı Ok"
        GmlKey.SPACE -> "Boşluk"
        GmlKey.ESCAPE -> "Esc"
        GmlKey.CONTROL -> "Ctrl"
        GmlKey.DELETE -> "Delete"
        else -> key.label
    }
}

/** Xbox style name for a canonical gamepad button, matching the gamepad tester drawing. */
fun gamepadButtonName(index: Int): String = when (Gamepad.Button.fromIndex(index)) {
    Gamepad.Button.FACE1 -> "A"
    Gamepad.Button.FACE2 -> "B"
    Gamepad.Button.FACE3 -> "X"
    Gamepad.Button.FACE4 -> "Y"
    Gamepad.Button.SHOULDER_L -> "LB"
    Gamepad.Button.SHOULDER_R -> "RB"
    Gamepad.Button.TRIGGER_L -> "LT"
    Gamepad.Button.TRIGGER_R -> "RT"
    Gamepad.Button.SELECT -> "View (Select)"
    Gamepad.Button.START -> "Menu (Start)"
    Gamepad.Button.STICK_L -> "Sol Çubuk Tık (LS)"
    Gamepad.Button.STICK_R -> "Sağ Çubuk Tık (RS)"
    Gamepad.Button.DPAD_UP -> "D-Pad Yukarı"
    Gamepad.Button.DPAD_DOWN -> "D-Pad Aşağı"
    Gamepad.Button.DPAD_LEFT -> "D-Pad Sol"
    Gamepad.Button.DPAD_RIGHT -> "D-Pad Sağ"
    Gamepad.Button.HOME -> "Xbox (Home)"
    null -> "Tuş $index"
}

/** Matching key used for rule lookup: gamepad sources match on the button alone, whatever the slot. */
private fun InputBinding.sourceKey(): InputBinding = when (this) {
    is InputBinding.Keyboard -> this
    is InputBinding.GamepadButton -> copy(device = 0)
}

/**
 * Sits between the physical input routers and the runner and applies the user's [InputRemap] rules.
 *
 * Targets are refcounted, so two physical inputs that end up on the same game input (e.g. keyboard Z
 * remapped to gamepad A while gamepad A is also held) only release it once both are released.
 * Unmapped inputs pass straight through with the same bookkeeping.
 */
class PhysicalInputRemapper(private val runner: ButterscotchDroidRunner, private val rules: () -> List<InputRemap>) {
    // What each currently held physical input was sent as, so release goes to the same target even if the rules change mid-press
    private val heldTargets = HashMap<InputBinding, InputBinding>()
    private val targetRefs = HashMap<InputBinding, Int>()

    private var cachedRules: List<InputRemap>? = null
    private var ruleMap: Map<InputBinding, InputBinding> = emptyMap()

    private fun targetFor(source: InputBinding): InputBinding {
        val current = rules()
        if (current !== cachedRules) {
            cachedRules = current
            ruleMap = current.associate { it.from.sourceKey() to it.to }
        }
        val mapped = ruleMap[source.sourceKey()] ?: return source
        // A gamepad target keeps the slot of the controller that pressed it; keyboard sources drive player 1
        return if (mapped is InputBinding.GamepadButton) mapped.copy(device = (source as? InputBinding.GamepadButton)?.device ?: 0) else mapped
    }

    fun onKey(vk: Int, isDown: Boolean) = onInput(InputBinding.Keyboard(vk), isDown)

    fun onGamepadButton(slot: Int, button: Int, isDown: Boolean) = onInput(InputBinding.GamepadButton(slot, button), isDown)

    private fun onInput(source: InputBinding, isDown: Boolean) {
        if (isDown) {
            if (source in heldTargets) return
            val target = targetFor(source)
            heldTargets[source] = target
            val refs = (targetRefs[target] ?: 0) + 1
            targetRefs[target] = refs
            if (refs == 1) send(target, true)
        } else {
            val target = heldTargets.remove(source) ?: return
            val refs = (targetRefs[target] ?: 1) - 1
            if (refs <= 0) {
                targetRefs.remove(target)
                send(target, false)
            } else {
                targetRefs[target] = refs
            }
        }
    }

    /** Releases everything this remapper is holding, so backgrounding never leaves a remapped key stuck. */
    fun releaseAll() {
        for (target in targetRefs.keys) send(target, false)
        targetRefs.clear()
        heldTargets.clear()
    }

    private fun send(target: InputBinding, isDown: Boolean) = when (target) {
        is InputBinding.Keyboard -> runner.onKey(target.vk, isDown)
        is InputBinding.GamepadButton -> runner.onGamepadButton(target.device, target.button, isDown)
    }
}

/**
 * "Press the key you want" picker for the remap screen. While [listening], the next physical
 * keyboard key or controller button (including the hat d-pad and analog triggers) is turned into the
 * game-level [InputBinding] the routers would produce for it, and handed to [captured].
 */
class InputCaptureState {
    var listening by mutableStateOf(false)
        private set
    private var onCaptured: ((InputBinding) -> Unit)? = null

    fun start(onCaptured: (InputBinding) -> Unit) {
        this.onCaptured = onCaptured
        listening = true
    }

    fun cancel() {
        listening = false
        onCaptured = null
    }

    private fun finish(binding: InputBinding) {
        val callback = onCaptured
        cancel()
        callback?.invoke(binding)
    }

    fun onKeyEvent(event: KeyEvent): Boolean {
        if (!listening) return false
        // Let back through so the picker can still be dismissed with the system back gesture
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return false
        if (event.action != KeyEvent.ACTION_DOWN) return true
        val device = event.device
        val fromController = device != null && GamepadRouter.isGameController(device)
        if (fromController) {
            val button = GamepadRouter.keyCodeToButton(event.keyCode)
            if (button >= 0) finish(InputBinding.GamepadButton(0, button))
        } else {
            val vk = KeyboardRouter.keyCodeToVk(event.keyCode)
            if (vk != -1) finish(InputBinding.Keyboard(vk))
        }
        return true
    }

    fun onMotionEvent(event: MotionEvent): Boolean {
        if (!listening || event.source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK) return false
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        val left = maxOf(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE))
        val right = maxOf(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS))
        val button = when {
            hatY < -0.5f -> Gamepad.Button.DPAD_UP
            hatY > 0.5f -> Gamepad.Button.DPAD_DOWN
            hatX < -0.5f -> Gamepad.Button.DPAD_LEFT
            hatX > 0.5f -> Gamepad.Button.DPAD_RIGHT
            left > 0.5f -> Gamepad.Button.TRIGGER_L
            right > 0.5f -> Gamepad.Button.TRIGGER_R
            else -> null
        }
        if (button != null) finish(InputBinding.GamepadButton(0, button.index))
        return true
    }
}
