package net.perfectdreams.butterscotch.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.FileNotFoundException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URL
import kotlin.coroutines.cancellation.CancellationException

/**
 * Looks at the latest GitHub release of [REPO] when the app starts and shows the result in the start screen's
 * top right corner ([UpdateStatusChip]). It never downloads anything itself: "İndir" opens the download site
 * ([SITE_URL], GitHub Pages from the repo's docs/ folder) in the browser.
 *
 * Release tags have to be the version name, e.g. "v2026.10.16" (the build's versionName is the build date).
 */
object UpdateChecker {
    private const val TAG = "UpdateChecker"
    const val REPO = "Mercimekcik/dev.mercimekcik.corbakasesi"
    const val REPO_URL = "https://github.com/$REPO"
    const val SITE_URL = "https://mercimekcik.github.io/dev.mercimekcik.corbakasesi/"
    private const val LATEST_RELEASE_API = "https://api.github.com/repos/$REPO/releases/latest"

    data class Release(val version: String, val notes: String)

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val release: Release) : State
        data object NoInternet : State
        data object Failed : State
    }

    var state by mutableStateOf<State>(State.Idle)
        private set

    /** The "new version" dialog, opened from the status chip. */
    var dialogVisible by mutableStateOf(false)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var autoChecked = false
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class GhRelease(
        @SerialName("tag_name") val tagName: String,
        val body: String? = null,
    )

    /** Once per app start. */
    fun autoCheck() {
        if (autoChecked) return
        autoChecked = true
        check()
    }

    fun check() {
        if (job?.isActive == true) return
        job = scope.launch {
            state = State.Checking
            state = try {
                val release = withContext(Dispatchers.IO) { fetchLatest() }
                when {
                    // No release published yet: nothing newer than this build
                    release == null -> State.UpToDate
                    compareVersions(release.version, BuildConfig.VERSION_NAME) > 0 -> State.Available(release)
                    else -> State.UpToDate
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Update check failed: $e", e)
                if (e is UnknownHostException || e is ConnectException || e is SocketTimeoutException || e is NoRouteToHostException) State.NoInternet else State.Failed
            }
        }
    }

    fun showDialog() {
        dialogVisible = state is State.Available
    }

    fun dismiss() {
        dialogVisible = false
    }

    /** null when the repo has no published release yet (GitHub answers 404). */
    private fun fetchLatest(): Release? {
        val connection = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "CorbaKasesi/${BuildConfig.VERSION_NAME}")
        }
        try {
            val text = try {
                connection.inputStream.bufferedReader().use { it.readText() }
            } catch (e: FileNotFoundException) {
                return null
            }
            val release = json.decodeFromString<GhRelease>(text)
            return Release(
                version = release.tagName.removePrefix("v").removePrefix("V"),
                notes = release.body.orEmpty().trim(),
            )
        } finally {
            connection.disconnect()
        }
    }

    /** Compares dotted versions number by number ("2026.10.16" < "2026.10.16.1" < "2026.11.2"). */
    fun compareVersions(a: String, b: String): Int {
        val left = a.split('.', '-').map { it.toIntOrNull() ?: 0 }
        val right = b.split('.', '-').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(left.size, right.size)) {
            val diff = left.getOrElse(i) { 0 }.compareTo(right.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }

    fun openInBrowser(context: Context, url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

/** Update status for the start screen's top right corner; tapping it opens the update or checks again. */
@Composable
fun UpdateStatusChip(modifier: Modifier = Modifier) {
    val state = UpdateChecker.state
    val available = state is UpdateChecker.State.Available
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = { if (available) UpdateChecker.showDialog() else UpdateChecker.check() },
        enabled = state != UpdateChecker.State.Checking,
        shape = RoundedCornerShape(50),
        // Fixed status colors so they read the same in every theme: green up to date, orange update, red offline/error
        color = when (state) {
            UpdateChecker.State.UpToDate -> Color(0xFF2E7D32)
            is UpdateChecker.State.Available -> Color(0xFFEF6C00)
            UpdateChecker.State.NoInternet, UpdateChecker.State.Failed -> Color(0xFFC62828)
            else -> colors.surfaceVariant
        },
        contentColor = if (state == UpdateChecker.State.Idle || state == UpdateChecker.State.Checking) colors.onSurfaceVariant else Color.White,
        modifier = modifier,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state == UpdateChecker.State.Checking || state == UpdateChecker.State.Idle) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
            } else {
                Icon(
                    when (state) {
                        is UpdateChecker.State.Available -> Icons.Filled.SystemUpdate
                        UpdateChecker.State.UpToDate -> Icons.Filled.CheckCircle
                        UpdateChecker.State.NoInternet -> Icons.Filled.CloudOff
                        else -> Icons.Filled.ErrorOutline
                    },
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                when (state) {
                    UpdateChecker.State.Idle, UpdateChecker.State.Checking -> "Denetleniyor"
                    UpdateChecker.State.UpToDate -> "Güncel"
                    is UpdateChecker.State.Available -> "Güncelleme var: ${state.release.version}"
                    UpdateChecker.State.NoInternet -> "İnternet yok"
                    UpdateChecker.State.Failed -> "Denetlenemedi"
                },
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/** "Yeni sürüm var" dialog, shown over whatever screen is open. */
@Composable
fun UpdateDialog() {
    val context = LocalContext.current
    val available = UpdateChecker.state as? UpdateChecker.State.Available
    if (!UpdateChecker.dialogVisible || available == null) return
    val release = available.release

    AlertDialog(
        onDismissRequest = { UpdateChecker.dismiss() },
        title = { Text("Yeni sürüm var") },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                Text("${BuildConfig.VERSION_NAME} → ${release.version}", style = MaterialTheme.typography.bodyLarge)
                if (release.notes.isNotEmpty()) {
                    Text(
                        release.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                Text(
                    "İndirme sayfasından yeni sürümü indirip mevcut uygulamanın üstüne kurabilirsin. Kayıtların silinmez.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                UpdateChecker.dismiss()
                UpdateChecker.openInBrowser(context, UpdateChecker.SITE_URL)
            }) { Text("İndir") }
        },
        dismissButton = {
            TextButton(onClick = { UpdateChecker.dismiss() }) { Text("Sonra") }
        },
    )
}
