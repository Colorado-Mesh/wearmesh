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
import org.coloradomesh.meshcore.protocol.Channel
import org.coloradomesh.meshcore.protocol.ChannelMessage
import org.coloradomesh.meshcore.protocol.CompanionClient
import org.coloradomesh.meshcore.protocol.DirectMessage
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * One Check-in: connect to the radio, collect what it has queued, disconnect.
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
 */
object CheckInRun {
    /** For runs that outlive the caller, such as one inside an alarm broadcast. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Long enough for 3 connection tries, pairing checks and reading a few hundred contacts. */
    const val TIME_LIMIT_MS = 90_000L
    private const val CLOSE_WAIT_MS = 5_000L

    /** The wake lock outlasts the time limit by this much, then lets go whatever happens. */
    private const val WAKE_MARGIN_MS = 15_000L

    /** One check-in at a time in this process, however it was started. */
    private val running = AtomicBoolean(false)

    suspend fun run(context: Context, why: String, timeLimitMs: Long = TIME_LIMIT_MS) {
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
                acquire(timeLimitMs + CLOSE_WAIT_MS + WAKE_MARGIN_MS)
            }
        try {
            checkIn(app, why, timeLimitMs)
        } catch (e: CancellationException) {
            SpikeLog.log("Check-in ($why): stopped before it finished")
            throw e
        } finally {
            if (wake.isHeld) wake.release()
            running.set(false)
        }
    }

    private suspend fun checkIn(app: Context, why: String, timeLimitMs: Long) {
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

        val outcome = CompletableDeferred<Outcome>()
        val closed = CompletableDeferred<String>()
        val linkUpAt = AtomicLong(0)
        val received = AtomicInteger(0)
        val channelNames = AtomicReference<Map<Int, String>>(emptyMap())

        // Called on the link's threads with the protocol client locked: nothing here may block.
        val listener = object : CompanionClient.Listener {
            override fun onLog(message: String) = SpikeLog.log("Check-in radio: $message")

            override fun onChannels(channels: List<Channel>) {
                channelNames.set(channels.associate { it.index to it.name })
            }

            override fun onChannelMessage(message: ChannelMessage) {
                received.incrementAndGet()
                val name = channelNames.get()[message.channelIndex] ?: "channel ${message.channelIndex}"
                SpikeLog.log("Check-in: $name: ${message.text}")
            }

            override fun onDirectMessage(message: DirectMessage) {
                received.incrementAndGet()
                SpikeLog.log("Check-in: DM from ${message.senderPrefixHex}: ${message.text}")
            }

            override fun onQueueEmpty() {
                outcome.complete(Outcome.Drained)
            }
        }
        val session = RadioSession(
            context = app,
            address = address,
            listener = listener,
            appName = SpikeService.APP_NAME,
            onLog = { SpikeLog.log("Check-in link: $it") },
            onLinkReady = { linkUpAt.set(SystemClock.elapsedRealtime()) },
            onClosed = { reason ->
                closed.complete(reason)
                outcome.complete(Outcome.Ended(reason))
            },
        )
        val result = try {
            session.open()
            withTimeoutOrNull(timeLimitMs) { outcome.await() }
                ?: Outcome.Ended("not finished within ${timeLimitMs / 1000} s")
        } finally {
            // Also when the system stops the job: a link left open would keep the radio busy,
            // locking out the Live link and the next check-in.
            session.close()
            withContext(NonCancellable) {
                if (withTimeoutOrNull(CLOSE_WAIT_MS) { closed.await() } == null) {
                    SpikeLog.log("Check-in: the link did not report closing within ${CLOSE_WAIT_MS / 1000} s")
                }
            }
        }

        val total = (SystemClock.elapsedRealtime() - started) / 1000.0
        val battery = watchBattery(app)
        when (result) {
            Outcome.Drained -> {
                val linkSeconds = (linkUpAt.get() - started) / 1000.0
                SpikeLog.log(
                    "Check-in: done in %.1f s (link up after %.1f s), %d new messages; watch battery %d%%"
                        .format(total, linkSeconds, received.get(), battery),
                )
            }
            is Outcome.Ended -> SpikeLog.log(
                "Check-in: failed after %.1f s (%s); %d messages before that; watch battery %d%%"
                    .format(total, result.reason, received.get(), battery),
            )
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
