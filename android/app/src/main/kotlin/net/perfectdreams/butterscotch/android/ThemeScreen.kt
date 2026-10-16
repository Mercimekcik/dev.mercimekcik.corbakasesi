package net.perfectdreams.butterscotch.android

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.perfectdreams.butterscotch.android.theme.AppThemes
import net.perfectdreams.butterscotch.android.theme.ThemeColors
import net.perfectdreams.butterscotch.android.theme.isValid
import net.perfectdreams.butterscotch.android.theme.parseHexColor
import net.perfectdreams.butterscotch.android.theme.toHex

/** What a theme export file looks like. [format] lets import reject random JSON files. */
@Serializable
private class ThemeFile(
    val format: String = THEME_FILE_FORMAT,
    val version: Int = 1,
    val name: String = "Özel Tema",
    val colors: ThemeColors,
)

private const val THEME_FILE_FORMAT = "corbakasesi-theme"
private val themeJson = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val settingsStore = Libraries.loadSettingsStore(context)
    val settings = settingsStore.settings
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    // Which custom color is being edited in the picker dialog: 0 background, 1 surface, 2 accent, 3 text
    var editingSlot by remember { mutableStateOf<Int?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val message = runCatching {
                withContext(Dispatchers.IO) {
                    val text = themeJson.encodeToString(ThemeFile.serializer(), ThemeFile(colors = settingsStore.settings.customTheme))
                    context.contentResolver.openOutputStream(uri, "w")?.use { it.write(text.toByteArray()) } ?: error("Dosya açılamadı")
                }
            }.fold({ "Tema dışa aktarıldı" }, { Log.e(TAG, "Theme export failed", it); "Dışa aktarılamadı: ${it.message}" })
            snackbar.showSnackbar(message)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = runCatching {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: error("Dosya açılamadı")
                }
                val file = themeJson.decodeFromString(ThemeFile.serializer(), text)
                require(file.format == THEME_FILE_FORMAT && file.colors.isValid()) { "Bu bir Çorba Kasesi tema dosyası değil" }
                file.colors
            }
            result.onSuccess { colors -> settingsStore.update { copy(customTheme = colors, themeId = AppThemes.CUSTOM_ID) } }
            snackbar.showSnackbar(result.fold({ "Tema içe aktarıldı" }, { Log.e(TAG, "Theme import failed", it); "İçe aktarılamadı: ${it.message}" }))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tema") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Hazır temalar", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            for (theme in AppThemes.presets) {
                ThemeOption(
                    name = if (theme.id == AppThemes.DEFAULT_ID) "${theme.name} (varsayılan)" else theme.name,
                    colors = theme.colors,
                    selected = settings.themeId == theme.id,
                    onClick = { settingsStore.update { copy(themeId = theme.id) } },
                )
            }

            Spacer(Modifier.height(12.dp))
            Text("Özel tema", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ThemeOption(
                name = "Özel Tema",
                colors = settings.customTheme,
                selected = settings.themeId == AppThemes.CUSTOM_ID,
                onClick = { settingsStore.update { copy(themeId = AppThemes.CUSTOM_ID) } },
            )

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    val custom = settings.customTheme
                    val slots = listOf("Arka plan" to custom.background, "Kart / yüzey" to custom.surface, "Vurgu" to custom.accent, "Metin" to custom.text)
                    slots.forEachIndexed { index, (label, hex) ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { editingSlot = index }.padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ColorDot(parseHexColor(hex) ?: Color.Black, 28)
                            Spacer(Modifier.width(12.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Text(hex, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text("Bir hazır temadan başla:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (theme in AppThemes.presets) {
                            Box(
                                Modifier.size(32.dp).clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(parseHexColor(theme.colors.background)!!, parseHexColor(theme.colors.accent)!!)))
                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                    .clickable { settingsStore.update { copy(customTheme = theme.colors, themeId = AppThemes.CUSTOM_ID) } },
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { exportLauncher.launch("corbakasesi-tema.json") }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Dışa Aktar")
                        }
                        OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("İçe Aktar")
                        }
                    }
                }
            }
        }
    }

    val slot = editingSlot
    if (slot != null) {
        val custom = settings.customTheme
        val current = listOf(custom.background, custom.surface, custom.accent, custom.text)[slot]
        ColorPickerDialog(
            title = listOf("Arka plan", "Kart / yüzey", "Vurgu", "Metin")[slot],
            initial = parseHexColor(current) ?: Color.Black,
            onDismiss = { editingSlot = null },
            onPick = { color ->
                editingSlot = null
                val hex = color.toHex()
                val updated = when (slot) {
                    0 -> custom.copy(background = hex)
                    1 -> custom.copy(surface = hex)
                    2 -> custom.copy(accent = hex)
                    else -> custom.copy(text = hex)
                }
                // Editing a color switches to the custom theme, so the change is visible right away
                settingsStore.update { copy(customTheme = updated, themeId = AppThemes.CUSTOM_ID) }
            },
        )
    }
}

@Composable
private fun ThemeOption(name: String, colors: ThemeColors, selected: Boolean, onClick: () -> Unit) {
    val bg = parseHexColor(colors.background) ?: Color.Black
    val surface = parseHexColor(colors.surface) ?: Color.DarkGray
    val accent = parseHexColor(colors.accent) ?: Color.White
    val text = parseHexColor(colors.text) ?: Color.White
    Card(
        Modifier.fillMaxWidth().clip(CardShape).clickable(onClick = onClick)
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CardShape) else Modifier),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Mini preview of the theme: its background with a card, a line of text and an accent button
            Box(Modifier.size(width = 72.dp, height = 48.dp).clip(RoundedCornerShape(8.dp)).background(bg).padding(6.dp)) {
                Column(Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)).background(surface).padding(5.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Box(Modifier.size(width = 34.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(text))
                    Box(Modifier.size(width = 24.dp, height = 9.dp).clip(RoundedCornerShape(5.dp)).background(accent))
                }
            }
            Spacer(Modifier.width(16.dp))
            Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (selected) Icon(Icons.Filled.Check, contentDescription = "Seçili", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

private val CardShape = RoundedCornerShape(12.dp)

@Composable
private fun ColorDot(color: Color, sizeDp: Int) {
    Box(Modifier.size(sizeDp.dp).clip(CircleShape).background(color).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
}

/** HSV color picker: hue / saturation / brightness sliders plus a hex field, with a live preview. */
@Composable
private fun ColorPickerDialog(title: String, initial: Color, onDismiss: () -> Unit, onPick: (Color) -> Unit) {
    val startHsv = remember(initial) { FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toArgbInt(), it) } }
    var hue by remember { mutableFloatStateOf(startHsv[0]) }
    var saturation by remember { mutableFloatStateOf(startHsv[1]) }
    var value by remember { mutableFloatStateOf(startHsv[2]) }
    val color = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)))
    var hexText by remember { mutableStateOf(initial.toHex()) }

    fun setFromColor(c: Color) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(c.toArgbInt(), hsv)
        hue = hsv[0]; saturation = hsv[1]; value = hsv[2]
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Box(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(12.dp)).background(color).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)))
                Spacer(Modifier.height(16.dp))
                GradientSlider("Ton", hue, 0f..360f, Brush.horizontalGradient((0..6).map { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f))) })) {
                    hue = it; hexText = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))).toHex()
                }
                GradientSlider("Doygunluk", saturation, 0f..1f, Brush.horizontalGradient(listOf(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0f, value))), Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, value)))))) {
                    saturation = it; hexText = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))).toHex()
                }
                GradientSlider("Parlaklık", value, 0f..1f, Brush.horizontalGradient(listOf(Color.Black, Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, 1f)))))) {
                    value = it; hexText = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))).toHex()
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = hexText,
                    onValueChange = {
                        hexText = it
                        parseHexColor(it)?.let(::setFromColor)
                    },
                    label = { Text("Hex") },
                    singleLine = true,
                    isError = parseHexColor(hexText) == null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onPick(color) }) { Text("Tamam") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } },
    )
}

@Composable
private fun GradientSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, track: Brush, onChange: (Float) -> Unit) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
        // The gradient shows what moving the slider does
        Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(10.dp).clip(RoundedCornerShape(5.dp)).background(track))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
    }
}

private fun Color.toArgbInt(): Int = android.graphics.Color.argb((alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())

private const val TAG = "ThemeScreen"
