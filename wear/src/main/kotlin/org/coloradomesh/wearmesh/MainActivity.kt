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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.coloradomesh.meshcore.ble.FoundRadio
import org.coloradomesh.meshcore.ble.RadioScanner
import org.coloradomesh.wearmesh.data.ConversationLast
import org.coloradomesh.wearmesh.data.Storage
import org.coloradomesh.wearmesh.notify.Notifier
import org.coloradomesh.wearmesh.notify.NotifyRules
import org.coloradomesh.wearmesh.ui.ConfirmScreen
import org.coloradomesh.wearmesh.ui.ConversationScreen
import org.coloradomesh.wearmesh.ui.DiagnosticsScreen
import org.coloradomesh.wearmesh.ui.FindRadioScreen
import org.coloradomesh.wearmesh.ui.ForgottenScreen
import org.coloradomesh.wearmesh.ui.HomeRows
import org.coloradomesh.wearmesh.ui.HomeScreen
import org.coloradomesh.wearmesh.ui.MessageScreen
import org.coloradomesh.wearmesh.ui.NewMessage
import org.coloradomesh.wearmesh.ui.NewMessageScreen
import org.coloradomesh.wearmesh.ui.NotificationsScreen
import org.coloradomesh.wearmesh.ui.NotifyRow
import org.coloradomesh.wearmesh.ui.QuickReplies
import org.coloradomesh.wearmesh.ui.QuickRepliesScreen
import org.coloradomesh.wearmesh.ui.QuickReplyScreen
import org.coloradomesh.wearmesh.ui.RadioScreen
import org.coloradomesh.wearmesh.ui.RadiosScreen
import org.coloradomesh.wearmesh.ui.ReplyScreen
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
    private var mode by mutableStateOf(Mode.Kind.LIVE)
    private var intervalMinutes by mutableStateOf(15)
    private var exactAlarmsAllowed by mutableStateOf(true)
    private var notificationsAllowed by mutableStateOf(true)
    private var offerAnswered by mutableStateOf(false)

    /** A conversation to open, from a tapped notification: radio, conversation, title. */
    private var toOpen by mutableStateOf<OpenRequest?>(null)

    /** Whether this activity has already taken a notification's conversation from its intent. */
    private var openedFromNotification = false

    /** Whether the last radio forgotten was the one in use, which leaves the watch on Welcome. */
    private var forgotInUse by mutableStateOf(false)

    /** The quick replies, as saved (M3 step 2). */
    private var quickReplies by mutableStateOf(QuickReplies.DEFAULTS)

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
        mode = Mode.get(this)
        intervalMinutes = CheckIn.intervalMinutes(this)
        offerAnswered = Mode.promptDone(this)
        Radios.load(this)
        SyncStatus.load(this)
        Notifier.createChannels(this)
        Notifier.loadChoices(this, Radios.state.value?.chosen?.address)
        quickReplies = QuickReplies.parse(Prefs.of(this).getString(Prefs.QUICK_REPLIES, null))
        // A notification's conversation opens once: not again when the activity is recreated
        // after opening it, nor when it is relaunched from Recents.
        if (savedInstanceState?.getBoolean(OPENED_FROM_NOTIFICATION) != true) openFrom(intent)
        val start = if (Radios.state.value?.chosen != null) HOME else WELCOME
        setContent { MaterialTheme { App(start) } }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(OPENED_FROM_NOTIFICATION, openedFromNotification)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openFrom(intent)
    }

    /** A tapped notification names the conversation to open. */
    private fun openFrom(intent: Intent?) {
        if (intent == null || (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) return
        val radio = intent.getStringExtra(Notifier.EXTRA_RADIO) ?: return
        val conversation = intent.getStringExtra(Notifier.EXTRA_CONVERSATION) ?: return
        val quick = intent.getBooleanExtra(Notifier.EXTRA_QUICK_REPLY, false)
        toOpen = OpenRequest(radio, conversation, intent.getStringExtra(Notifier.EXTRA_TITLE) ?: conversation, quick)
        openedFromNotification = true
        // Quick reply comes from a notification the app does not take down by opening the
        // conversation underneath, since the reply screen is on top of it.
        if (quick) Notifier.dismiss(this, radio, conversation)
        intent.removeExtra(Notifier.EXTRA_RADIO)
        intent.removeExtra(Notifier.EXTRA_CONVERSATION)
        intent.removeExtra(Notifier.EXTRA_TITLE)
        intent.removeExtra(Notifier.EXTRA_QUICK_REPLY)
    }

    override fun onStart() {
        super.onStart()
        visible = true
        // Opening the app after Quit carries on in the mode in use before.
        Mode.resumeAfterQuit(this)
        syncOnOpen()
    }

    override fun onResume() {
        super.onResume()
        // The user may have just allowed "Alarms & reminders" in Settings.
        exactAlarmsAllowed = CheckIn.exactAlarmsAllowed(this)
        notificationsAllowed = Notifier.enabled(this)
        Storage.refreshCounts(this)
    }

    override fun onStop() {
        visible = false
        scanner.stop()
        scanning = false
        super.onStop()
    }

    /**
     * Opening the app syncs (decided for M2). In Live mode that means holding the link, if it is
     * not held already (after a restart, say). In Check-in mode it is a brief sync, unless a sync is
     * running or one ended, worked or not, in the last [OPEN_SYNC_GAP_MS]: a watch comes on screen
     * at every glance, and a radio that is not answering should not be tried at each one.
     */
    private fun syncOnOpen() {
        val radio = Radios.state.value?.chosen ?: return
        if (SpikeState.state.value.running || !hasBluetoothPermission()) return
        if (Mode.get(this) == Mode.Kind.LIVE) {
            // A check-in still holding the radio would make Live's first connection fail.
            afterSyncEnds(
                action = {
                    try {
                        if (onScreen() && !SpikeState.state.value.running) SpikeService.connect(this, radio.address)
                    } catch (e: IllegalStateException) {
                        SpikeLog.log("Live (app opened): could not start (${e.message})")
                    }
                },
                onTimeout = { SpikeLog.log("Live (app opened): a check-in is still running; Live starts when the app is next opened") },
            )
            return
        }
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
            if (Mode.get(this) == Mode.Kind.LIVE) SpikeService.connect(this, radio.address) else CheckIn.now(this)
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

    private fun switchMode(kind: Mode.Kind) = withPermissions {
        mode = kind
        // Live waits for a check-in under way to let go of the radio. By then the user may have
        // chosen again, or left the app: the latest choice wins, and Live's link is only started
        // with the app on screen (otherwise it starts when the app is next opened).
        val apply = {
            if (mode == kind) {
                Mode.set(this, kind, startLive = onScreen())
                exactAlarmsAllowed = CheckIn.exactAlarmsAllowed(this)
            }
        }
        if (kind == Mode.Kind.LIVE) {
            afterSyncEnds(
                action = apply,
                onTimeout = {
                    // Never two links to one radio: record Live, start it at the next opening.
                    if (mode == kind) Mode.set(this, kind, startLive = false)
                    SpikeLog.log("Mode: Live; a check-in is still running, so the link starts when the app is next opened")
                },
            )
        } else {
            apply()
        }
    }

    /**
     * Set in [onStart] itself, not read from the lifecycle: the lifecycle only reports STARTED once
     * onStart has returned, so the sync on opening, which runs in onStart, took the app for off
     * screen and never started Live.
     */
    private var visible = false

    private fun onScreen(): Boolean = visible

    /**
     * Runs [action] now, or once the check-in under way ends. If it has not ended within
     * [SYNC_END_WAIT_MS], runs [onTimeout] instead, so the two never hold the radio at once.
     */
    private fun afterSyncEnds(action: () -> Unit, onTimeout: () -> Unit) {
        if (!SyncStatus.state.value.syncing) {
            action()
            return
        }
        lifecycleScope.launch {
            val ended = withTimeoutOrNull(SYNC_END_WAIT_MS) { SyncStatus.state.first { !it.syncing } }
            if (ended != null) action() else onTimeout()
        }
    }

    /** Quit: nothing runs until the app is opened again; the screen closes and leaves Recents. */
    private fun quit() {
        scanner.stop()
        scanning = false
        Mode.quit(this)
        finishAndRemoveTask()
    }

    private fun saveQuickReplies(replies: List<String>) {
        quickReplies = replies
        Prefs.of(this).edit().putString(Prefs.QUICK_REPLIES, QuickReplies.format(replies)).apply()
    }

    private fun replyRoute(conversation: String, title: String, to: String?, quick: Boolean = false): String {
        val base = "$REPLY/${Uri.encode(conversation)}/${Uri.encode(title)}"
        val query = listOfNotNull(to?.let { "to=${Uri.encode(it)}" }, "quick=true".takeIf { quick })
        return if (query.isEmpty()) base else "$base?${query.joinToString("&")}"
    }

    /** A conversation a tapped notification asks to open, and whether at its quick replies. */
    private data class OpenRequest(val radio: String, val conversation: String, val title: String, val quick: Boolean)

    private fun answerOffer(switch: Boolean) {
        Mode.markPromptDone(this)
        offerAnswered = true
        if (switch) switchMode(Mode.Kind.CHECK_IN)
    }

    private fun openNotificationSettings() {
        try {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        } catch (e: ActivityNotFoundException) {
            notice = "This watch has no page for that"
        }
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
        // Every stored query is for the radio in use; another radio's history waits until it is chosen again.
        val radioInUse = radios?.chosen?.address.orEmpty()
        val choices by Notifier.choices.collectAsStateWithLifecycle()
        val openConversation = { conversation: String, title: String ->
            nav.navigate("$CONVERSATION/${Uri.encode(conversation)}/${Uri.encode(title)}")
        }

        // A tapped notification opens its conversation, if it is for the radio in use.
        LaunchedEffect(toOpen) {
            val target = toOpen ?: return@LaunchedEffect
            toOpen = null
            if (target.radio == radioInUse) {
                nav.navigate(HOME) { popUpTo(nav.graph.id) { inclusive = true } }
                openConversation(target.conversation, target.title)
                // Quick reply: the quick replies straight away, over the conversation, so sending
                // one lands on the conversation.
                if (target.quick) nav.navigate(replyRoute(target.conversation, target.title, null, quick = true))
            }
        }

        SwipeDismissableNavHost(navController = nav, startDestination = start) {
            composable(WELCOME) { WelcomeScreen(onFind = { nav.navigate(FIND) }) }

            composable(HOME) {
                val channels by remember(radioInUse) { dao.channelsFlow(radioInUse) }.collectAsStateWithLifecycle(emptyList())
                val lasts by remember(radioInUse) { dao.lastMessagesFlow(radioInUse) }.collectAsStateWithLifecycle(emptyList<ConversationLast>())
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
                    // Once, after Live has first connected.
                    offerCheckIn = mode == Mode.Kind.LIVE && !offerAnswered && link.running && link.link == "Connected",
                    onCheckNow = { withPermissions { CheckIn.now(this@MainActivity) } },
                    onNewMessage = { nav.navigate(NEW_MESSAGE) },
                    onOfferAnswered = { switch -> answerOffer(switch) },
                    onOpen = { row -> openConversation(row.conversation, row.title) },
                    onRadios = { nav.navigate(RADIOS) },
                    onSettings = { nav.navigate(SETTINGS) },
                )
            }

            composable("$CONVERSATION/{key}/{title}") { entry ->
                val key = entry.arguments?.getString("key").orEmpty()
                val title = entry.arguments?.getString("title").orEmpty()
                val messages by remember(radioInUse, key) { dao.messagesFlow(radioInUse, key) }.collectAsStateWithLifecycle(emptyList())
                val channels by remember(radioInUse) { dao.channelsFlow(radioInUse) }.collectAsStateWithLifecycle(emptyList())
                val kind = NotifyRules.kindOf(key, channels.firstOrNull { "c:${it.slot}" == key }?.kind)
                // The contact, for a direct conversation, wrapped in a list so "not read yet" (empty)
                // differs from "no such contact" (a null in it): no warning flashes up while it loads.
                val contact by remember(radioInUse, key) {
                    dao.contactFlow(radioInUse, key.removePrefix("d:")).map { listOf(it) }
                }.collectAsStateWithLifecycle(emptyList())
                // While this conversation is on screen it does not notify, and its notification goes.
                LifecycleResumeEffect(radioInUse, key) {
                    Notifier.onScreen(this@MainActivity, radioInUse, key)
                    onPauseOrDispose { Notifier.onScreen(this@MainActivity, null, null) }
                }
                ConversationScreen(
                    title = title,
                    messages = messages,
                    selfName = remember { Prefs.of(this@MainActivity).getString(Prefs.SELF_NAME, null) },
                    now = now,
                    notifyOn = NotifyRules.isOn(key, kind, choices),
                    favouriteWarning = if (contact.isEmpty()) null else NewMessage.favouriteWarning(key, contact.first()),
                    onSeen = { Storage.later { dao.markRead(radioInUse, key) } },
                    onToggleNotify = {
                        Notifier.setChoice(this@MainActivity, radioInUse, key, kind, !NotifyRules.isOn(key, kind, choices))
                    },
                    onReply = { nav.navigate(replyRoute(key, title, null)) },
                    onOpenMessage = { message -> nav.navigate("$MESSAGE/${message.id}/${Uri.encode(title)}") },
                )
            }

            composable(
                "$REPLY/{key}/{title}?to={to}&quick={quick}",
                arguments = listOf(
                    navArgument("to") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("quick") { type = NavType.BoolType; defaultValue = false },
                ),
            ) { entry ->
                val key = entry.arguments?.getString("key").orEmpty()
                ReplyScreen(
                    conversation = key,
                    title = entry.arguments?.getString("title").orEmpty(),
                    to = entry.arguments?.getString("to"),
                    // From a notification's Quick reply: the quick replies, without dictation first.
                    startWithDictation = entry.arguments?.getBoolean("quick") != true,
                    selfName = remember { Prefs.of(this@MainActivity).getString(Prefs.SELF_NAME, null) },
                    quickReplies = quickReplies,
                    // The screen closes once the message is stored to send; if Bluetooth permission is
                    // refused, it stays, with the text, so nothing typed is lost.
                    onSend = { body ->
                        withPermissions {
                            Outbox.send(this@MainActivity, key, body)
                            nav.popBackStack()
                        }
                    },
                )
            }

            composable("$MESSAGE/{id}/{title}") { entry ->
                val id = entry.arguments?.getString("id")?.toLongOrNull() ?: -1
                val title = entry.arguments?.getString("title").orEmpty()
                val message by remember(radioInUse, id) { dao.messageFlow(radioInUse, id) }.collectAsStateWithLifecycle(null)
                val shown = message
                if (shown != null) {
                    MessageScreen(
                        message = shown,
                        now = now,
                        onReply = { to ->
                            nav.popBackStack()
                            nav.navigate(replyRoute(shown.conversation, title, to))
                        },
                        onRetry = {
                            withPermissions {
                                Outbox.retry(this@MainActivity, shown.id)
                                nav.popBackStack()
                            }
                        },
                    )
                }
            }

            composable(NEW_MESSAGE) {
                val favourites by remember(radioInUse) { dao.favouriteCompanionsFlow(radioInUse) }.collectAsStateWithLifecycle(emptyList())
                val recent by remember(radioInUse) { dao.recentCompanionsFlow(radioInUse, NewMessage.RECENT) }
                    .collectAsStateWithLifecycle(emptyList())
                val channels by remember(radioInUse) { dao.channelsFlow(radioInUse) }.collectAsStateWithLifecycle(emptyList())
                NewMessageScreen(picks = NewMessage.build(favourites, recent, channels, now)) { pick ->
                    // Its conversation, empty or not, with Reply under any history there is.
                    nav.popBackStack()
                    openConversation(pick.conversation, pick.title)
                }
            }

            composable(QUICK_REPLIES) {
                QuickRepliesScreen(
                    replies = quickReplies,
                    onOpen = { index -> nav.navigate("$QUICK_REPLY/$index") },
                    onAdd = { text -> saveQuickReplies(QuickReplies.add(quickReplies, text)) },
                    onReset = { saveQuickReplies(QuickReplies.DEFAULTS) },
                )
            }

            composable("$QUICK_REPLY/{index}") { entry ->
                val index = entry.arguments?.getString("index")?.toIntOrNull() ?: -1
                val reply = quickReplies.getOrNull(index)
                if (reply == null) {
                    LaunchedEffect(Unit) { nav.popBackStack() }
                } else {
                    QuickReplyScreen(
                        reply = reply,
                        // Back to the list: a change that repeats another reply removes one, which
                        // moves the replies after it up, so this index may now be another reply.
                        onChange = { text ->
                            saveQuickReplies(QuickReplies.change(quickReplies, index, text))
                            nav.popBackStack()
                        },
                        onRemove = {
                            saveQuickReplies(QuickReplies.remove(quickReplies, index))
                            nav.popBackStack()
                        },
                    )
                }
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
                        // Each radio keeps its own history, so switching needs no warning.
                        onUse = {
                            use(radio)
                            goHome()
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
                        use(KnownRadio(picked.address, picked.name))
                        goHome()
                    },
                )
            }

            composable("$CONFIRM_FORGET/{address}") { entry ->
                val address = entry.arguments?.getString("address").orEmpty()
                val radio = radios?.known?.firstOrNull { it.address == address }
                val inUse = radios?.chosen?.address == address
                ConfirmScreen(
                    title = "Forget ${radio?.name ?: address}?",
                    explanation = if (inUse) {
                        "Its stored messages are deleted, and the watch stops syncing with it."
                    } else {
                        "Its stored messages are deleted, and it is removed from the list."
                    },
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
                        live = mode == Mode.Kind.LIVE,
                        intervalMinutes = intervalMinutes,
                        alarmsAllowed = exactAlarmsAllowed,
                        liveRunning = link.running,
                        hasRadio = radios?.chosen != null,
                        syncing = sync.syncing,
                        notificationsAllowed = notificationsAllowed,
                    ),
                    onMode = {
                        switchMode(if (mode == Mode.Kind.LIVE) Mode.Kind.CHECK_IN else Mode.Kind.LIVE)
                        // Choosing a mode answers the first-run offer too.
                        Mode.markPromptDone(this@MainActivity)
                        offerAnswered = true
                    },
                    onInterval = { intervalMinutes = CheckIn.nextInterval(this@MainActivity) },
                    onAllowAlarms = { openSettingsPage(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, withPackage = true) },
                    onNotifications = { nav.navigate(NOTIFICATIONS) },
                    onQuickReplies = { nav.navigate(QUICK_REPLIES) },
                    onAllowNotifications = { openNotificationSettings() },
                    onReadEverything = {
                        withPermissions { CheckIn.now(this@MainActivity, full = true) }
                        goHome()
                    },
                    onDiagnostics = { nav.navigate(DIAGNOSTICS) },
                    onQuit = { quit() },
                )
            }

            composable(NOTIFICATIONS) {
                val channels by remember(radioInUse) { dao.channelsFlow(radioInUse) }.collectAsStateWithLifecycle(emptyList())
                val lasts by remember(radioInUse) { dao.lastMessagesFlow(radioInUse) }.collectAsStateWithLifecycle(emptyList<ConversationLast>())
                val rows = HomeRows.build(channels, lasts).map { row ->
                    val kind = NotifyRules.kindOf(row.conversation, channels.firstOrNull { "c:${it.slot}" == row.conversation }?.kind)
                    NotifyRow(row.conversation, row.title, kind, NotifyRules.isOn(row.conversation, kind, choices))
                }
                NotificationsScreen(rows = rows) { row ->
                    Notifier.setChoice(this@MainActivity, radioInUse, row.conversation, row.kind, !row.on)
                }
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
        const val CONFIRM_FORGET = "confirm-forget"
        const val FORGOTTEN = "forgotten"
        const val SETTINGS = "settings"
        const val DIAGNOSTICS = "diagnostics"
        const val NOTIFICATIONS = "notifications"
        const val REPLY = "reply"
        const val MESSAGE = "message"
        const val QUICK_REPLIES = "quick-replies"
        const val QUICK_REPLY = "quick-reply"
        const val NEW_MESSAGE = "new-message"

        /** No sync on opening the app if the last one finished this recently. */
        const val OPEN_SYNC_GAP_MS = 2 * 60_000L
        const val NOW_TICK_MS = 30_000L
        const val LIVE_STOP_WAIT_MS = 10_000L

        /** Longer than a check-in's own limit, so Live never starts while one holds the radio. */
        const val SYNC_END_WAIT_MS = 120_000L
        const val OPENED_FROM_NOTIFICATION = "opened_from_notification"
    }
}
