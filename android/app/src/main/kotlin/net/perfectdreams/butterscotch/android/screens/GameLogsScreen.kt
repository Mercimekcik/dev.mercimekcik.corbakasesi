package net.perfectdreams.butterscotch.android.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun GameLogContent(logsDir: File, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            // The file only exists after the game has been launched at least once
            text = File(logsDir, "latest.log").takeIf { it.exists() }?.readText(Charsets.UTF_8)
        }
    }

    Box(
        modifier
            .padding(24.dp)
            .horizontalScroll(rememberScrollState())
            .verticalScroll(rememberScrollState())
    ) {
        val _text = text

        if (_text != null) {
            Text(
                text = _text,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                lineHeight = TextUnit(11f, TextUnitType.Sp),
                color = Color(0xFFE0E0E0),
                softWrap = true
            )
        } else {
            Text("No logs yet... :(")
        }
    }
}
