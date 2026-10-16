package net.perfectdreams.butterscotch.android

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.perfectdreams.butterscotch.android.theme.ButterscotchAndroidTheme
import java.io.File
import java.util.UUID

/** Start screen listing the games bundled in the `assets/game`, `assets/game1`, ... folders. Returning from [GameActivity] lands back here. */
class MainActivity : ComponentActivity() {
    // Physical keyboard / controller input is handed to whichever tool screen is listening (each only
    // consumes events while its screen is open), otherwise it keeps its normal behaviour
    private val gamepadTesterState = GamepadTesterState()
    private val keyboardTesterState = KeyboardTesterState()
    private val inputCapture = InputCaptureState()

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        inputCapture.onKeyEvent(event) || gamepadTesterState.onKeyEvent(event) || keyboardTesterState.onKeyEvent(event) || super.dispatchKeyEvent(event)

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        inputCapture.onMotionEvent(event) || gamepadTesterState.onMotionEvent(event) || super.dispatchGenericMotionEvent(event)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val gameLibrary = Libraries.loadGameLibrary(this.applicationContext)
        val error = runCatching {
            EmbeddedGames.init(this.applicationContext)
            EmbeddedGames.ensureEntries(this.applicationContext, gameLibrary)
        }
            .onFailure { Log.e(TAG, "Failed to prepare the bundled games", it) }
            .exceptionOrNull()

        val cards = EmbeddedGames.games.map { game ->
            val entry = gameLibrary.findById(game.id)
            GameCard(
                id = game.id,
                title = entry?.title ?: game.folder,
                launcher = LauncherInfo.load(this, game.folder),
                saveDir = entry?.let { gameLibrary.savesDir(it) },
            )
        }

        setContent {
            ButterscotchAndroidTheme {
                LaunchedEffect(Unit) { UpdateChecker.autoCheck() }
                UpdateDialog()
                // Which game's save screen is open, if any
                var savesFor by rememberSaveable { mutableStateOf<String?>(null) }
                var settingsOpen by rememberSaveable { mutableStateOf(false) }
                // Tool screens opened from settings; going back returns to the settings screen
                var settingsPage by rememberSaveable { mutableStateOf<String?>(null) }
                when (settingsPage) {
                    "gamepad" -> {
                        GamepadTesterScreen(gamepadTesterState, onBack = { settingsPage = null })
                        return@ButterscotchAndroidTheme
                    }
                    "keyboard" -> {
                        KeyboardTesterScreen(keyboardTesterState, onBack = { settingsPage = null })
                        return@ButterscotchAndroidTheme
                    }
                    "theme" -> {
                        ThemeScreen(onBack = { settingsPage = null })
                        return@ButterscotchAndroidTheme
                    }
                    "remap" -> {
                        InputRemapScreen(inputCapture, onBack = { settingsPage = null })
                        return@ButterscotchAndroidTheme
                    }
                }
                if (settingsOpen) {
                    AppSettingsScreen(onBack = { settingsOpen = false }, onOpenPage = { settingsPage = it })
                    return@ButterscotchAndroidTheme
                }
                val savesGame = cards.firstOrNull { it.id.toString() == savesFor }
                if (savesGame?.saveDir != null) {
                    SavesScreen(
                        title = savesGame.title,
                        saveDir = savesGame.saveDir,
                        accentColor = savesGame.launcher.accentColor,
                        onBack = { savesFor = null },
                    )
                    return@ButterscotchAndroidTheme
                }

                StartScreen(
                    games = cards,
                    error = when {
                        error != null -> stringResource(R.string.start_load_failed, error.message ?: error.toString())
                        cards.isEmpty() -> stringResource(R.string.start_no_games)
                        else -> null
                    },
                    onStart = ::startGame,
                    onOpenSaves = { savesFor = it.toString() },
                    onOpenSettings = { settingsOpen = true },
                )
            }
        }
    }

    private fun startGame(gameId: UUID) {
        startActivity(Intent(this, GameActivity::class.java).apply {
            putExtra(GameActivity.EXTRA_GAME_ID, gameId.toString())
        })
    }

    companion object {
        private const val TAG = "MainActivity"
        const val ACTION_LAUNCH_GAME = "net.perfectdreams.butterscotch.android.action.LAUNCH_GAME"
    }
}

private class GameCard(val id: UUID, val title: String, val launcher: LauncherInfo, val saveDir: File?)

/**
 * Optional start screen extras for a bundled game, read from `assets/launcher/<game folder>/`:
 * `logo.png` replaces the title text, `info.json` sets the button color and a short description.
 * Kept outside the game folder so the game bundle itself stays untouched.
 */
private class LauncherInfo(val logo: ImageBitmap?, val accentColor: Color?, val description: String?) {
    @Serializable
    private class InfoFile(val accentColor: String? = null, val description: String? = null)

    companion object {
        private const val TAG = "LauncherInfo"
        private val json = Json { ignoreUnknownKeys = true }

        fun load(context: Context, folder: String): LauncherInfo {
            val dir = "launcher/$folder"
            val logo = runCatching {
                context.assets.open("$dir/logo.png").use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            }.getOrNull()
            val info = runCatching {
                context.assets.open("$dir/info.json").use { json.decodeFromString<InfoFile>(it.readBytes().toString(Charsets.UTF_8)) }
            }.onFailure { if (it !is java.io.FileNotFoundException) Log.w(TAG, "Invalid $dir/info.json", it) }.getOrNull()
            val accentColor = info?.accentColor?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
            return LauncherInfo(logo, accentColor, info?.description)
        }
    }
}

@Composable
private fun StartScreen(games: List<GameCard>, error: String?, onStart: (UUID) -> Unit, onOpenSaves: (UUID) -> Unit, onOpenSettings: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(48.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    return@Column
                }

                for (game in games) {
                    GameRow(game, onStart = { onStart(game.id) }, onOpenSaves = { onOpenSaves(game.id) })
                }
            }

            Row(
                Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UpdateStatusChip()
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.start_settings))
                }
            }
        }
    }
}

@Composable
private fun GameRow(game: GameCard, onStart: () -> Unit, onOpenSaves: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val logo = game.launcher.logo
        if (logo != null) {
            Image(
                logo,
                contentDescription = game.title,
                modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(0.8f),
                contentScale = ContentScale.FillWidth,
                // Game logos are usually pixel art, keep them crisp when scaled up
                filterQuality = FilterQuality.None,
            )
        } else {
            Text(game.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        }

        val description = game.launcher.description
        if (description != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }

        Spacer(Modifier.height(16.dp))
        val accent = game.launcher.accentColor
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = onStart,
                modifier = Modifier.widthIn(min = 200.dp),
                colors = if (accent != null) ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White) else ButtonDefaults.buttonColors(),
            ) {
                Text(stringResource(R.string.start_game))
            }
            if (game.saveDir != null) {
                Spacer(Modifier.width(12.dp))
                FilledTonalIconButton(onClick = onOpenSaves) {
                    Icon(Icons.Filled.Save, contentDescription = stringResource(R.string.start_saves))
                }
            }
        }
    }
}
