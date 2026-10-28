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
    val checkInOn: Boolean,
    val intervalMinutes: Int,
    val alarmsAllowed: Boolean,
    val liveRunning: Boolean,
    val hasRadio: Boolean,
    val syncing: Boolean,
)

@Composable
fun SettingsScreen(
    state: SettingsState,
    onCheckIn: () -> Unit,
    onInterval: () -> Unit,
    onAllowAlarms: () -> Unit,
    onLive: () -> Unit,
    onReadEverything: () -> Unit,
    onDiagnostics: () -> Unit,
) {
    ScreenList {
        item { ListHeader { Text("Settings") } }
        item {
            WideChip(
                label = if (state.checkInOn) "Check-in: on" else "Check-in: off",
                detail = "Syncs with the radio, also while the watch sleeps",
                secondary = !state.checkInOn,
                detailLines = 2,
            ) { onCheckIn() }
        }
        item { WideChip("Every ${state.intervalMinutes} min", detail = "Tap to change", secondary = true) { onInterval() } }
        if (state.checkInOn && !state.alarmsAllowed) {
            item { WideChip("Allow alarms", detail = "Needed for Check-in") { onAllowAlarms() } }
        }
        if (state.hasRadio) {
            item {
                WideChip(
                    label = if (state.liveRunning) "Live: disconnect" else "Live: connect",
                    detail = "Holds the link open; uses more battery",
                    secondary = true,
                    detailLines = 2,
                ) { onLive() }
            }
            item {
                WideChip(
                    label = "Read channels and contacts again",
                    detail = when {
                        state.liveRunning -> "Disconnect Live first"
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
