package org.coloradomesh.wearmesh.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

/**
 * Home: the status line, Check now, every conversation in Home's order (see [HomeRows]), then
 * Radios and Settings.
 */
@Composable
fun HomeScreen(
    radioName: String?,
    status: String,
    rows: List<HomeRow>,
    now: Long,
    checkNowEnabled: Boolean,
    onCheckNow: () -> Unit,
    onOpen: (HomeRow) -> Unit,
    onRadios: () -> Unit,
    onSettings: () -> Unit,
) {
    ScreenList {
        item { ListHeader { Text(radioName ?: "WearMesh", maxLines = 1) } }
        item { Note(status) }
        item { WideChip("Check now", secondary = true, enabled = checkNowEnabled) { onCheckNow() } }
        if (rows.isEmpty()) {
            item { Note("No channels yet. They appear after the first sync with the radio.") }
        }
        items(rows, key = { it.conversation }) { row ->
            val label = if (row.unread > 0) "${row.title} (${row.unread})" else row.title
            val detail = row.last?.let { "${TimeAgo.format(now, it.receivedAt)} · ${HomeRows.preview(it)}" } ?: "No messages yet"
            WideChip(label, detail = detail, secondary = row.unread == 0) { onOpen(row) }
        }
        item { WideChip("Radios", secondary = true) { onRadios() } }
        item { WideChip("Settings", secondary = true) { onSettings() } }
    }
}

@Composable
fun WelcomeScreen(onFind: () -> Unit) {
    ScreenList {
        item { ListHeader { Text("WearMesh") } }
        item {
            Text(
                "MeshCore messages on your watch, straight from your companion radio. Keep the radio nearby, " +
                    "and disconnect the phone app from it: a radio talks to one device at a time.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.caption1,
            )
        }
        item { WideChip("Find my radio") { onFind() } }
    }
}
