package org.coloradomesh.wearmesh

import android.Manifest
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.coloradomesh.meshcore.ble.RadioSession
import org.coloradomesh.meshcore.protocol.CompanionClient
import org.coloradomesh.meshcore.protocol.DirectOutcome
import org.coloradomesh.meshcore.protocol.SelfInfo
import org.coloradomesh.wearmesh.data.Delays
import org.coloradomesh.wearmesh.data.IncomingMessage
import org.coloradomesh.wearmesh.data.OutboxSender
import org.coloradomesh.wearmesh.data.Storage
import org.coloradomesh.wearmesh.data.SyncPlan
import org.coloradomesh.wearmesh.data.SyncRecorder
import org.coloradomesh.wearmesh.glance.Glances
import org.coloradomesh.wearmesh.notify.Notifier
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * One Check-in: connect to the radio, collect what it has queued into the message store,
 * disconnect.
 *
 * Most check-ins are brief: contacts changed since the last one, no channel slots (see
 * [SyncPlan]). "Read channels and contacts again", a radio the store does not know yet, and the
 * first check-in an hour or more after the last full read read everything. A check-in tries to connect [CONNECT_TRIES] times, one more than Live,
 * since a failed one waits a whole interval for the next.
 *
 * Started by [CheckInService] (from the exact alarm [CheckIn] sets, or from "Check now"), or, if the
 * system will not start that service, inside the alarm itself by [CheckInAlarmReceiver]. Each run
 * logs when it started and why, the screen and Doze state, the app's standby bucket and
 * battery-optimisation state, how long the link took, how many messages came, and the watch
 * battery.
 *
 * Each run holds a partial wake lock. The first overnight test showed that a running job is not
 * guaranteed to keep the CPU awake: in deep Doze the link's 12 s connection limit took 30 s and a
 * 1.5 s pause took 6 minutes, because those timers stop while the CPU sleeps. It always
 * disconnects before returning, even when the run is stopped part-way, so the radio is free for
 * the next client.
 *
 * Once the queue is collected, it sends what is waiting in the outbox (M3) and stays connected
 * until each message has ended, up to [SEND_LIMIT_MS]: a direct message is waited on through all
 * 3 tries (decided for M3). It also checks the radio's clock at start-up and sets it when more
 * than [RadioClock.TOLERANCE_SECONDS] off (decided for M3).
 */
object CheckInRun {
    /** For runs that outlive the caller, such as one inside an alarm broadcast. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Long enough for 4 connection tries, pairing checks and reading a few hundred contacts. */
    const val TIME_LIMIT_MS = 90_000L

    /**
     * How long to stay connected for the outbox once the queue is collected: 3 tries of a direct
     * message, each waited on for up to the shared code's 62 s, and some margin.
     */
    const val SEND_LIMIT_MS = 200_000L
    private const val CLOSE_WAIT_MS = 5_000L

    /** Night 2 of step 1 saw one check-in need all 3 tries after Bluetooth status 133. */
    private const val CONNECT_TRIES = 4

    /** How long to wait for the store to take what the check-in collected. */
    private const val STORE_WAIT_MS = 10_000L

    /** The wake lock outlasts the time limit by this much, then lets go whatever happens. */
    private const val WAKE_MARGIN_MS = 15_000L

    /** One check-in at a time in this process, however it was started. */
    private val running = AtomicBoolean(false)

    /**
     * [fullRead] asks for every channel slot and contact, as "Read channels and contacts again"
     * does. [sendLimitMs] 0 sends nothing, for a run that must stay short (inside an alarm).
     */
    suspend fun run(
        context: Context,
        why: String,
        timeLimitMs: Long = TIME_LIMIT_MS,
        fullRead: Boolean = false,
        sendLimitMs: Long = SEND_LIMIT_MS,
    ) {
        val app = context.applicationContext
        SpikeLog.init(app)
        if (!running.compareAndSet(false, true)) {
            SpikeLog.log("Check-in ($why): skipped, another is still running")
            return
        }
        val wake = app.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WearMesh:check-in")
            .apply {
                setReferenceCounted(false)
                acquire(timeLimitMs + sendLimitMs + CLOSE_WAIT_MS + 2 * STORE_WAIT_MS + WAKE_MARGIN_MS)
            }
        try {
            checkIn(app, why, timeLimitMs, fullRead, sendLimitMs)
        } catch (e: CancellationException) {
            SpikeLog.log("Check-in ($why): stopped before it finished")
            throw e
        } finally {
            if (wake.isHeld) wake.release()
            running.set(false)
        }
    }

    private suspend fun checkIn(app: Context, why: String, timeLimitMs: Long, fullRead: Boolean, sendLimitMs: Long) {
        val address = Prefs.of(app).getString(Prefs.RADIO_ADDRESS, null)
        if (address == null) {
            SpikeLog.log("Check-in ($why): skipped, no radio chosen yet")
            return
        }
        if (SpikeState.state.value.running) {
            SpikeLog.log("Check-in ($why): skipped, the Live link is up")
            return
        }
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            SpikeLog.log("Check-in ($why): skipped, Bluetooth permission not granted")
            return
        }
        val started = SystemClock.elapsedRealtime()
        SpikeLog.log("Check-in ($why): starting; ${conditions(app)}")
        SyncStatus.started()
        var problem: String? = "stopped before it finished"
        try {
            problem = connectAndCollect(app, address, fullRead, started, timeLimitMs, sendLimitMs)
        } finally {
            SyncStatus.finished(app, problem)
            // After the sync time is saved, so "Synced … ago" counts from this one.
            Glances.refresh(app)
        }
    }

    /** Returns why the check-in failed, or null when it collected the radio's queue. */
    private suspend fun connectAndCollect(
        app: Context,
        address: String,
        fullRead: Boolean,
        started: Long,
        timeLimitMs: Long,
        sendLimitMs: Long,
    ): String? {
        val store = Storage.store(app, address)
        // The first sync after choosing a radio brings its backlog, which does not notify (decided
        // for step 4); once that sync has collected the queue, later messages do.
        val prefs = Prefs.of(app)
        val backlog = prefs.getString(Prefs.QUIET_SYNC_RADIO, null) == address
        // A channel message waiting to send names only its slot, so a connection that sends one
        // reads the channel list first: a slot given another channel meanwhile loses its waiting
        // messages (see MessageStore.saveChannels) instead of sending them to the wrong channel.
        var channelsWaiting = false
        val plan = try {
            withContext(Dispatchers.IO) {
                Storage.call {
                    store.open()
                    channelsWaiting = sendLimitMs > 0 && store.pendingOutgoing().any { it.conversation.startsWith("c:") }
                    SyncPlan.decide(store.radio(), address, System.currentTimeMillis(), fullRead)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SpikeLog.log("Check-in: could not read the store (${e.cause?.message ?: e.message}); reading everything")
            SyncPlan(fullRead = true, contactsSince = 0)
        }

        val outcome = CompletableDeferred<Outcome>()
        val closed = CompletableDeferred<String>()
        val linkUpAt = AtomicLong(0)

        // The outbox goes over this connection once the queue is collected.
        val link = AtomicReference<RadioSession?>(null)
        val sender = OutboxSender(
            store,
            Storage.writer,
            object : OutboxSender.Port {
                override fun sendChannelText(channelIndex: Int, text: String) {
                    link.get()?.sendChannelText(channelIndex, text)
                }

                override fun sendDirectText(publicKeyHex: String, text: String): Long? = link.get()?.sendDirectText(publicKeyHex, text)
            },
            { SpikeLog.log("Check-in: $it") },
            onFailed = { message, status, why -> Notifier.failed(app, address, message, status, why) },
        )
        val sendsDone = CompletableDeferred<Unit>()

        // Called on the link's threads with the protocol client locked: nothing here may block.
        val listener = object : CompanionClient.Listener {
            override fun onLog(message: String) = SpikeLog.log("Check-in radio: $message")

            // Saved without waiting; mentions of this name stand out in conversations.
            override fun onSelf(info: SelfInfo) = Prefs.of(app).edit().putString(Prefs.SELF_NAME, info.name).apply()

            override fun onRadioClock(radioSeconds: Long, clientSeconds: Long, setting: Boolean) {
                RadioClock.describe(radioSeconds, clientSeconds, setting)?.let { SpikeLog.log("Check-in: $it") }
            }

            override fun onQueueEmpty() {
                if (outcome.isCompleted) return
                if (sendLimitMs > 0) sender.sendPending { sendsDone.complete(Unit) } else sendsDone.complete(Unit)
                outcome.complete(Outcome.Drained)
            }

            override fun onSendResult(channelIndex: Int, text: String, accepted: Boolean) =
                sender.onSendResult(channelIndex, text, accepted)

            override fun onDirectRefused(id: Long, errorCode: Int?) = sender.onDirectRefused(id, errorCode)

            override fun onDirectResult(id: Long, publicKeyHex: String, text: String, outcome: DirectOutcome) =
                sender.onDirectResult(id, outcome)
        }
        // Collected on the storage thread, notified about once the check-in is over.
        val newlyStored = Collections.synchronizedList(ArrayList<IncomingMessage>())
        val recorder = SyncRecorder(
            store,
            plan,
            Storage.writer,
            log = { SpikeLog.log("Check-in: $it") },
            also = listener,
            onStored = { newlyStored.add(it) },
        )
        val session = RadioSession(
            context = app,
            address = address,
            listener = recorder,
            appName = SpikeService.APP_NAME,
            onLog = { SpikeLog.log("Check-in link: $it") },
            onLinkReady = { linkUpAt.set(SystemClock.elapsedRealtime()) },
            connectTries = CONNECT_TRIES,
            startUp = plan.startUp.copy(
                readChannels = plan.startUp.readChannels || channelsWaiting,
                clockTolerance = RadioClock.TOLERANCE_SECONDS,
            ),
            onClosed = { reason ->
                closed.complete(reason)
                outcome.complete(Outcome.Ended(reason))
                // Ends any wait for the outbox, and records what it had handed over.
                sender.finish()
            },
        )
        link.set(session)
        val result = try {
            session.open()
            val collected = withTimeoutOrNull(timeLimitMs) { outcome.await() }
                ?: Outcome.Ended("not finished within ${timeLimitMs / 1000} s")
            if (collected == Outcome.Drained && sendLimitMs > 0) {
                if (withTimeoutOrNull(sendLimitMs) { sendsDone.await() } == null) {
                    SpikeLog.log("Check-in: messages still sending after ${sendLimitMs / 1000} s; disconnecting")
                }
            }
            collected
        } finally {
            // Also when the system stops the job: a link left open would keep the radio busy,
            // locking out the Live link and the next check-in.
            session.close()
            withContext(NonCancellable) {
                if (withTimeoutOrNull(CLOSE_WAIT_MS) { closed.await() } == null) {
                    SpikeLog.log("Check-in: the link did not report closing within ${CLOSE_WAIT_MS / 1000} s")
                }
            }
            sender.finish()
            // Hand the store any contacts still gathered. Anything the radio sends after this, if
            // the link was slow to close, is logged and not stored.
            recorder.finish()
            // Queued behind every write still pending, so the list holds every message stored,
            // even when the check-in was stopped part-way or the store is slow.
            Storage.later {
                val toNotify = synchronized(newlyStored) { newlyStored.toList() }
                Notifier.afterSync(app, address, toNotify, backlog)
            }
        }

        // The link is closed, so no more writes are coming: wait for the store to take them.
        val stored = withContext(NonCancellable + Dispatchers.IO) { Storage.flush(STORE_WAIT_MS) }
        if (!stored) SpikeLog.log("Check-in: the store did not catch up within ${STORE_WAIT_MS / 1000} s")
        Storage.refreshCounts(app)

        if (backlog && result == Outcome.Drained) prefs.edit().remove(Prefs.QUIET_SYNC_RADIO).apply()

        val total = (SystemClock.elapsedRealtime() - started) / 1000.0
        val battery = watchBattery(app)
        val read = if (plan.fullRead) "full read" else "brief read"
        // Complete once the store has caught up; how long new messages waited to reach the watch.
        val delays = Delays.summary(synchronized(newlyStored) { newlyStored.toList() })?.let { " ($it)" }.orEmpty()
        val kept = "%d new messages%s, %d already stored".format(recorder.newMessages, delays, recorder.duplicates)
        return when (result) {
            Outcome.Drained -> {
                val linkSeconds = (linkUpAt.get() - started) / 1000.0
                SpikeLog.log(
                    "Check-in: done in %.1f s (link up after %.1f s, %s), %s; watch battery %d%%"
                        .format(total, linkSeconds, read, kept, battery),
                )
                null
            }
            is Outcome.Ended -> {
                SpikeLog.log(
                    "Check-in: failed after %.1f s (%s, %s); %s before that; watch battery %d%%"
                        .format(total, result.reason, read, kept, battery),
                )
                result.reason
            }
        }
    }

    /** What decides whether and how often the system lets this app run in the background. */
    private fun conditions(app: Context): String {
        val power = app.getSystemService(PowerManager::class.java)
        val screen = if (power.isInteractive) "screen on" else "screen off"
        val idle = if (power.isDeviceIdleMode) ", Doze" else ""
        val optimised = if (power.isIgnoringBatteryOptimizations(app.packageName)) "not optimised" else "battery-optimised"
        val bucket = when (app.getSystemService(UsageStatsManager::class.java).appStandbyBucket) {
            UsageStatsManager.STANDBY_BUCKET_ACTIVE -> "active"
            UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> "working set"
            UsageStatsManager.STANDBY_BUCKET_FREQUENT -> "frequent"
            UsageStatsManager.STANDBY_BUCKET_RARE -> "rare"
            UsageStatsManager.STANDBY_BUCKET_RESTRICTED -> "restricted"
            else -> "other"
        }
        return "$screen$idle, standby bucket $bucket, $optimised, watch battery ${watchBattery(app)}%"
    }

    private fun watchBattery(app: Context): Int =
        app.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

    /** How a check-in ended: the queue collected, or the link ended (or never came) first. */
    private sealed interface Outcome {
        data object Drained : Outcome

        data class Ended(val reason: String) : Outcome
    }
}
