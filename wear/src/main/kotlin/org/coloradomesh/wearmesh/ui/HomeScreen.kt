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
    offerCheckIn: Boolean,
    onCheckNow: () -> Unit,
    onOfferAnswered: (switch: Boolean) -> Unit,
    onOpen: (HomeRow) -> Unit,
    onRadios: () -> Unit,
    onSettings: () -> Unit,
) {
    ScreenList {
        item { ListHeader { Text(radioName ?: "WearMesh", maxLines = 1) } }
        item { Note(status) }
        if (offerCheckIn) {
            // The first-run offer (decided for M2): Live first, then the choice to save battery.
            item { Note("Save battery: sync every 15 minutes instead of staying connected?") }
            item { WideChip("Switch to Check-in") { onOfferAnswered(true) } }
            item { WideChip("Not now", secondary = true) { onOfferAnswered(false) } }
        }
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
