package org.coloradomesh.wearmesh

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import org.coloradomesh.wearmesh.ble.FoundRadio
import org.coloradomesh.wearmesh.ble.RadioScanner

/**
 * The M0 spike's one screen: find and pick a radio, connect or disconnect, test dictation, and
 * watch what the radio reports. The link itself lives in [SpikeService].
 */
class MainActivity : ComponentActivity() {
    private lateinit var scanner: RadioScanner
    private val found = mutableStateListOf<FoundRadio>()
    private var scanning by mutableStateOf(false)
    private var notice by mutableStateOf<String?>(null)
    private val prefs by lazy { getSharedPreferences("wearmesh", MODE_PRIVATE) }
    private var dictationStartedAt = 0L

    /** What to do once permissions are granted. */
    private var afterPermissions: (() -> Unit)? = null

    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val next = afterPermissions
        afterPermissions = null
        if (hasBluetoothPermission()) {
            next?.invoke()
        } else {
            notice = "Bluetooth permission is needed to reach the radio"
        }
    }

    private val dictation = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val seconds = (SystemClock.elapsedRealtime() - dictationStartedAt) / 1000.0
        val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        when {
            result.resultCode == Activity.RESULT_OK && text != null ->
                SpikeLog.log("M0 dictation (%.1f s, network: %s): \"%s\"".format(seconds, networkNow(), text))
            else -> SpikeLog.log("M0 dictation: no result after %.1f s (network: %s)".format(seconds, networkNow()))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SpikeLog.init(this)
        scanner = RadioScanner(this)
        setContent { MaterialTheme { SpikeScreen() } }
    }

    override fun onStop() {
        scanner.stop()
        scanning = false
        super.onStop()
    }

    private fun hasBluetoothPermission(): Boolean =
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT).all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    /** Runs [action] now if Bluetooth permission is granted, otherwise after asking for it. */
    private fun withPermissions(action: () -> Unit) {
        if (hasBluetoothPermission()) {
            action()
            return
        }
        afterPermissions = action
        permissionRequest.launch(
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                // Without it the service still runs; its notification just is not shown.
                Manifest.permission.POST_NOTIFICATIONS,
            ),
        )
    }

    private fun scan() = withPermissions {
        notice = scanner.blocker()
        if (notice != null) return@withPermissions
        found.clear()
        scanning = true
        scanner.start(
            onFound = { radio ->
                val at = found.indexOfFirst { it.address == radio.address }
                if (at >= 0) found[at] = radio else found.add(radio)
            },
            onDone = { error ->
                scanning = false
                notice = error ?: if (found.isEmpty()) "No radios found. Is another app connected to it?" else null
            },
        )
    }

    private fun connect(address: String, name: String) = withPermissions {
        scanner.stop()
        scanning = false
        found.clear()
        notice = null
        prefs.edit().putString(KEY_ADDRESS, address).putString(KEY_NAME, name).apply()
        SpikeService.connect(this, address)
    }

    private fun testDictation() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a test message")
        try {
            dictationStartedAt = SystemClock.elapsedRealtime()
            dictation.launch(intent)
        } catch (e: ActivityNotFoundException) {
            SpikeLog.log("M0 dictation: this watch has no speech recognizer")
        }
    }

    /** Whether the watch has any network right now, and over what; the dictation test records it. */
    private fun networkNow(): String {
        val manager = getSystemService(ConnectivityManager::class.java)
        val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return "none"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "phone over Bluetooth"
            else -> "other"
        }
    }

    @Composable
    private fun SpikeScreen() {
        val state by SpikeState.state.collectAsStateWithLifecycle()
        val lines by SpikeLog.lines.collectAsStateWithLifecycle()
        val listState = rememberScalingLazyListState()
        val savedAddress = prefs.getString(KEY_ADDRESS, null)
        val savedName = prefs.getString(KEY_NAME, null) ?: savedAddress

        Scaffold(
            timeText = { TimeText() },
            positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
        ) {
            ScalingLazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 8.dp, top = 32.dp, end = 8.dp, bottom = 48.dp),
            ) {
                item { ListHeader { Text("WearMesh M0") } }
                item {
                    Text(
                        text = listOfNotNull(state.radioName, state.link).joinToString(" · "),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.caption1,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (state.running) {
                    item { WideChip("Disconnect", secondary = true) { SpikeService.stop(this@MainActivity) } }
                } else {
                    if (savedAddress != null) {
                        item { WideChip("Connect", detail = savedName) { connect(savedAddress, savedName ?: savedAddress) } }
                    }
                    item { WideChip(if (scanning) "Scanning…" else "Scan for radios", secondary = true, enabled = !scanning) { scan() } }
                    items(found, key = { it.address }) { radio ->
                        val signal = if (radio.rssi != 0) "${radio.rssi} dBm" else radio.address
                        WideChip(radio.name, detail = signal) { connect(radio.address, radio.name) }
                    }
                }
                notice?.let { text -> item { Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.caption2) } }

                item { WideChip("Test dictation", secondary = true) { testDictation() } }

                item {
                    val facts = listOfNotNull(
                        state.firmware?.let { "Firmware $it" },
                        state.packetSize?.let { "Packet size $it B" },
                        "Contacts ${state.contacts} · Messages ${state.messages}",
                        state.radioMillivolts?.let { "Radio battery $it mV" },
                    )
                    Text(facts.joinToString("\n"), textAlign = TextAlign.Center, style = MaterialTheme.typography.caption2)
                }

                if (state.channels.isNotEmpty()) {
                    item { ListHeader { Text("Channels (${state.channels.size})") } }
                    items(state.channels, key = { it.index }) { channel ->
                        Text("${channel.index}  ${channel.name}", style = MaterialTheme.typography.caption1, modifier = Modifier.fillMaxWidth())
                    }
                }

                item { ListHeader { Text("Log") } }
                // No keys: two identical lines in the same millisecond would make duplicate keys.
                items(lines.take(SHOWN_LOG_LINES)) { line ->
                    Text("${SpikeLog.clock(line.at)} ${line.text}", style = MaterialTheme.typography.caption3, modifier = Modifier.fillMaxWidth())
                }
                item { WideChip("Clear log", secondary = true) { SpikeLog.clear() } }
            }
        }
    }

    @Composable
    private fun WideChip(
        label: String,
        detail: String? = null,
        secondary: Boolean = false,
        enabled: Boolean = true,
        onClick: () -> Unit,
    ) {
        Chip(
            onClick = onClick,
            label = { Text(label) },
            secondaryLabel = if (detail != null) {
                { Text(detail) }
            } else {
                null
            },
            colors = if (secondary) ChipDefaults.secondaryChipColors() else ChipDefaults.primaryChipColors(),
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    private companion object {
        const val KEY_ADDRESS = "radio_address"
        const val KEY_NAME = "radio_name"
        const val SHOWN_LOG_LINES = 80
    }
}
