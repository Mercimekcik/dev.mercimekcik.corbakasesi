package net.perfectdreams.butterscotch.android

import android.content.Context
import android.hardware.input.InputManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.abs

/**
 * Live state of the physical controller being tested. [MainActivity] feeds it every key and motion
 * event while the tester screen is open ([listening]), and the screen draws from it.
 *
 * Inputs are read the same way the game's GamepadRouter reads them (left stick X/Y, right stick
 * Z/RZ, triggers LTRIGGER/BRAKE and RTRIGGER/GAS, d-pad from keys or the hat axis), so whatever
 * works here also works in game.
 */
class GamepadTesterState {
    var listening = false

    /** The device that sent the latest input; the screen follows it when several are connected. */
    var activeDeviceId by mutableStateOf<Int?>(null)
        private set
    val pressedKeys = mutableStateMapOf<Int, Boolean>()
    val axes = mutableStateMapOf<Int, Float>()

    private fun isFromController(event: android.view.InputEvent): Boolean {
        val source = event.source
        if (source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD) return true
        if (source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK) return true
        // D-pad keys also come from TV remotes; only count them when the device itself is a controller
        val device = InputDevice.getDevice(event.deviceId) ?: return false
        return source and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD && device.isGameController()
    }

    private fun switchTo(deviceId: Int) {
        if (activeDeviceId == deviceId) return
        activeDeviceId = deviceId
        pressedKeys.clear()
        axes.clear()
    }

    /** Returns true when the event was a controller event and got consumed by the tester. */
    fun onKeyEvent(event: KeyEvent): Boolean {
        if (!listening || !isFromController(event)) return false
        switchTo(event.deviceId)
        when (event.action) {
            KeyEvent.ACTION_DOWN -> pressedKeys[event.keyCode] = true
            KeyEvent.ACTION_UP -> pressedKeys.remove(event.keyCode)
        }
        // Consumed so B doesn't fall back to "back" and leave the screen mid-test
        return true
    }

    fun onMotionEvent(event: MotionEvent): Boolean {
        if (!listening || !isFromController(event) || event.action != MotionEvent.ACTION_MOVE) return false
        switchTo(event.deviceId)
        for (axis in TRACKED_AXES) axes[axis] = event.getAxisValue(axis)
        return true
    }

    fun isPressed(keyCode: Int) = pressedKeys[keyCode] == true
    fun axis(axis: Int) = axes[axis] ?: 0f

    // Derived values, matching GamepadRouter
    val leftTrigger get() = maxOf(axis(MotionEvent.AXIS_LTRIGGER), axis(MotionEvent.AXIS_BRAKE), if (isPressed(KeyEvent.KEYCODE_BUTTON_L2)) 1f else 0f)
    val rightTrigger get() = maxOf(axis(MotionEvent.AXIS_RTRIGGER), axis(MotionEvent.AXIS_GAS), if (isPressed(KeyEvent.KEYCODE_BUTTON_R2)) 1f else 0f)
    val dpadUp get() = isPressed(KeyEvent.KEYCODE_DPAD_UP) || axis(MotionEvent.AXIS_HAT_Y) < -0.5f
    val dpadDown get() = isPressed(KeyEvent.KEYCODE_DPAD_DOWN) || axis(MotionEvent.AXIS_HAT_Y) > 0.5f
    val dpadLeft get() = isPressed(KeyEvent.KEYCODE_DPAD_LEFT) || axis(MotionEvent.AXIS_HAT_X) < -0.5f
    val dpadRight get() = isPressed(KeyEvent.KEYCODE_DPAD_RIGHT) || axis(MotionEvent.AXIS_HAT_X) > 0.5f

    companion object {
        private val TRACKED_AXES = intArrayOf(
            MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ,
            MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_RTRIGGER, MotionEvent.AXIS_BRAKE, MotionEvent.AXIS_GAS,
            MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y,
        )
    }
}

private fun InputDevice.isGameController(): Boolean =
    sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
        sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK

/** Currently connected game controllers, kept up to date as they connect and disconnect. */
@Composable
private fun rememberConnectedControllers(context: Context): List<InputDevice> {
    var devices by remember { mutableStateOf(emptyList<InputDevice>()) }
    DisposableEffect(context) {
        val inputManager = context.getSystemService(InputManager::class.java)
        fun refresh() {
            devices = InputDevice.getDeviceIds().toList()
                .mapNotNull { InputDevice.getDevice(it) }
                .filter { !it.isVirtual && it.isGameController() }
        }
        val listener = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) = refresh()
            override fun onInputDeviceRemoved(deviceId: Int) = refresh()
            override fun onInputDeviceChanged(deviceId: Int) = refresh()
        }
        refresh()
        inputManager.registerInputDeviceListener(listener, Handler(Looper.getMainLooper()))
        onDispose { inputManager.unregisterInputDeviceListener(listener) }
    }
    return devices
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamepadTesterScreen(state: GamepadTesterState, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    DisposableEffect(state) {
        state.listening = true
        onDispose { state.listening = false }
    }

    val context = LocalContext.current
    val controllers = rememberConnectedControllers(context)
    val active = state.activeDeviceId?.let { id -> controllers.firstOrNull { it.id == id } } ?: controllers.firstOrNull()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gamepad Test") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                when {
                    controllers.isEmpty() -> "Bağlı kontrolcü yok. Bluetooth ya da USB ile bir kontrolcü bağla."
                    controllers.size == 1 -> active?.name ?: ""
                    else -> "${active?.name} (${controllers.size} kontrolcü bağlı, en son kullanılan gösteriliyor)"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            XboxControllerDrawing(state, Modifier.fillMaxWidth().widthIn(max = 560.dp).aspectRatio(CONTROLLER_W / CONTROLLER_H))

            Spacer(Modifier.height(16.dp))
            Card(Modifier.fillMaxWidth().widthIn(max = 560.dp)) {
                Column(Modifier.padding(16.dp)) {
                    ValueRow("Sol çubuk", "X ${fmt(state.axis(MotionEvent.AXIS_X))}   Y ${fmt(state.axis(MotionEvent.AXIS_Y))}")
                    ValueRow("Sağ çubuk", "X ${fmt(state.axis(MotionEvent.AXIS_Z))}   Y ${fmt(state.axis(MotionEvent.AXIS_RZ))}")
                    ValueRow("Tetikler", "LT ${fmt(state.leftTrigger)}   RT ${fmt(state.rightTrigger)}")
                    val pressed = state.pressedKeys.keys.sorted().joinToString(", ") { buttonName(it) }
                    ValueRow("Basılı tuşlar", pressed.ifEmpty { "-" })
                }
            }

            val vibrateTarget = active
            if (vibrateTarget != null && vibrateTarget.canVibrate()) {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = { vibrateTarget.vibrateOnce() }) { Text("Titreşimi Test Et") }
            }
        }
    }
}

@Composable
private fun ValueRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// Values that round to zero print as "+0.00" (a resting stick often reports -0.0 or tiny negative noise)
private fun fmt(value: Float) = String.format(Locale.ROOT, "%+.2f", if (abs(value) < 0.005f) 0f else value)

private fun buttonName(keyCode: Int): String = when (keyCode) {
    KeyEvent.KEYCODE_BUTTON_A -> "A"
    KeyEvent.KEYCODE_BUTTON_B -> "B"
    KeyEvent.KEYCODE_BUTTON_X -> "X"
    KeyEvent.KEYCODE_BUTTON_Y -> "Y"
    KeyEvent.KEYCODE_BUTTON_L1 -> "LB"
    KeyEvent.KEYCODE_BUTTON_R1 -> "RB"
    KeyEvent.KEYCODE_BUTTON_L2 -> "LT"
    KeyEvent.KEYCODE_BUTTON_R2 -> "RT"
    KeyEvent.KEYCODE_BUTTON_THUMBL -> "LS"
    KeyEvent.KEYCODE_BUTTON_THUMBR -> "RS"
    KeyEvent.KEYCODE_BUTTON_SELECT -> "View"
    KeyEvent.KEYCODE_BUTTON_START -> "Menu"
    KeyEvent.KEYCODE_BUTTON_MODE -> "Xbox"
    KeyEvent.KEYCODE_DPAD_UP -> "↑"
    KeyEvent.KEYCODE_DPAD_DOWN -> "↓"
    KeyEvent.KEYCODE_DPAD_LEFT -> "←"
    KeyEvent.KEYCODE_DPAD_RIGHT -> "→"
    else -> KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
}

private fun InputDevice.canVibrate(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) vibratorManager.vibratorIds.isNotEmpty()
    else @Suppress("DEPRECATION") vibrator.hasVibrator()

private fun InputDevice.vibrateOnce() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        vibratorManager.vibrate(android.os.CombinedVibration.createParallel(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE)))
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        @Suppress("DEPRECATION") vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION") vibrator.vibrate(300)
    }
}

// ===[ Controller drawing ]===

// The controller is laid out in this virtual coordinate space and scaled to fit the canvas
private const val CONTROLLER_W = 500f
private const val CONTROLLER_H = 330f

private object Pad {
    val body = Color(0xFF26262A)
    val bodyEdge = Color(0xFF3A3A40)
    val idle = Color(0xFF45454C)
    val pressed = Color(0xFFF2F2F4)
    val label = Color(0xFFE6E6E9)
    val labelOnPressed = Color(0xFF121214)
    val a = Color(0xFF3FB34F)
    val b = Color(0xFFE5383B)
    val x = Color(0xFF2F7DE1)
    val y = Color(0xFFF2B705)
    val guide = Color(0xFF8BD17C)
}

// Left half of the body outline from the top centre to the bottom centre, as cubic segments
// (c1x, c1y, c2x, c2y, x, y). The right half is the mirror image.
private val BODY_START = Offset(250f, 64f)
private val BODY_LEFT = listOf(
    floatArrayOf(205f, 64f, 160f, 50f, 122f, 54f),
    floatArrayOf(96f, 57f, 76f, 70f, 68f, 96f),
    floatArrayOf(56f, 140f, 26f, 236f, 48f, 286f),
    floatArrayOf(62f, 316f, 104f, 318f, 120f, 298f),
    floatArrayOf(138f, 276f, 150f, 242f, 172f, 232f),
    floatArrayOf(200f, 222f, 228f, 224f, 250f, 224f),
)

private fun bodyPath(): Path = Path().apply {
    moveTo(BODY_START.x, BODY_START.y)
    for (s in BODY_LEFT) cubicTo(s[0], s[1], s[2], s[3], s[4], s[5])
    // Walk the mirrored segments backwards: each one ends where the previous left segment started
    for (i in BODY_LEFT.indices.reversed()) {
        val s = BODY_LEFT[i]
        val end = if (i == 0) BODY_START else Offset(BODY_LEFT[i - 1][4], BODY_LEFT[i - 1][5])
        cubicTo(CONTROLLER_W - s[2], s[3], CONTROLLER_W - s[0], s[1], CONTROLLER_W - end.x, end.y)
    }
    close()
}

@Composable
private fun XboxControllerDrawing(state: GamepadTesterState, modifier: Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val body = remember { bodyPath() }
    Canvas(modifier) {
        val scale = minOf(size.width / CONTROLLER_W, size.height / CONTROLLER_H)
        withTransform({
            translate((size.width - CONTROLLER_W * scale) / 2f, (size.height - CONTROLLER_H * scale) / 2f)
            scale(scale, scale, Offset.Zero)
        }) {
            // Triggers (analog fill) and bumpers sit behind the body's top edge
            drawTrigger(Offset(112f, 2f), state.leftTrigger, "LT", textMeasurer)
            drawTrigger(Offset(338f, 2f), state.rightTrigger, "RT", textMeasurer)
            drawBumper(Offset(96f, 34f), state.isPressed(KeyEvent.KEYCODE_BUTTON_L1), "LB", textMeasurer)
            drawBumper(Offset(324f, 34f), state.isPressed(KeyEvent.KEYCODE_BUTTON_R1), "RB", textMeasurer)

            drawPath(body, Pad.body)
            drawPath(body, Pad.bodyEdge, style = Stroke(width = 3f))

            // Xbox / guide button
            val guidePressed = state.isPressed(KeyEvent.KEYCODE_BUTTON_MODE)
            drawCircle(if (guidePressed) Pad.guide else Pad.idle, radius = 15f, center = Offset(250f, 92f))
            drawCircle(Pad.body, radius = 8f, center = Offset(250f, 92f), style = Stroke(width = 3f))

            // View and Menu
            drawSmallButton(Offset(212f, 128f), state.isPressed(KeyEvent.KEYCODE_BUTTON_SELECT)) { c, color ->
                drawRect(color, c + Offset(-6f, -5f), Size(8f, 7f), style = Stroke(1.6f))
                drawRect(color, c + Offset(-2f, -1f), Size(8f, 7f), style = Stroke(1.6f))
            }
            drawSmallButton(Offset(288f, 128f), state.isPressed(KeyEvent.KEYCODE_BUTTON_START)) { c, color ->
                for (dy in listOf(-4f, 0f, 4f)) drawLine(color, c + Offset(-5f, dy), c + Offset(5f, dy), strokeWidth = 1.8f)
            }

            drawStick(
                Offset(150f, 128f), state.axis(MotionEvent.AXIS_X), state.axis(MotionEvent.AXIS_Y),
                state.isPressed(KeyEvent.KEYCODE_BUTTON_THUMBL),
            )
            drawStick(
                Offset(305f, 198f), state.axis(MotionEvent.AXIS_Z), state.axis(MotionEvent.AXIS_RZ),
                state.isPressed(KeyEvent.KEYCODE_BUTTON_THUMBR),
            )
            drawDPad(Offset(195f, 198f), state.dpadUp, state.dpadDown, state.dpadLeft, state.dpadRight)

            // Face buttons in the Xbox diamond: Y top, A bottom, X left, B right
            val face = Offset(368f, 128f)
            drawFaceButton(face + Offset(0f, -30f), "Y", Pad.y, state.isPressed(KeyEvent.KEYCODE_BUTTON_Y), textMeasurer)
            drawFaceButton(face + Offset(0f, 30f), "A", Pad.a, state.isPressed(KeyEvent.KEYCODE_BUTTON_A), textMeasurer)
            drawFaceButton(face + Offset(-30f, 0f), "X", Pad.x, state.isPressed(KeyEvent.KEYCODE_BUTTON_X), textMeasurer)
            drawFaceButton(face + Offset(30f, 0f), "B", Pad.b, state.isPressed(KeyEvent.KEYCODE_BUTTON_B), textMeasurer)
        }
    }
}

// [sizeUnits] is the font size in controller units: the text is laid out at that many pixels and then
// scaled together with the rest of the drawing, so it keeps its proportions on any screen density
private fun DrawScope.drawCenteredText(text: String, center: Offset, color: Color, sizeUnits: Float, textMeasurer: TextMeasurer) {
    val layout = textMeasurer.measure(text, TextStyle(color = color, fontSize = sizeUnits.toSp(), fontWeight = FontWeight.Bold))
    drawText(layout, topLeft = center - Offset(layout.size.width / 2f, layout.size.height / 2f))
}

private fun DrawScope.drawTrigger(topLeft: Offset, value: Float, label: String, textMeasurer: TextMeasurer) {
    val size = Size(50f, 40f)
    drawRoundRect(Pad.idle, topLeft, size, CornerRadius(10f))
    if (value > 0.01f) {
        val fill = size.height * value.coerceIn(0f, 1f)
        drawRoundRect(Pad.pressed, topLeft + Offset(0f, size.height - fill), Size(size.width, fill), CornerRadius(10f))
    }
    drawCenteredText(label, topLeft + Offset(size.width / 2f, 15f), if (value > 0.5f) Pad.labelOnPressed else Pad.label, 13f, textMeasurer)
}

private fun DrawScope.drawBumper(topLeft: Offset, pressed: Boolean, label: String, textMeasurer: TextMeasurer) {
    val size = Size(80f, 26f)
    drawRoundRect(if (pressed) Pad.pressed else Pad.idle, topLeft, size, CornerRadius(12f))
    drawCenteredText(label, topLeft + Offset(size.width / 2f, 11f), if (pressed) Pad.labelOnPressed else Pad.label, 13f, textMeasurer)
}

private fun DrawScope.drawSmallButton(center: Offset, pressed: Boolean, glyph: DrawScope.(Offset, Color) -> Unit) {
    drawCircle(if (pressed) Pad.pressed else Pad.idle, radius = 11f, center = center)
    glyph(center, if (pressed) Pad.labelOnPressed else Pad.label)
}

private fun DrawScope.drawStick(center: Offset, x: Float, y: Float, clicked: Boolean) {
    drawCircle(Color(0xFF1A1A1D), radius = 32f, center = center)
    drawCircle(Pad.bodyEdge, radius = 32f, center = center, style = Stroke(2f))
    // Deadzone-free so even small drift is visible while testing
    val knob = center + Offset(x.coerceIn(-1f, 1f) * 14f, y.coerceIn(-1f, 1f) * 14f)
    val moved = abs(x) > 0.1f || abs(y) > 0.1f
    drawCircle(if (clicked) Pad.pressed else if (moved) Color(0xFF6A6A72) else Pad.idle, radius = 21f, center = knob)
    drawCircle(Color(0xFF2E2E33), radius = 21f, center = knob, style = Stroke(2.5f))
}

private fun DrawScope.drawDPad(center: Offset, up: Boolean, down: Boolean, left: Boolean, right: Boolean) {
    val arm = 21f
    val length = 32f
    drawCircle(Color(0xFF1A1A1D), radius = 37f, center = center)
    fun armRect(dx: Float, dy: Float, pressed: Boolean) {
        val topLeft = if (dx == 0f) Offset(center.x - arm / 2f, if (dy < 0) center.y - length else center.y)
        else Offset(if (dx < 0) center.x - length else center.x, center.y - arm / 2f)
        val size = if (dx == 0f) Size(arm, length) else Size(length, arm)
        drawRoundRect(if (pressed) Pad.pressed else Pad.idle, topLeft, size, CornerRadius(4f))
    }
    armRect(0f, -1f, up)
    armRect(0f, 1f, down)
    armRect(-1f, 0f, left)
    armRect(1f, 0f, right)
    drawRect(Pad.idle, center - Offset(arm / 2f, arm / 2f), Size(arm, arm))
}

private fun DrawScope.drawFaceButton(center: Offset, letter: String, color: Color, pressed: Boolean, textMeasurer: TextMeasurer) {
    drawCircle(if (pressed) color else Color(0xFF1C1C20), radius = 14f, center = center)
    drawCircle(color.copy(alpha = if (pressed) 1f else 0.6f), radius = 14f, center = center, style = Stroke(2f))
    drawCenteredText(letter, center, if (pressed) Color.White else color, 16f, textMeasurer)
}
