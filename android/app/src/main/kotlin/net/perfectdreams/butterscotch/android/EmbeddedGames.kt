package net.perfectdreams.butterscotch.android

import android.content.Context
import net.perfectdreams.butterscotch.android.library.GameEntry
import net.perfectdreams.butterscotch.android.library.GameLibrary
import java.io.File
import java.util.UUID

/**
 * Games bundled inside the APK. Every top level asset folder named `game`, `game1`, `game2`, ... that has a
 * `data.win` in it is one game (sibling files such as audio groups / external .ogg files go next to it).
 *
 * Nothing is copied out of the APK: the runtime reads each bundle through a virtual "/@assets/<folder>" path
 * (see [ApkAssets]). Each game gets a stable id derived from its folder name, so its saves live in their own
 * library folder and survive app updates that ship a new build of the game.
 */
object EmbeddedGames {
    private const val WAD_FILENAME = "data.win"
    private val FOLDER_REGEX = Regex("game\\d*")

    data class Game(val folder: String, val id: UUID) {
        val bundleDir: File get() = File("${ApkAssets.PREFIX}$folder")
    }

    /** Bundled games in folder order (game, game1, game2, ...). Filled by [init]. */
    var games: List<Game> = emptyList()
        private set

    /** Sets up the APK asset reader and finds the bundled games. Cheap to call again; needed by every entry point since the process may start in any activity. */
    fun init(context: Context) {
        ApkAssets.init(context.assets)
        if (games.isNotEmpty()) return

        val assets = context.assets
        games = assets.list("").orEmpty()
            .filter { FOLDER_REGEX.matches(it) && assets.list(it).orEmpty().contains(WAD_FILENAME) }
            .sortedBy { it.removePrefix("game").toIntOrNull() ?: -1 }
            .map { Game(it, UUID.nameUUIDFromBytes("mercimekcik:embedded:$it".toByteArray())) }
    }

    fun find(id: UUID): Game? = games.firstOrNull { it.id == id }

    /** Creates or refreshes the library entries for every bundled game. Must be called on the main thread, after [init]. */
    fun ensureEntries(context: Context, library: GameLibrary) {
        val stamp = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString()

        for (game in games) {
            val stampFile = File(library.gameDir(game.id), ".embedded_stamp")
            val existing = library.findById(game.id)
            // Only re-read the WAD header when the APK (and so maybe the game) changed
            if (existing != null && stampFile.exists() && stampFile.readText() == stamp)
                continue

            library.gameDir(game.id).mkdirs()
            val (title, wadVersion) = ParsedDataWin.parseLight(File(game.bundleDir, WAD_FILENAME).path)?.use { dw ->
                (dw.displayName ?: dw.name)?.takeIf { it.isNotBlank() } to dw.wadVersion
            } ?: (null to -1)
            val gameType = GameEntry.GameType.GameMakerStudio(wadVersion = wadVersion, filename = WAD_FILENAME)

            if (existing == null) {
                val dir = library.gameDir(game.id)
                library.commit(
                    GameLibrary.StagedGame(game.id, game.bundleDir, File(dir, "saves").apply { mkdirs() }),
                    title = title ?: game.folder,
                    gameType = gameType,
                )
            } else {
                library.update(game.id) { it.copy(title = title ?: it.title, gameType = gameType) }
            }
            stampFile.writeText(stamp)
        }
    }
}
