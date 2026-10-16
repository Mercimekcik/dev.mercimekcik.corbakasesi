package net.perfectdreams.butterscotch.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import net.perfectdreams.butterscotch.android.layouts.Gamepad
import net.perfectdreams.butterscotch.android.layouts.GmlKey
import net.perfectdreams.butterscotch.android.layouts.InputBinding

/**
 * Universal remap rules: a physical keyboard key or controller button becomes another keyboard key
 * or gamepad button for the game. Applied by [PhysicalInputRemapper]; the on-screen virtual gamepad
 * has its own bindings in the layout editor and isn't affected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputRemapScreen(capture: InputCaptureState, onBack: () -> Unit) {
    val settingsStore = Libraries.loadSettingsStore(LocalContext.current)
    val rules = settingsStore.settings.remaps
    // The "new rule" form is a page inside this screen rather than a dialog: a dialog is its own window,
    // so physical key presses would go to it and never reach the capture in MainActivity
    var adding by remember { mutableStateOf(false) }
    BackHandler { if (adding) adding = false else onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (adding) "Yeni Eşleme" else "Tuş Eşleme") },
                navigationIcon = {
                    IconButton(onClick = { if (adding) adding = false else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (adding) {
                AddRemapForm(
                    capture = capture,
                    onCancel = { adding = false },
                    onAdd = { rule ->
                        adding = false
                        // One rule per source: adding a new target for the same input replaces the old one
                        settingsStore.update { copy(remaps = remaps.filter { it.from != rule.from } + rule) }
                    },
                )
                return@Column
            }

            Text(
                "Fiziksel klavyeden ya da kontrolcüden gelen bir tuşu, oyunda başka bir klavye ya da gamepad tuşuna çevirir. " +
                    "Eşlenen tuş kendi işlevini kaybeder. Ekrandaki sanal gamepad etkilenmez.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (rules.isEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Text("Henüz eşleme yok", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
                }
            }

            for (rule in rules) {
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(rule.from.displayName(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp).size(18.dp),
                        )
                        Text(rule.to.displayName(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { settingsStore.update { copy(remaps = remaps - rule) } }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Eşlemeyi sil")
                        }
                    }
                }
            }

            Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Yeni Eşleme")
            }
        }
    }
}

@Composable
private fun AddRemapForm(capture: InputCaptureState, onCancel: () -> Unit, onAdd: (InputRemap) -> Unit) {
    var from by remember { mutableStateOf<InputBinding>(InputBinding.Keyboard(GmlKey.Z.code)) }
    var to by remember { mutableStateOf<InputBinding>(InputBinding.GamepadButton(0, Gamepad.Button.FACE1.index)) }
    // Which side is waiting for a physical key press, if any
    var capturing by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) { onDispose { capture.cancel() } }

    fun startCapture(side: String) {
        capturing = side
        capture.start { binding ->
            if (side == "from") from = binding else to = binding
            capturing = null
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            BindingPicker("Basılan tuş", from, capturing == "from", onCapture = { startCapture("from") }) { from = it }
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            BindingPicker("Oyunun göreceği tuş", to, capturing == "to", onCapture = { startCapture("to") }) { to = it }
        }
    }
    if (from == to) {
        Text("Basılan tuş ile hedef aynı.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("İptal") }
        Button(onClick = { onAdd(InputRemap(from, to)) }, enabled = from != to, modifier = Modifier.weight(1f)) { Text("Ekle") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BindingPicker(title: String, value: InputBinding, capturing: Boolean, onCapture: () -> Unit, onChange: (InputBinding) -> Unit) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(8.dp))

    val isKeyboard = value is InputBinding.Keyboard
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = isKeyboard,
            onClick = { if (!isKeyboard) onChange(InputBinding.Keyboard(GmlKey.Z.code)) },
            shape = SegmentedButtonDefaults.itemShape(0, 2),
        ) { Text("Klavye") }
        SegmentedButton(
            selected = !isKeyboard,
            onClick = { if (isKeyboard) onChange(InputBinding.GamepadButton(0, Gamepad.Button.FACE1.index)) },
            shape = SegmentedButtonDefaults.itemShape(1, 2),
        ) { Text("Gamepad") }
    }
    Spacer(Modifier.height(8.dp))

    val options: List<Pair<InputBinding, String>> = if (isKeyboard) {
        GmlKey.entries.map { InputBinding.Keyboard(it.code) to keyboardKeyName(it.code) }
    } else {
        Gamepad.Button.entries.map { InputBinding.GamepadButton(0, it.index) to gamepadButtonName(it.index) }
    }
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = if (capturing) "Bir tuşa bas…" else value.displayName().substringAfter(": "),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for ((binding, label) in options) {
                DropdownMenuItem(text = { Text(label) }, onClick = {
                    onChange(binding)
                    expanded = false
                })
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = onCapture, enabled = !capturing, modifier = Modifier.fillMaxWidth()) {
        Text(if (capturing) "Klavyede ya da kontrolcüde bir tuşa bas…" else "Tuşa Basarak Seç")
    }
}
