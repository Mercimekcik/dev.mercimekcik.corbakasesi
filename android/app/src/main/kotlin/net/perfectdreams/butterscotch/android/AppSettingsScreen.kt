package net.perfectdreams.butterscotch.android

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import net.perfectdreams.butterscotch.android.components.PixelButtonFace
import net.perfectdreams.butterscotch.android.components.PixelIcons
import net.perfectdreams.butterscotch.android.components.drawPixelDPad
import net.perfectdreams.butterscotch.android.components.pixelText
import net.perfectdreams.butterscotch.android.settings.GamepadStyle
import net.perfectdreams.butterscotch.android.theme.AppThemes
import net.perfectdreams.butterscotch.android.theme.ButterscotchPrimary
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

private const val BUTTERSCOTCH_URL = "https://github.com/ButterscotchRunner/Butterscotch"

/** App wide settings opened from the start screen: developer mode and the about section. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(onBack: () -> Unit, onOpenPage: (String) -> Unit) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val settingsStore = Libraries.loadSettingsStore(context)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ayarlar") },
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
        ) {
            SectionTitle("Görünüm")
            Card(Modifier.fillMaxWidth()) {
                val themeName = when (val id = settingsStore.settings.themeId) {
                    AppThemes.CUSTOM_ID -> "Özel Tema"
                    else -> AppThemes.preset(id)?.name ?: "Koyu"
                }
                AboutRow("Tema", themeName, onClick = { onOpenPage("theme") })
            }

            Spacer(Modifier.height(24.dp))
            SectionTitle("Kontroller")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Tuş Görünümü", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Ekrandaki sanal gamepad'in nasıl çizileceği. Özel görsel atanmış tuşlar değişmez.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    val style = settingsStore.settings.gamepadStyle
                    val options = listOf(GamepadStyle.NORMAL to "Normal", GamepadStyle.PIXEL_ART to "Piksel Art")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        options.forEachIndexed { index, (value, label) ->
                            SegmentedButton(
                                selected = style == value,
                                onClick = { settingsStore.update { copy(gamepadStyle = value) } },
                                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                            ) { Text(label) }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    // Local while dragging so the preview follows the finger; persisted once on release
                    var opacity by remember { mutableFloatStateOf(settingsStore.settings.gamepadOpacity) }
                    GamepadStylePreview(style, opacity)

                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Tuş Opaklığı", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(
                            "%${(opacity * 100).roundToInt()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Slider(
                        value = opacity,
                        onValueChange = { opacity = (it * 20).roundToInt() / 20f },
                        onValueChangeFinished = { settingsStore.update { copy(gamepadOpacity = opacity) } },
                        valueRange = 0.1f..1f,
                        // 5% steps between 10% and 100%
                        steps = 17,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth()) {
                AboutRow("Tuş Eşleme", "Klavye ve kontrolcü tuşlarını oyunda başka tuşlara çevir", onClick = { onOpenPage("remap") })
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                AboutRow("Gamepad Test", "Bağlı kontrolcünün tuşlarını, çubuklarını ve tetiklerini test et", onClick = { onOpenPage("gamepad") })
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                AboutRow("Klavye Test", "Bağlı klavyenin tuşlarını test et", onClick = { onOpenPage("keyboard") })
            }

            Spacer(Modifier.height(24.dp))
            SectionTitle("Geliştirici")
            Card(Modifier.fillMaxWidth()) {
                val enabled = settingsStore.settings.developerMode
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { settingsStore.update { copy(developerMode = !enabled) } }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Geliştirici Modu", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Oyun içi menüde hileleri, logları ve GML Profiler'ı gösterir.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Switch(checked = enabled, onCheckedChange = { settingsStore.update { copy(developerMode = it) } })
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionTitle("Hakkında")
            Card(Modifier.fillMaxWidth()) {
                AboutRow("Uygulama", "${stringResource(R.string.app_name)} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                AboutRow(
                    "Oyun motoru",
                    "Butterscotch, açık kaynak bir GameMaker runtime'ı",
                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BUTTERSCOTCH_URL))) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                AboutRow(
                    "Kaynak kodu",
                    UpdateChecker.REPO_URL.removePrefix("https://"),
                    onClick = { UpdateChecker.openInBrowser(context, UpdateChecker.REPO_URL) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                AboutRow("Lisans", "GNU Affero General Public License v3.0")
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun AboutRow(title: String, value: String, onClick: (() -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// A few sample buttons in the chosen style, so the difference is visible before starting a game
@Composable
private fun GamepadStylePreview(style: GamepadStyle, opacity: Float) {
    Row(
        // alpha after background: only the sample buttons fade, the dark backdrop stays
        Modifier.fillMaxWidth().background(Color.Black, RoundedCornerShape(12.dp)).padding(16.dp).alpha(opacity),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val buttonModifier = Modifier.size(52.dp)
        if (style == GamepadStyle.PIXEL_ART) {
            Canvas(Modifier.size(76.dp)) { drawPixelDPad(upHeld = true, downHeld = false, leftHeld = false, rightHeld = false) }
            Box(buttonModifier) { PixelButtonFace(pixelText("Z"), pressed = false) }
            Box(buttonModifier) { PixelButtonFace(pixelText("X"), pressed = true) }
            Box(buttonModifier) { PixelButtonFace(PixelIcons.MENU, pressed = false) }
        } else {
            Box(Modifier.size(76.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Box(Modifier.size(30.dp).offset(y = (-12).dp).clip(CircleShape).background(Color.White.copy(alpha = 0.6f)))
            }
            for ((label, pressed) in listOf("Z" to false, "X" to true)) {
                Box(
                    buttonModifier.clip(CircleShape).background(if (pressed) ButterscotchPrimary.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center,
                ) { Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp) }
            }
            Box(buttonModifier.clip(CircleShape).background(Color.White.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Menu, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
    }
}
