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
    val keepContacts: Boolean = false,
)

/** Settings controls and navigation actions, with radio and permission options shown as needed. */
@Composable
fun SettingsScreen(
    state: SettingsState,
    onMode: () -> Unit,
    onInterval: () -> Unit,
    onAllowAlarms: () -> Unit,
    onNotifications: () -> Unit,
    onComplication: () -> Unit,
    onKeepContacts: () -> Unit,
    onQuickReplies: () -> Unit,
    onAllowNotifications: () -> Unit,
    onReadEverything: () -> Unit,
    onDiagnostics: () -> Unit,
    onAbout: () -> Unit,
    onQuit: () -> Unit,
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
            item { WideChip("Quick replies", detail = "Change, add or remove", secondary = true) { onQuickReplies() } }
            item { WideChip("Complication", detail = "What its count counts", secondary = true) { onComplication() } }
            item {
                WideChip(
                    "Keep contacts on radio",
                    detail = if (state.keepContacts) {
                        "On: people you message are made favourites, and put back if dropped"
                    } else {
                        "Off. Changes the radio's contacts: tap to turn on"
                    },
                    secondary = !state.keepContacts,
                    detailLines = 2,
                ) { onKeepContacts() }
            }
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
        item { WideChip("Diagnostics", detail = "The log, and saving it", secondary = true) { onDiagnostics() } }
        item { WideChip("About WearMesh", secondary = true) { onAbout() } }
        item {
            WideChip(
                "Quit WearMesh",
                detail = "Stops Live and check-ins until you open it again",
                secondary = true,
                detailLines = 2,
            ) { onQuit() }
        }
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

/**
 * Settings › Complication (decided 31 October 2026): count every conversation that notifies, as
 * before, or one channel. [channels] are conversation and name pairs, null while they load;
 * [chosen] is null for all.
 */
@Composable
fun ComplicationScreen(channels: List<Pair<String, String>>?, chosen: String?, onChoose: (String?) -> Unit) {
    ScreenList {
        item { ListHeader { Text("Complication") } }
        item { Note("What the WearMesh complication on your watch face counts. A channel shows its unread count over its short name.") }
        if (channels == null) {
            item { Note("Loading channels…") }
            return@ScreenList
        }
        item {
            WideChip("All that notify", detail = if (chosen == null) "Chosen" else "Every conversation with notifications on", secondary = chosen != null) {
                onChoose(null)
            }
        }
        items(channels, key = { it.first }) { (conversation, name) ->
            WideChip(name, detail = if (conversation == chosen) "Chosen" else null, secondary = conversation != chosen) { onChoose(conversation) }
        }
    }
}

/** A conversation's line on the Notifications screen. */
data class NotifyRow(val conversation: String, val title: String, val kind: org.coloradomesh.wearmesh.notify.NotifyRules.Kind, val on: Boolean)

/**
 * The log, what is stored, and what the radio last reported. With the log's two controls for
 * testers (M5 step 2): whether message text goes into it, and saving it where adb can fetch it.
 * [saved] is the last saved file's name, an empty string on failure, or null when no result is
 * shown. While [saving], Save log and Clear log are disabled.
 */
@Composable
fun DiagnosticsScreen(
    link: SpikeState.Snapshot,
    counts: StoreCounts?,
    lines: List<SpikeLog.Line>,
    showText: Boolean,
    saving: Boolean,
    saved: String?,
    onShowText: () -> Unit,
    onSaveLog: () -> Unit,
    onClearLog: () -> Unit,
) {
    ScreenList {
        item { ListHeader { Text("Diagnostics") } }
        item {
            WideChip(
                "Message text in log",
                detail = if (showText) "Shown. Tap to hide it from now on" else "Hidden: who and when only. Tap to show",
                secondary = !showText,
                detailLines = 2,
            ) { onShowText() }
        }
        item {
            WideChip(
                if (saving) "Saving the log…" else "Save log",
                detail = "To Download/${SpikeLog.SAVE_FOLDER}, for adb pull",
                secondary = true,
                enabled = !saving,
            ) { onSaveLog() }
        }
        when {
            saved == null -> {}
            saved.isEmpty() -> item { Note("The log could not be saved.") }
            else -> item { Note("Saved as $saved. On a computer: adb pull /sdcard/Download/${SpikeLog.SAVE_FOLDER}/") }
        }
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
        item { WideChip("Clear log", secondary = true, enabled = !saving) { onClearLog() } }
    }
}

private const val SHOWN_LOG_LINES = 80

/**
 * Settings › About (M5 step 2): what testers report and where the code is. [radio] is the radio
 * in use's name, [model] and [firmware] as it last reported them; each is null until known.
 */
@Composable
fun AboutScreen(version: String, radio: String?, model: String?, firmware: String?) {
    ScreenList {
        item { ListHeader { Text("About") } }
        item { Note("WearMesh $version") }
        item {
            Note(
                if (radio == null) {
                    "No radio chosen"
                } else {
                    listOfNotNull("Radio: $radio", model, firmware?.let { "Firmware $it" }).joinToString("\n")
                },
            )
        }
        item { Note("A MeshCore client for Wear OS, from Colorado Mesh (coloradomesh.org). Not affiliated with MeshCore.") }
        item { Note("Free software under the GNU GPL, version 3. Source: github.com/Colorado-Mesh/wearmesh") }
        item { Note("To report a problem, open an issue there, with the log from Diagnostics › Save log.") }
    }
}
