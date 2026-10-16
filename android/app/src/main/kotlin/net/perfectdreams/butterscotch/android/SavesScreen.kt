package net.perfectdreams.butterscotch.android

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.net.Uri
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Import/export of one bundled game's save files (its active save slot folder) as a .zip.
 * Import replaces the current save, so it asks first. [SaveSlotZip] does the actual zip work.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavesScreen(
    title: String,
    saveDir: File,
    accentColor: Color?,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    // Bumped after an import so the summary card rereads the folder
    var refresh by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }

    val files = remember(saveDir, refresh) {
        saveDir.walkTopDown().filter { it.isFile }.sortedBy { it.relativeTo(saveDir).invariantSeparatorsPath.lowercase() }.toList()
    }
    val lastModified = files.maxOfOrNull { it.lastModified() }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val message = runCatching { SaveSlotZip.exportSlotToZip(context, saveDir, uri) }
                .fold(
                    onSuccess = { "Kayıt dışa aktarıldı ($it dosya)" },
                    onFailure = { Log.e(TAG, "Export failed", it); "Dışa aktarılamadı: ${it.message}" },
                )
            busy = false
            snackbar.showSnackbar(message)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingImport = uri
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$title - Kayıtlar") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    if (files.isEmpty()) {
                        Text("Henüz kayıt yok", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Oyunu oynayıp kaydettiğinde burada görünecek. Başka bir cihazdan kayıt da içe aktarabilirsin.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text("${files.size} kayıt dosyası", style = MaterialTheme.typography.titleMedium)
                        if (lastModified != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Son değişiklik: ${SimpleDateFormat("d MMMM yyyy, HH:mm", Locale.forLanguageTag("tr")).format(Date(lastModified))}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
                    exportLauncher.launch("${title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')}-kayit-$date.zip")
                },
                enabled = !busy && files.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                colors = if (accentColor != null) ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color.White) else ButtonDefaults.buttonColors(),
            ) {
                Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Dışa Aktar (.zip)")
            }

            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("İçe Aktar (.zip)")
            }

            if (files.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Dosyalar",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Card(Modifier.fillMaxWidth()) {
                    files.forEachIndexed { index, file ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        SaveFileRow(file.relativeTo(saveDir).invariantSeparatorsPath, file.length(), file.lastModified())
                    }
                }
            }
        }
    }

    val importUri = pendingImport
    if (importUri != null) {
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Kayıt içe aktarılsın mı?") },
            text = {
                Text(
                    if (files.isEmpty()) "Seçtiğin dosyadaki kayıt bu oyuna yüklenecek."
                    else "Mevcut kayıt silinip yerine seçtiğin dosyadaki kayıt konacak. Bu geri alınamaz."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    busy = true
                    scope.launch {
                        val message = runCatching { SaveSlotZip.importZipIntoSlot(context, importUri, saveDir) }
                            .fold(
                                onSuccess = { "Kayıt içe aktarıldı ($it dosya)" },
                                onFailure = { Log.e(TAG, "Import failed", it); "İçe aktarılamadı: ${it.message}" },
                            )
                        busy = false
                        refresh++
                        snackbar.showSnackbar(message)
                    }
                }) { Text("İçe Aktar") }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("İptal") } },
        )
    }
}

private const val TAG = "SavesScreen"

@Composable
private fun SaveFileRow(name: String, size: Long, lastModified: Long) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.InsertDriveFile,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                SimpleDateFormat("d MMM yyyy, HH:mm", Locale.forLanguageTag("tr")).format(Date(lastModified)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(formatSize(size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.forLanguageTag("tr"), "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> String.format(Locale.forLanguageTag("tr"), "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}
