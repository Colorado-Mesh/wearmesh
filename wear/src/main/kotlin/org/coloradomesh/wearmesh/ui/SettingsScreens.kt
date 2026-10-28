package org.coloradomesh.wearmesh.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import org.coloradomesh.wearmesh.SpikeLog
import org.coloradomesh.wearmesh.SpikeState
import org.coloradomesh.wearmesh.data.StoreCounts

/** What Settings shows; the actions are the callbacks. */
data class SettingsState(
    val live: Boolean,
    val intervalMinutes: Int,
    val alarmsAllowed: Boolean,
    val liveRunning: Boolean,
    val hasRadio: Boolean,
    val syncing: Boolean,
    val notificationsAllowed: Boolean,
)

@Composable
fun SettingsScreen(
    state: SettingsState,
    onMode: () -> Unit,
    onInterval: () -> Unit,
    onAllowAlarms: () -> Unit,
    onNotifications: () -> Unit,
    onAllowNotifications: () -> Unit,
    onReadEverything: () -> Unit,
    onDiagnostics: () -> Unit,
) {
    ScreenList {
        item { ListHeader { Text("Settings") } }
        item {
            WideChip(
                label = if (state.live) "Mode: Live" else "Mode: Check-in",
                detail = if (state.live) {
                    "Holds the link open; uses more battery. Tap for Check-in"
                } else {
                    "Syncs on a schedule, also while the watch sleeps. Tap for Live"
                },
                secondary = true,
                detailLines = 2,
            ) { onMode() }
        }
        if (!state.live) {
            item { WideChip("Every ${state.intervalMinutes} min", detail = "Tap to change", secondary = true) { onInterval() } }
            if (!state.alarmsAllowed) {
                item { WideChip("Allow alarms", detail = "Needed for Check-in") { onAllowAlarms() } }
            }
        }
        if (state.hasRadio) {
            item { WideChip("Notifications", detail = "On or off for each conversation", secondary = true) { onNotifications() } }
        }
        if (!state.notificationsAllowed) {
            item { WideChip("Allow notifications", detail = "They are off for WearMesh", detailLines = 2) { onAllowNotifications() } }
        }
        if (state.hasRadio) {
            item {
                WideChip(
                    label = "Read channels and contacts again",
                    detail = when {
                        state.liveRunning -> "Live reads them when it connects"
                        state.syncing -> "Wait for the sync under way"
                        else -> "About 10 s"
                    },
                    secondary = true,
                    enabled = !state.liveRunning && !state.syncing,
                ) { onReadEverything() }
            }
        }
        item { WideChip("Diagnostics", secondary = true) { onDiagnostics() } }
    }
}

/** Every conversation, each switched on or off for notifications. */
@Composable
fun NotificationsScreen(rows: List<NotifyRow>, onToggle: (NotifyRow) -> Unit) {
    ScreenList {
        item { ListHeader { Text("Notifications") } }
        item { Note("Direct messages and private channels are on unless turned off. A message naming your radio always notifies.") }
        items(rows, key = { it.conversation }) { row ->
            WideChip(row.title, detail = if (row.on) "On" else "Off", secondary = !row.on) { onToggle(row) }
        }
    }
}

/** A conversation's line on the Notifications screen. */
data class NotifyRow(val conversation: String, val title: String, val kind: org.coloradomesh.wearmesh.notify.NotifyRules.Kind, val on: Boolean)

/** The log, what is stored, and what the radio last reported. */
@Composable
fun DiagnosticsScreen(link: SpikeState.Snapshot, counts: StoreCounts?, lines: List<SpikeLog.Line>, onClearLog: () -> Unit) {
    ScreenList {
        item { ListHeader { Text("Diagnostics") } }
        item {
            val facts = listOfNotNull(
                link.radioName?.let { "Radio $it" },
                "Live: ${link.link}",
                link.firmware?.let { "Firmware $it" },
                link.packetSize?.let { "Packet size $it B" },
                link.radioMillivolts?.let { "Radio battery $it mV" },
                counts?.let {
                    "Stored: ${it.messages} messages in ${it.conversations} conversations, " +
                        "${it.channels} channels, ${it.contacts} contacts"
                },
            )
            Text(facts.joinToString("\n"), textAlign = TextAlign.Center, style = MaterialTheme.typography.caption2)
        }
        item { ListHeader { Text("Log") } }
        // No keys: two identical lines in the same millisecond would make duplicate keys.
        items(lines.take(SHOWN_LOG_LINES)) { line ->
            Text(
                "${SpikeLog.clock(line.at)} ${line.text}",
                style = MaterialTheme.typography.caption3,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item { WideChip("Clear log", secondary = true) { onClearLog() } }
    }
}

private const val SHOWN_LOG_LINES = 80
