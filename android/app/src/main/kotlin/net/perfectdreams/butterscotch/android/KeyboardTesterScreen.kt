package net.perfectdreams.butterscotch.android

import android.view.InputDevice
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.perfectdreams.butterscotch.android.layouts.GmlKey

/**
 * Live state for the keyboard tester. [MainActivity] feeds it every key event while the screen is
 * open ([listening]); keyboard keys are consumed so Esc, Tab, arrows etc. don't move focus around.
 */
class KeyboardTesterState {
    var listening = false

    val pressedKeys = mutableStateMapOf<Int, Boolean>()
    /** Every key pressed at least once since the screen opened, so untested keys stand out. */
    val testedKeys = mutableStateMapOf<Int, Boolean>()
    var lastEvent by mutableStateOf<KeyEvent?>(null)
        private set

    fun onKeyEvent(event: KeyEvent): Boolean {
        if (!listening) return false
        // Back from the navigation bar / gesture must still leave the screen
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return false
        if (event.source and InputDevice.SOURCE_KEYBOARD != InputDevice.SOURCE_KEYBOARD) return false
        // Controllers also report SOURCE_KEYBOARD for their buttons, the gamepad tester covers those
        val device = event.device
        if (device != null && GamepadRouter.isGameController(device) && KeyEvent.isGamepadButton(event.keyCode)) return false

        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                pressedKeys[event.keyCode] = true
                testedKeys[event.keyCode] = true
                if (event.repeatCount == 0) lastEvent = event
            }
            KeyEvent.ACTION_UP -> pressedKeys.remove(event.keyCode)
        }
        return true
    }

    fun reset() {
        pressedKeys.clear()
        testedKeys.clear()
        lastEvent = null
    }
}

// ===[ Layout ]===

// One key on the drawn keyboard: its Android keycode, label and width in key units
private class KeyCap(val keyCode: Int, val label: String, val width: Float = 1f)

// A gap of [width] units between keys
private fun gap(width: Float) = KeyCap(KeyEvent.KEYCODE_UNKNOWN, "", width)

private fun letters(chars: String) = chars.map { KeyCap(KeyEvent.KEYCODE_A + (it - 'A'), it.toString()) }

// Standard ANSI tenkeyless layout: main block (15 units) + navigation block (3 units) after a half unit gap
private val KEYBOARD_ROWS: List<List<KeyCap>> = listOf(
    listOf(KeyCap(KeyEvent.KEYCODE_ESCAPE, "Esc"), gap(1f)) +
        (0 until 12).map { KeyCap(KeyEvent.KEYCODE_F1 + it, "F${it + 1}") }.chunked(4).flatMapIndexed { i, chunk -> if (i < 2) chunk + gap(0.5f) else chunk } +
        listOf(gap(0.5f), KeyCap(KeyEvent.KEYCODE_SYSRQ, "PrtSc"), KeyCap(KeyEvent.KEYCODE_SCROLL_LOCK, "ScrLk"), KeyCap(KeyEvent.KEYCODE_BREAK, "Pause")),
    listOf(KeyCap(KeyEvent.KEYCODE_GRAVE, "`")) +
        (1..9).map { KeyCap(KeyEvent.KEYCODE_0 + it, it.toString()) } +
        listOf(
            KeyCap(KeyEvent.KEYCODE_0, "0"), KeyCap(KeyEvent.KEYCODE_MINUS, "-"), KeyCap(KeyEvent.KEYCODE_EQUALS, "="), KeyCap(KeyEvent.KEYCODE_DEL, "⌫", 2f),
            gap(0.5f), KeyCap(KeyEvent.KEYCODE_INSERT, "Ins"), KeyCap(KeyEvent.KEYCODE_MOVE_HOME, "Home"), KeyCap(KeyEvent.KEYCODE_PAGE_UP, "PgUp"),
        ),
    listOf(KeyCap(KeyEvent.KEYCODE_TAB, "Tab", 1.5f)) + letters("QWERTYUIOP") +
        listOf(
            KeyCap(KeyEvent.KEYCODE_LEFT_BRACKET, "["), KeyCap(KeyEvent.KEYCODE_RIGHT_BRACKET, "]"), KeyCap(KeyEvent.KEYCODE_BACKSLASH, "\\", 1.5f),
            gap(0.5f), KeyCap(KeyEvent.KEYCODE_FORWARD_DEL, "Del"), KeyCap(KeyEvent.KEYCODE_MOVE_END, "End"), KeyCap(KeyEvent.KEYCODE_PAGE_DOWN, "PgDn"),
        ),
    listOf(KeyCap(KeyEvent.KEYCODE_CAPS_LOCK, "Caps", 1.75f)) + letters("ASDFGHJKL") +
        listOf(KeyCap(KeyEvent.KEYCODE_SEMICOLON, ";"), KeyCap(KeyEvent.KEYCODE_APOSTROPHE, "'"), KeyCap(KeyEvent.KEYCODE_ENTER, "Enter", 2.25f)),
    listOf(KeyCap(KeyEvent.KEYCODE_SHIFT_LEFT, "Shift", 2.25f)) + letters("ZXCVBNM") +
        listOf(
            KeyCap(KeyEvent.KEYCODE_COMMA, ","), KeyCap(KeyEvent.KEYCODE_PERIOD, "."), KeyCap(KeyEvent.KEYCODE_SLASH, "/"), KeyCap(KeyEvent.KEYCODE_SHIFT_RIGHT, "Shift", 2.75f),
            gap(1.5f), KeyCap(KeyEvent.KEYCODE_DPAD_UP, "↑"),
        ),
    listOf(
        KeyCap(KeyEvent.KEYCODE_CTRL_LEFT, "Ctrl", 1.25f), KeyCap(KeyEvent.KEYCODE_META_LEFT, "Win", 1.25f), KeyCap(KeyEvent.KEYCODE_ALT_LEFT, "Alt", 1.25f),
        KeyCap(KeyEvent.KEYCODE_SPACE, "Space", 6.25f),
        KeyCap(KeyEvent.KEYCODE_ALT_RIGHT, "AltGr", 1.25f), KeyCap(KeyEvent.KEYCODE_META_RIGHT, "Win", 1.25f), KeyCap(KeyEvent.KEYCODE_MENU, "Menu", 1.25f), KeyCap(KeyEvent.KEYCODE_CTRL_RIGHT, "Ctrl", 1.25f),
        gap(0.5f), KeyCap(KeyEvent.KEYCODE_DPAD_LEFT, "←"), KeyCap(KeyEvent.KEYCODE_DPAD_DOWN, "↓"), KeyCap(KeyEvent.KEYCODE_DPAD_RIGHT, "→"),
    ),
)

// Width of the whole layout in key units: 15 for the main block, a half unit gap, 3 for the navigation block
private const val KEYBOARD_UNITS = 18.5f
// Gap between keys as a fraction of one key unit
private const val GAP_FRACTION = 0.1f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardTesterScreen(state: KeyboardTesterState, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    DisposableEffect(state) {
        state.reset()
        state.listening = true
        onDispose { state.listening = false }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Klavye Test") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Bağlı klavyedeki tuşlara bas. Basılı tuşlar beyaz yanar, denenmiş tuşlar yeşil kenarlıkla işaretlenir.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(16.dp))

            // The keys are sized from the available width so the whole keyboard always fits on screen
            BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                val step = minOf(maxWidth / KEYBOARD_UNITS, 44.dp)
                val gap = step * GAP_FRACTION
                val unit = step - gap
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    for ((rowIndex, row) in KEYBOARD_ROWS.withIndex()) {
                        // A little extra space under the function key row, like real keyboards
                        if (rowIndex == 1) Spacer(Modifier.height(gap * 2))
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            for (key in row) KeyCapView(key, state, unit, gap)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Card(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                Column(Modifier.padding(16.dp)) {
                    val event = state.lastEvent
                    if (event == null) {
                        Text("Henüz tuşa basılmadı", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        InfoRow("Son tuş", KeyEvent.keyCodeToString(event.keyCode).removePrefix("KEYCODE_"))
                        InfoRow("Android kodu / tarama kodu", "${event.keyCode} / ${event.scanCode}")
                        val vk = KeyboardRouter.keyCodeToVk(event.keyCode)
                        InfoRow("Oyunda", if (vk == -1) "Kullanılmıyor" else (GmlKey.fromCode(vk)?.label ?: "vk $vk"))
                        val char = event.getUnicodeChar(event.metaState)
                        if (char != 0) InfoRow("Karakter", String(Character.toChars(char)))
                        InfoRow("Cihaz", event.device?.name ?: "Bilinmiyor")
                    }
                    Spacer(Modifier.height(4.dp))
                    InfoRow("Denenen tuş sayısı", state.testedKeys.size.toString())
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { state.reset() }) { Text("Sıfırla") }
        }
    }
}

@Composable
private fun KeyCapView(key: KeyCap, state: KeyboardTesterState, unit: Dp, gap: Dp) {
    // Every row is a sequence of w-unit slots each followed by a gap, so a w-unit key (or spacer) is w steps minus
    // the trailing gap. That lines wide keys up with the rows above and makes every row exactly KEYBOARD_UNITS wide
    val width = (unit + gap) * key.width - gap
    if (key.keyCode == KeyEvent.KEYCODE_UNKNOWN) {
        Spacer(Modifier.size(width, unit))
        return
    }
    val pressed = state.pressedKeys[key.keyCode] == true
    val tested = state.testedKeys[key.keyCode] == true
    val shape = RoundedCornerShape(unit * 0.18f)
    // Labels scale with the keys; long ones like "PrtSc" get a smaller size so they stay inside the cap
    val fontSize = with(LocalDensity.current) { (unit * if (key.label.length > 3) 0.27f else 0.4f).toSp() }
    Box(
        Modifier
            .size(width, unit)
            .background(if (pressed) Color(0xFFF2F2F4) else Color(0xFF26262A), shape)
            .border(if (unit < 24.dp) 1.dp else 1.5.dp, if (tested && !pressed) Color(0xFF3FB34F) else Color(0xFF3A3A40), shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            key.label,
            color = if (pressed) Color(0xFF121214) else Color(0xFFE6E6E9),
            fontSize = fontSize,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
