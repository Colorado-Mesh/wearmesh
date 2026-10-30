package org.coloradomesh.wearmesh

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import org.coloradomesh.meshcore.ble.RadioSession
import org.coloradomesh.meshcore.protocol.Channel
import org.coloradomesh.meshcore.protocol.ChannelMessage
import org.coloradomesh.meshcore.protocol.CompanionClient
import org.coloradomesh.meshcore.protocol.Contact
import org.coloradomesh.meshcore.protocol.DeviceInfo
import org.coloradomesh.meshcore.protocol.DirectMessage
import org.coloradomesh.meshcore.protocol.DirectOutcome
import org.coloradomesh.meshcore.protocol.SelfInfo
import org.coloradomesh.wearmesh.data.IncomingMessage
import org.coloradomesh.wearmesh.data.OutboxSender
import org.coloradomesh.wearmesh.data.Storage
import org.coloradomesh.wearmesh.data.SyncPlan
import org.coloradomesh.wearmesh.data.SyncRecorder
import org.coloradomesh.wearmesh.glance.Glances
import org.coloradomesh.wearmesh.notify.Notifier
import java.util.Collections
import java.util.concurrent.atomic.AtomicReference

/**
 * Live mode: holds one link to a companion radio in a connectedDevice foreground service, so it
 * stays up with the screen off, stores what the radio sends (the same sync step as Check-in, with
 * a full read each time the link comes up) and logs it. It only reads from the radio; it sends
 * nothing on the mesh.
 *
 * When the link drops it reconnects with a growing pause (30 s doubling up to 5 min) and logs how
 * long each link lasted. The pause is timed by an exact alarm that fires in Doze as well as by a
 * Handler, whichever comes first: a Handler alone stops while the watch sleeps, which held Live
 * down for 18 minutes after one drop in the step 5 carry. In Doze the system lets such an alarm
 * fire at most about every 9 minutes, so a pause can stretch to that. Each reconnect holds a
 * partial wake lock until the link is up or has failed, so its own time limits run.
 *
 * Each time the protocol client reads the radio's battery (about every 5 minutes by the wall
 * clock) it logs the watch's own battery too, which is what the battery measurements are read
 * from. A Handler timer would not do: its clock stops while the watch sleeps,
 * so with the screen off it hardly ever fires.
 *
 * Each link sends what is waiting in the outbox once its start-up has collected the queue, and
 * again whenever a message is written ([sendPending]); a link that drops leaves unsent channel
 * messages for the next. It checks the radio's clock at start-up too (see [RadioClock]).
 *
 * All state is kept on the main thread; callbacks from the link and the protocol client are posted
 * there first.
 */
class SpikeService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var session: RadioSession? = null
    private var recorder: SyncRecorder? = null

    /** The current link's outbox sender, once its start-up has collected the queue; set on the link's threads. */
    @Volatile
    private var readyOutbox: OutboxSender? = null
    private var outbox: OutboxSender? = null

    /** Messages stored since the radio's queue was last emptied; notified together, on the storage thread. */
    private val toNotify = Collections.synchronizedList(ArrayList<IncomingMessage>())

    /** Bumped for every new session, so callbacks from an older one are ignored. */
    private var generation = 0
    private var address: String? = null
    private var stopping = false
    private var reconnects = 0
    private var linkUpAt = 0L
    private var contactsSeen = 0
    private var channelNames: Map<Int, String> = emptyMap()

    /** Held from a reconnect's start until its link is up or closed, at most [RECONNECT_WAKE_MS]. */
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        SpikeLog.init(this)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.channel_link), NotificationManager.IMPORTANCE_LOW),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val target = intent.getStringExtra(EXTRA_ADDRESS) ?: return START_NOT_STICKY
                goForeground("Connecting to $target")
                if (target == address && session != null) return START_NOT_STICKY
                stopping = false
                reconnects = 0
                address = target
                session?.close()
                session = null
                recorder?.finish()
                recorder = null
                endOutbox()
                SpikeState.update { SpikeState.Snapshot(running = true, link = "Connecting…") }
                SpikeLog.log("M0: connecting to $target; watch battery ${watchBattery()}%")
                openSession()
            }
            ACTION_SEND_PENDING -> {
                // Before start-up has collected the queue, the link sends the outbox itself then.
                readyOutbox?.sendPending()
                if (address == null && session == null) stopSelf()
            }
            ACTION_RECONNECT -> {
                if (address == null) {
                    // A stale alarm, after the service had stopped.
                    if (session == null) stopSelf()
                } else {
                    reconnectNow("alarm")
                }
            }
            ACTION_STOP -> {
                stopping = true
                val open = session
                if (open != null) open.close() else finish()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopping = true
        session?.close()
        session = null
        recorder?.finish()
        recorder = null
        endOutbox()
        main.removeCallbacksAndMessages(null)
        cancelReconnectAlarm()
        releaseWakeLock()
        super.onDestroy()
    }

    private fun openSession() {
        val target = address ?: return
        val mine = ++generation
        contactsSeen = 0
        val store = Storage.store(this, target)
        // Queued ahead of everything this session writes.
        Storage.later { store.open() }
        // This link's own session, never a later one's: the sender is called on the storage thread.
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
            { SpikeLog.log(it) },
            onFailed = { message, status, why -> Notifier.failed(this, target, message, status, why) },
        )
        outbox = sender
        // This link's own answers about sends go to its own sender; the rest to the shared listener.
        val linkListener = object : CompanionClient.Listener by listener {
            override fun onQueueEmpty() {
                listener.onQueueEmpty()
                if (mine == generation && readyOutbox !== sender) {
                    readyOutbox = sender
                    sender.sendPending()
                }
            }

            override fun onSendResult(channelIndex: Int, text: String, accepted: Boolean) =
                sender.onSendResult(channelIndex, text, accepted)

            override fun onDirectRefused(id: Long, errorCode: Int?) = sender.onDirectRefused(id, errorCode)

            override fun onDirectResult(id: Long, publicKeyHex: String, text: String, outcome: DirectOutcome) =
                sender.onDirectResult(id, outcome)
        }
        val sync = SyncRecorder(
            store,
            SyncPlan(fullRead = true, contactsSince = 0),
            Storage.writer,
            { SpikeLog.log(it) },
            also = linkListener,
            // Gathered and notified once the radio's queue is empty (see onQueueEmpty), so a burst
            // after reconnecting is one update, not one per message.
            onStored = { message -> toNotify.add(message) },
        )
        recorder = sync
        val opened = RadioSession(
            context = this,
            address = target,
            listener = sync,
            appName = APP_NAME,
            startUp = CompanionClient.StartUp(clockTolerance = RadioClock.TOLERANCE_SECONDS),
            onLog = { SpikeLog.log("Link: $it") },
            onLinkReady = { size -> main.post { if (mine == generation) linkReady(size) } },
            onClosed = { reason -> main.post { if (mine == generation) linkClosed(reason) } },
        )
        session = opened
        link.set(opened)
        opened.open()
    }

    private fun linkReady(packetSize: Int) {
        linkUpAt = SystemClock.elapsedRealtime()
        reconnects = 0
        releaseWakeLock()
        SpikeLog.log("M0: link up, packet size $packetSize bytes")
        Glances.refresh(this)
        SpikeState.update { it.copy(link = "Connected", packetSize = packetSize) }
        goForeground("Connected")
    }

    private fun linkClosed(reason: String) {
        session = null
        releaseWakeLock()
        recorder?.finish()
        recorder = null
        endOutbox()
        Storage.refreshCounts(this)
        if (linkUpAt > 0) {
            val minutes = (SystemClock.elapsedRealtime() - linkUpAt) / 60_000.0
            SpikeLog.log("M0: link down after %.1f min (%s)".format(minutes, reason))
            linkUpAt = 0
        }
        if (stopping) {
            finish()
            return
        }
        val pause = (RECONNECT_FIRST_MS shl reconnects.coerceAtMost(4)).coerceAtMost(RECONNECT_MAX_MS)
        reconnects++
        SpikeLog.log("M0: reconnecting in ${pause / 1000} s (try $reconnects)")
        SpikeState.update { it.copy(link = "Reconnecting in ${pause / 1000} s", packetSize = null) }
        goForeground("Link lost; reconnecting")
        main.postDelayed(reconnectTimer, pause)
        val alarms = getSystemService(AlarmManager::class.java)
        val at = SystemClock.elapsedRealtime() + pause
        if (alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, reconnectIntent())
        } else {
            // Without "Alarms & reminders" the system picks the moment, still also in Doze.
            alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, reconnectIntent())
        }
    }

    /** The end of a reconnect pause, from the Handler or the alarm, whichever comes first. */
    private val reconnectTimer = Runnable { reconnectNow("timer") }

    private fun reconnectNow(by: String) {
        main.removeCallbacks(reconnectTimer)
        cancelReconnectAlarm()
        if (stopping || session != null) return
        if (by == "alarm") SpikeLog.log("M0: reconnecting now (woken by the alarm)")
        acquireWakeLock()
        openSession()
    }

    /** A broadcast, not a service intent: the system keeps the watch awake while a receiver runs. */
    private fun reconnectIntent(): PendingIntent = PendingIntent.getBroadcast(
        this,
        RECONNECT_REQUEST,
        Intent(this, LiveReconnectReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun cancelReconnectAlarm() = getSystemService(AlarmManager::class.java).cancel(reconnectIntent())

    private fun acquireWakeLock() {
        val lock = wakeLock ?: getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WearMesh:live-reconnect")
            .apply { setReferenceCounted(false) }
            .also { wakeLock = it }
        lock.acquire(RECONNECT_WAKE_MS)
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
    }

    /** The link has ended: its sender records what it had handed over, and the next link takes the rest. */
    private fun endOutbox() {
        readyOutbox = null
        outbox?.finish()
        outbox = null
    }

    private fun finish() {
        main.removeCallbacksAndMessages(null)
        cancelReconnectAlarm()
        releaseWakeLock()
        SpikeLog.log("M0: stopped; watch battery ${watchBattery()}%")
        Glances.refresh(this)
        SpikeState.update { SpikeState.Snapshot() }
        address = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun watchBattery(): Int =
        getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

    private fun goForeground(text: String) {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
    }

    /** Called on the link's own threads, with the protocol client locked: it only logs and updates state. */
    private val listener = object : CompanionClient.Listener {
        override fun onLog(message: String) = SpikeLog.log("Radio: $message")

        override fun onSelf(info: SelfInfo) {
            SpikeLog.log(
                "Radio: ${info.name}, %.3f MHz, BW %.1f kHz, SF%d, CR%d, %d dBm"
                    .format(info.frequencyMhz, info.bandwidthKhz, info.spreadingFactor, info.codingRate, info.txPowerDbm),
            )
            SpikeState.update { it.copy(radioName = info.name) }
            Prefs.of(this@SpikeService).edit().putString(Prefs.SELF_NAME, info.name).apply()
        }

        override fun onDevice(info: DeviceInfo) {
            val firmware = listOf(info.version, info.build).filter { it.isNotEmpty() }.joinToString(" ")
                .ifEmpty { "protocol ${info.firmwareVersion}" }
            SpikeLog.log("Radio: ${info.model.ifEmpty { "unknown model" }}, firmware $firmware, ${info.maxChannels} channel slots")
            SpikeState.update { it.copy(firmware = firmware) }
        }

        override fun onContact(contact: Contact) {
            contactsSeen++
            val count = contactsSeen
            SpikeState.update { it.copy(contacts = count) }
        }

        override fun onChannels(channels: List<Channel>) {
            channelNames = channels.associate { it.index to it.name }
            SpikeLog.log("Radio: $contactsSeen contacts, ${channels.size} channels")
            for (channel in channels) SpikeLog.log("Channel ${channel.index}: ${channel.name} (${channel.kind.name.lowercase()})")
            SpikeState.update { it.copy(channels = channels) }
        }

        override fun onChannelMessage(message: ChannelMessage) {
            val name = channelNames[message.channelIndex] ?: "channel ${message.channelIndex}"
            SpikeLog.log("$name ${age(message.timestamp)}: ${message.text}")
            SpikeState.update { it.copy(messages = it.messages + 1) }
        }

        override fun onDirectMessage(message: DirectMessage) {
            SpikeLog.log("DM from ${message.senderPrefixHex} ${age(message.timestamp)}: ${message.text}")
            SpikeState.update { it.copy(messages = it.messages + 1) }
        }

        override fun onQueueEmpty() {
            Storage.refreshCounts(this@SpikeService)
            val radio = address ?: return
            // Queued behind this collection's writes, so every message it stored is in the list. The
            // queue waiting at connect after choosing a radio is its backlog: stored, not notified;
            // from then on, new messages notify.
            Storage.later {
                val batch = synchronized(toNotify) { toNotify.toList().also { toNotify.clear() } }
                val prefs = Prefs.of(this@SpikeService)
                val backlog = prefs.getString(Prefs.QUIET_SYNC_RADIO, null) == radio
                if (backlog) prefs.edit().remove(Prefs.QUIET_SYNC_RADIO).apply()
                Notifier.afterSync(this@SpikeService, radio, batch, backlog)
                Glances.refresh(this@SpikeService)
            }
        }

        override fun onRadioClock(radioSeconds: Long, clientSeconds: Long, setting: Boolean) {
            RadioClock.describe(radioSeconds, clientSeconds, setting)?.let { SpikeLog.log(it) }
        }

        override fun onBattery(millivolts: Int) {
            if (millivolts > 0) SpikeLog.log("Radio battery: $millivolts mV")
            SpikeLog.log("M0: watch battery ${watchBattery()}%")
            SpikeState.update { it.copy(radioMillivolts = millivolts.takeIf { mv -> mv > 0 }) }
        }
    }

    /** How long ago a message was sent, by the sender's clock, which may be off. */
    private fun age(timestampSeconds: Long): String {
        val seconds = System.currentTimeMillis() / 1000 - timestampSeconds
        return when {
            seconds < 0 -> "(sender clock ahead)"
            seconds < 120 -> "(${seconds}s ago)"
            seconds < 7200 -> "(${seconds / 60} min ago)"
            else -> "(${seconds / 3600} h ago)"
        }
    }

    companion object {
        private const val ACTION_CONNECT = "org.coloradomesh.wearmesh.CONNECT"
        private const val ACTION_STOP = "org.coloradomesh.wearmesh.STOP"
        private const val ACTION_RECONNECT = "org.coloradomesh.wearmesh.RECONNECT"
        private const val ACTION_SEND_PENDING = "org.coloradomesh.wearmesh.SEND_PENDING"
        private const val EXTRA_ADDRESS = "address"
        private const val CHANNEL_ID = "link"
        private const val NOTIFICATION_ID = 1
        private const val RECONNECT_FIRST_MS = 30_000L
        private const val RECONNECT_MAX_MS = 5 * 60_000L

        /** Longer than a connection with all its tries; released as soon as it is up or failed. */
        private const val RECONNECT_WAKE_MS = 90_000L
        private const val RECONNECT_REQUEST = 10

        /** The name the watch gives the radio when it says hello. */
        const val APP_NAME = "WearMesh"

        /** Call only once Bluetooth permission is granted and the app is on screen. */
        fun connect(context: Context, address: String) {
            context.startForegroundService(
                Intent(context, SpikeService::class.java).setAction(ACTION_CONNECT).putExtra(EXTRA_ADDRESS, address),
            )
        }

        /** A message was written: send it over the link, now if it is up, else when it next is. */
        fun sendPending(context: Context) {
            context.startService(Intent(context, SpikeService::class.java).setAction(ACTION_SEND_PENDING))
        }

        /** From [LiveReconnectReceiver]: the reconnect pause is over. */
        fun reconnect(context: Context) {
            context.startService(Intent(context, SpikeService::class.java).setAction(ACTION_RECONNECT))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, SpikeService::class.java).setAction(ACTION_STOP))
        }
    }
}
