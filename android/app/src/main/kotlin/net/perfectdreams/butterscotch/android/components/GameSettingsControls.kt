package net.perfectdreams.butterscotch.android.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import net.perfectdreams.butterscotch.android.layouts.GamepadLayout
import net.perfectdreams.butterscotch.android.layouts.LayoutLibrary
import net.perfectdreams.butterscotch.android.library.GameEntry
import java.util.UUID
import kotlin.math.roundToInt

// Exposed dropdown that assigns one of the LayoutLibrary's layouts to this game for an orientation.
// The selection persists immediately via the caller's onSelect. "(unknown)" shows when the assigned
// id has no matching layout (e.g. a default that is not seeded yet).
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LayoutDropdown(
    label: String,
    selectedId: UUID,
    options: List<GamepadLayout>,
    onSelect: (UUID) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = options.firstOrNull { it.id == selectedId }?.fancyName ?: "(unknown)"
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { layout ->
                DropdownMenuItem(
                    text = { Text(layout.fancyName) },
                    onClick = {
                        onSelect(layout.id)
                        expanded = false
                    },
                )
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostProcessingSection(
    settings: GameEntry.PostProcessingSettings,
    onChange: (GameEntry.PostProcessingSettings) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var crtDialogOpen by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.weight(1f),
        ) {
            OutlinedTextField(
                value = settings.shader.fancyName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Görüntü Efekti") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                GameEntry.PostProcessingShader.entries.forEach { shader ->
                    DropdownMenuItem(
                        text = { Text(shader.fancyName) },
                        onClick = {
                            onChange(settings.copy(shader = shader))
                            expanded = false
                        },
                    )
                }
            }
        }

        // Always shown so the row layout stays stable, but only the CRT shader has anything to tweak
        IconButton(onClick = { crtDialogOpen = true }, enabled = settings.shader == GameEntry.PostProcessingShader.CRT) {
            Icon(Icons.Default.Settings, contentDescription = "Tüplü TV efektini özelleştir")
        }
    }

    if (crtDialogOpen) {
        CrtSettingsDialog(
            settings = settings.crt,
            onChange = { onChange(settings.copy(crt = it)) },
            onDismiss = { crtDialogOpen = false },
        )
    }
}

// Sliders for each CRT effect strength (0..100%). Edits write straight back through onChange so the preview state
// stays live; the values only persist when the host screen commits the form state.
@Composable
private fun CrtSettingsDialog(
    settings: GameEntry.CrtSettings,
    onChange: (GameEntry.CrtSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tamam") } },
        title = { Text("Tüplü TV Efekti") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                CrtSlider("Kavis", settings.curvature) { onChange(settings.copy(curvature = it)) }
                CrtSlider("Renk sapması", settings.aberration) { onChange(settings.copy(aberration = it)) }
                CrtSlider("Işık taşması", settings.halation) { onChange(settings.copy(halation = it)) }
                CrtSlider("Tarama çizgileri", settings.scanlines) { onChange(settings.copy(scanlines = it)) }
                CrtSlider("Fosfor maskesi", settings.mask) { onChange(settings.copy(mask = it)) }
                CrtSlider("Vinyet", settings.vignette) { onChange(settings.copy(vignette = it)) }
            }
        },
    )
}

// The strength is stored as a 0.0..1.0 fraction (matching the shader uniform), but the slider works in whole
// percent for feel, so we scale by 100 at this UI boundary only
@Composable
private fun CrtSlider(label: String, value: Double, onChange: (Double) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text("${(value * 100).roundToInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = (value * 100).toFloat(),
            onValueChange = { onChange(it.roundToInt() / 100.0) },
            valueRange = 0f..100f,
        )
    }
}