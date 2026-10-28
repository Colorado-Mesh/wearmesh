package org.coloradomesh.wearmesh.ui

import androidx.compose.runtime.Composable
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.Text
import org.coloradomesh.meshcore.ble.FoundRadio
import org.coloradomesh.wearmesh.KnownRadio

/** Radios this watch knows, the one in use first and marked. */
@Composable
fun RadiosScreen(known: List<KnownRadio>, chosen: String?, onOpen: (KnownRadio) -> Unit, onAdd: () -> Unit) {
    val ordered = known.sortedByDescending { it.address == chosen }
    ScreenList {
        item { ListHeader { Text("Radios") } }
        if (known.isEmpty()) item { Note("No radios yet") }
        items(ordered, key = { it.address }) { radio ->
            val inUse = radio.address == chosen
            WideChip(radio.name, detail = if (inUse) "In use" else radio.address, secondary = !inUse) { onOpen(radio) }
        }
        item { WideChip("Add a radio", secondary = true) { onAdd() } }
    }
}

/** One radio: use it, or forget it. */
@Composable
fun RadioScreen(radio: KnownRadio, inUse: Boolean, onUse: () -> Unit, onForget: () -> Unit) {
    ScreenList {
        item { ListHeader { Text(radio.name, maxLines = 1) } }
        item { Note(if (inUse) "In use · ${radio.address}" else radio.address) }
        if (!inUse) item { WideChip("Use this radio") { onUse() } }
        item { WideChip("Forget", secondary = true) { onForget() } }
    }
}

/** Radios found nearby, as the scanner lists them (paired ones first); tapping one adds it. */
@Composable
fun FindRadioScreen(scanning: Boolean, found: List<FoundRadio>, notice: String?, onScan: () -> Unit, onPick: (FoundRadio) -> Unit) {
    ScreenList {
        item { ListHeader { Text("Find a radio") } }
        item { WideChip(if (scanning) "Scanning…" else "Scan again", secondary = true, enabled = !scanning) { onScan() } }
        items(found, key = { it.address }) { radio ->
            val signal = if (radio.rssi != 0) "${radio.rssi} dBm" else radio.address
            WideChip(radio.name, detail = signal) { onPick(radio) }
        }
        notice?.let { text -> item { Note(text) } }
    }
}

/** Asks before something that cannot be undone. */
@Composable
fun ConfirmScreen(title: String, explanation: String, action: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    ScreenList {
        item { ListHeader { Text(title, maxLines = 2) } }
        item { Note(explanation) }
        item { WideChip(action) { onConfirm() } }
        item { WideChip("Cancel", secondary = true) { onCancel() } }
    }
}

/** After forgetting: an app cannot unpair a device, so this points to where the user can. */
@Composable
fun ForgottenScreen(onBluetoothSettings: () -> Unit, onDone: () -> Unit) {
    ScreenList {
        item { ListHeader { Text("Forgotten") } }
        item { Note("Its messages are deleted. To unpair it too, forget it in the watch's Bluetooth settings.") }
        item { WideChip("Bluetooth settings", secondary = true) { onBluetoothSettings() } }
        item { WideChip("Done") { onDone() } }
    }
}
