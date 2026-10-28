package org.coloradomesh.wearmesh

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.coloradomesh.meshcore.ble.FoundRadio
import org.coloradomesh.meshcore.ble.RadioScanner
import org.coloradomesh.wearmesh.data.ConversationLast
import org.coloradomesh.wearmesh.data.Storage
import org.coloradomesh.wearmesh.ui.ConfirmScreen
import org.coloradomesh.wearmesh.ui.ConversationScreen
import org.coloradomesh.wearmesh.ui.DiagnosticsScreen
import org.coloradomesh.wearmesh.ui.FindRadioScreen
import org.coloradomesh.wearmesh.ui.ForgottenScreen
import org.coloradomesh.wearmesh.ui.HomeRows
import org.coloradomesh.wearmesh.ui.HomeScreen
import org.coloradomesh.wearmesh.ui.RadioScreen
import org.coloradomesh.wearmesh.ui.RadiosScreen
import org.coloradomesh.wearmesh.ui.SettingsScreen
import org.coloradomesh.wearmesh.ui.SettingsState
import org.coloradomesh.wearmesh.ui.WelcomeScreen

/**
 * The app's one activity: the screens (see docs/M2-step3-screens.md and the `ui` package), and
 * what they need from Android, such as Bluetooth permission, scanning, and the system's settings
 * pages. Live runs in [SpikeService]; Check-in in [CheckInService]. Opening the app syncs.
 */
class MainActivity : ComponentActivity() {
    private lateinit var scanner: RadioScanner
    private val found = mutableStateListOf<FoundRadio>()
    private var scanning by mutableStateOf(false)
    private var notice by mutableStateOf<String?>(null)
    private var checkInOn by mutableStateOf(false)
    private var intervalMinutes by mutableStateOf(15)
    private var exactAlarmsAllowed by mutableStateOf(true)

    /** Whether the last radio forgotten was the one in use, so its messages were deleted. */
    private var forgotInUse by mutableStateOf(false)

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SpikeLog.init(this)
        scanner = RadioScanner(this)
        checkInOn = CheckIn.isOn(this)
        intervalMinutes = CheckIn.intervalMinutes(this)
        Radios.load(this)
        SyncStatus.load(this)
        val start = if (Radios.state.value?.chosen != null) HOME else WELCOME
        setContent { MaterialTheme { App(start) } }
    }

    override fun onStart() {
        super.onStart()
        syncOnOpen()
    }

    override fun onResume() {
        super.onResume()
        // The user may have just allowed "Alarms & reminders" in Settings.
        exactAlarmsAllowed = CheckIn.exactAlarmsAllowed(this)
        Storage.refreshCounts(this)
    }

    override fun onStop() {
        scanner.stop()
        scanning = false
        super.onStop()
    }

    /**
     * Opening the app syncs (decided for M2), unless Live holds the radio, a sync is running, or
     * one ended, worked or not, in the last [OPEN_SYNC_GAP_MS]: a watch comes on screen at every
     * glance, and a radio that is not answering should not be tried at each one.
     */
    private fun syncOnOpen() {
        if (Radios.state.value?.chosen == null || SpikeState.state.value.running || !hasBluetoothPermission()) return
        val status = SyncStatus.state.value
        val lastEnded = maxOf(status.lastSyncAt, status.lastAttemptAt)
        if (status.syncing || System.currentTimeMillis() - lastEnded < OPEN_SYNC_GAP_MS) return
        try {
            CheckIn.onOpen(this)
        } catch (e: IllegalStateException) {
            SpikeLog.log("Check-in (app opened): could not start (${e.message})")
        }
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
                // Without it the services still run; their notifications just are not shown.
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
                notice = error ?: if (found.isEmpty()) "No radios found. Is another device connected to it?" else null
            },
        )
    }

    /** Uses [radio], then syncs with it. Live is stopped first, and waited for, so it writes nothing more. */
    private fun use(radio: KnownRadio) = withPermissions {
        scanner.stop()
        scanning = false
        afterLiveStops {
            Radios.choose(this, radio)
            CheckIn.now(this)
        }
    }

    private fun forget(address: String) {
        forgotInUse = Radios.state.value?.chosen?.address == address
        if (forgotInUse) afterLiveStops { Radios.forget(this, address) } else Radios.forget(this, address)
    }

    /**
     * Runs [action] once Live has stopped, stopping it first if it is running. Stopping only asks
     * the service, so this waits until it says it is down, up to [LIVE_STOP_WAIT_MS].
     */
    private fun afterLiveStops(action: () -> Unit) {
        if (!SpikeState.state.value.running) {
            action()
            return
        }
        SpikeService.stop(this)
        lifecycleScope.launch {
            if (withTimeoutOrNull(LIVE_STOP_WAIT_MS) { SpikeState.state.first { !it.running } } == null) {
                SpikeLog.log("Live did not stop within ${LIVE_STOP_WAIT_MS / 1000} s; carrying on")
            }
            action()
        }
    }

    private fun toggleCheckIn() = withPermissions {
        checkInOn = !checkInOn
        CheckIn.setOn(this, checkInOn)
        exactAlarmsAllowed = CheckIn.exactAlarmsAllowed(this)
    }

    private fun toggleLive() = withPermissions {
        val radio = Radios.state.value?.chosen ?: return@withPermissions
        if (SpikeState.state.value.running) SpikeService.stop(this) else SpikeService.connect(this, radio.address)
    }

    private fun openSettingsPage(action: String, withPackage: Boolean) {
        try {
            val intent = Intent(action)
            if (withPackage) intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            notice = "This watch has no page for that"
        }
    }

    @Composable
    private fun App(start: String) {
        val nav = rememberSwipeDismissableNavController()
        val radios by Radios.state.collectAsState()
        val link by SpikeState.state.collectAsStateWithLifecycle()
        val sync by SyncStatus.state.collectAsStateWithLifecycle()
        val dao = remember { Storage.dao(this) }
        val now = rememberNow()
        val goHome = { nav.navigate(HOME) { popUpTo(nav.graph.id) { inclusive = true } } }

        SwipeDismissableNavHost(navController = nav, startDestination = start) {
            composable(WELCOME) { WelcomeScreen(onFind = { nav.navigate(FIND) }) }

            composable(HOME) {
                val channels by remember { dao.channelsFlow() }.collectAsStateWithLifecycle(emptyList())
                val lasts by remember { dao.lastMessagesFlow() }.collectAsStateWithLifecycle(emptyList<ConversationLast>())
                HomeScreen(
                    radioName = radios?.chosen?.name,
                    status = HomeRows.status(
                        liveLink = link.link.takeIf { link.running },
                        syncing = sync.syncing,
                        problem = sync.problem?.let { SyncStatus.explain(it) },
                        lastSyncAt = sync.lastSyncAt,
                        now = now,
                    ),
                    rows = HomeRows.build(channels, lasts),
                    now = now,
                    checkNowEnabled = !link.running && !sync.syncing,
                    onCheckNow = { withPermissions { CheckIn.now(this@MainActivity) } },
                    onOpen = { row -> nav.navigate("$CONVERSATION/${Uri.encode(row.conversation)}/${Uri.encode(row.title)}") },
                    onRadios = { nav.navigate(RADIOS) },
                    onSettings = { nav.navigate(SETTINGS) },
                )
            }

            composable("$CONVERSATION/{key}/{title}") { entry ->
                val key = entry.arguments?.getString("key").orEmpty()
                val title = entry.arguments?.getString("title").orEmpty()
                val messages by remember(key) { dao.messagesFlow(key) }.collectAsStateWithLifecycle(emptyList())
                ConversationScreen(
                    title = title,
                    messages = messages,
                    selfName = remember { Prefs.of(this@MainActivity).getString(Prefs.SELF_NAME, null) },
                    now = now,
                    onSeen = { Storage.later { dao.markRead(key) } },
                )
            }

            composable(RADIOS) {
                RadiosScreen(
                    known = radios?.known.orEmpty(),
                    chosen = radios?.chosen?.address,
                    onOpen = { radio -> nav.navigate("$RADIO/${Uri.encode(radio.address)}") },
                    onAdd = { nav.navigate(FIND) },
                )
            }

            composable("$RADIO/{address}") { entry ->
                val address = entry.arguments?.getString("address").orEmpty()
                val radio = radios?.known?.firstOrNull { it.address == address }
                if (radio == null) {
                    LaunchedEffect(Unit) { nav.popBackStack() }
                } else {
                    RadioScreen(
                        radio = radio,
                        inUse = radios?.chosen?.address == address,
                        onUse = {
                            // With no radio in use there are no messages to lose, so no need to ask.
                            if (radios?.chosen == null) {
                                use(radio)
                                goHome()
                            } else {
                                nav.navigate("$CONFIRM_USE/${Uri.encode(address)}")
                            }
                        },
                        onForget = { nav.navigate("$CONFIRM_FORGET/${Uri.encode(address)}") },
                    )
                }
            }

            composable(FIND) {
                LaunchedEffect(Unit) { scan() }
                FindRadioScreen(
                    scanning = scanning,
                    found = found,
                    notice = notice,
                    onScan = { scan() },
                    onPick = { picked ->
                        val radio = KnownRadio(picked.address, picked.name)
                        val current = radios?.chosen
                        when {
                            // Nothing stored yet to lose, or the same radio again.
                            current == null || current.address == radio.address -> {
                                use(radio)
                                goHome()
                            }
                            else -> {
                                Radios.remember(this@MainActivity, radio)
                                nav.navigate("$CONFIRM_USE/${Uri.encode(radio.address)}")
                            }
                        }
                    },
                )
            }

            composable("$CONFIRM_USE/{address}") { entry ->
                val address = entry.arguments?.getString("address").orEmpty()
                val radio = radios?.known?.firstOrNull { it.address == address }
                ConfirmScreen(
                    title = "Use ${radio?.name ?: address}?",
                    explanation = "The watch keeps one radio's messages. Switching deletes the ones stored now; " +
                        "the new radio's waiting messages come in at the next sync.",
                    action = "Use this radio",
                    onConfirm = {
                        if (radio != null) use(radio)
                        goHome()
                    },
                    onCancel = { nav.popBackStack() },
                )
            }

            composable("$CONFIRM_FORGET/{address}") { entry ->
                val address = entry.arguments?.getString("address").orEmpty()
                val radio = radios?.known?.firstOrNull { it.address == address }
                val inUse = radios?.chosen?.address == address
                ConfirmScreen(
                    title = "Forget ${radio?.name ?: address}?",
                    explanation = if (inUse) "Its stored messages are deleted, and the watch stops syncing with it." else "It is removed from the list.",
                    action = "Forget",
                    onConfirm = {
                        forget(address)
                        nav.navigate(FORGOTTEN) { popUpTo(RADIOS) }
                    },
                    onCancel = { nav.popBackStack() },
                )
            }

            composable(FORGOTTEN) {
                ForgottenScreen(
                    messagesDeleted = forgotInUse,
                    onBluetoothSettings = { openSettingsPage(Settings.ACTION_BLUETOOTH_SETTINGS, withPackage = false) },
                    onDone = {
                        // The radio in use was forgotten (its removal may still be waiting for Live to stop).
                        if (forgotInUse || Radios.state.value?.chosen == null) {
                            nav.navigate(WELCOME) { popUpTo(nav.graph.id) { inclusive = true } }
                        } else {
                            nav.popBackStack()
                        }
                    },
                )
            }

            composable(SETTINGS) {
                SettingsScreen(
                    state = SettingsState(
                        checkInOn = checkInOn,
                        intervalMinutes = intervalMinutes,
                        alarmsAllowed = exactAlarmsAllowed,
                        liveRunning = link.running,
                        hasRadio = radios?.chosen != null,
                        syncing = sync.syncing,
                    ),
                    onCheckIn = { toggleCheckIn() },
                    onInterval = { intervalMinutes = CheckIn.nextInterval(this@MainActivity) },
                    onAllowAlarms = { openSettingsPage(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, withPackage = true) },
                    onLive = { toggleLive() },
                    onReadEverything = {
                        withPermissions { CheckIn.now(this@MainActivity, full = true) }
                        goHome()
                    },
                    onDiagnostics = { nav.navigate(DIAGNOSTICS) },
                )
            }

            composable(DIAGNOSTICS) {
                val counts by Storage.counts.collectAsStateWithLifecycle()
                val lines by SpikeLog.lines.collectAsStateWithLifecycle()
                DiagnosticsScreen(link = link, counts = counts, lines = lines, onClearLog = { SpikeLog.clear() })
            }
        }
    }

    /** The current time, moved on every 30 s so "4 min" ages while a screen is open. */
    @Composable
    private fun rememberNow(): Long {
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(NOW_TICK_MS)
                now = System.currentTimeMillis()
            }
        }
        return now
    }

    private companion object {
        const val WELCOME = "welcome"
        const val HOME = "home"
        const val CONVERSATION = "conversation"
        const val RADIOS = "radios"
        const val RADIO = "radio"
        const val FIND = "find"
        const val CONFIRM_USE = "confirm-use"
        const val CONFIRM_FORGET = "confirm-forget"
        const val FORGOTTEN = "forgotten"
        const val SETTINGS = "settings"
        const val DIAGNOSTICS = "diagnostics"

        /** No sync on opening the app if the last one finished this recently. */
        const val OPEN_SYNC_GAP_MS = 2 * 60_000L
        const val NOW_TICK_MS = 30_000L
        const val LIVE_STOP_WAIT_MS = 10_000L
    }
}
