package net.perfectdreams.butterscotch.android

import android.content.res.AssetManager
import java.io.File
import java.io.InputStream

/** Native side lives in src/main/cpp/apk_assets.c: makes "/@assets/..." paths readable by the runtime. */
object ApkAssets {
    const val PREFIX = "/@assets/"

    init {
        System.loadLibrary("butterscotch")
    }

    external fun init(assetManager: AssetManager)

    /** Opens [file] for reading on the JVM side, resolving "/@assets/..." paths through [assets] (java.io can't see them). */
    fun openStream(assets: AssetManager, file: File): InputStream =
        if (file.path.startsWith(PREFIX)) assets.open(file.path.removePrefix(PREFIX)) else file.inputStream()
}
