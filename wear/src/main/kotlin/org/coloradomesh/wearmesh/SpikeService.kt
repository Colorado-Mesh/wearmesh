package org.coloradomesh.wearmesh

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
import org.coloradomesh.meshcore.protocol.SelfInfo
import org.coloradomesh.wearmesh.data.Storage
import org.coloradomesh.wearmesh.data.SyncPlan
import org.coloradomesh.wearmesh.data.SyncRecorder

/**
 * Live mode: holds one link to a companion radio in a connectedDevice foreground service, so it
 * stays up with the screen off, stores what the radio sends (the same sync step as Check-in, with
 * a full read each time the link comes up) and logs it. It only reads from the radio; it sends
 * nothing on the mesh.
 *
 * When the link drops it reconnects with a growing pause (30 s doubling up to 5 min) and logs how
 * long each link lasted. Each time the protocol client reads the radio's battery (about every
 * 5 minutes by the wall clock) it logs the watch's own battery too, which is what the M0 battery
 * measurement is read from. A Handler timer would not do: its clock stops while the watch sleeps,
 * so with the screen off it hardly ever fires.
 *
 * All state is kept on the main thread; callbacks from the link and the protocol client are posted
 * there first.
 */
class SpikeService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var session: RadioSession? = null
    private var recorder: SyncRecorder? = null

    /** Bumped for every new session, so callbacks from an older one are ignored. */
    private var generation = 0
    private var address: String? = null
    private var stopping = false
    private var reconnects = 0
    private var linkUpAt = 0L
    private var contactsSeen = 0
    private var channelNames: Map<Int, String> = emptyMap()

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
                SpikeState.update { SpikeState.Snapshot(running = true, link = "Connecting…") }
                SpikeLog.log("M0: connecting to $target; watch battery ${watchBattery()}%")
                openSession()
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
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun openSession() {
        val target = address ?: return
        val mine = ++generation
        contactsSeen = 0
        val store = Storage.store(this)
        // Queued ahead of everything this session writes; a different radio clears the store.
        Storage.writer.execute {
            try {
                store.useRadio(target)
            } catch (e: Exception) {
                SpikeLog.log("Storage failed: ${e.message}")
            }
        }
        val sync = SyncRecorder(store, SyncPlan(fullRead = true, contactsSince = 0), Storage.writer, { SpikeLog.log(it) }, also = listener)
        recorder = sync
        val opened = RadioSession(
            context = this,
            address = target,
            listener = sync,
            appName = APP_NAME,
            onLog = { SpikeLog.log("Link: $it") },
            onLinkReady = { size -> main.post { if (mine == generation) linkReady(size) } },
            onClosed = { reason -> main.post { if (mine == generation) linkClosed(reason) } },
        )
        session = opened
        opened.open()
    }

    private fun linkReady(packetSize: Int) {
        linkUpAt = SystemClock.elapsedRealtime()
        reconnects = 0
        SpikeLog.log("M0: link up, packet size $packetSize bytes")
        SpikeState.update { it.copy(link = "Connected", packetSize = packetSize) }
        goForeground("Connected")
    }

    private fun linkClosed(reason: String) {
        session = null
        recorder?.finish()
        recorder = null
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
        main.postDelayed({ if (!stopping && session == null) openSession() }, pause)
    }

    private fun finish() {
        main.removeCallbacksAndMessages(null)
        SpikeLog.log("M0: stopped; watch battery ${watchBattery()}%")
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
        private const val EXTRA_ADDRESS = "address"
        private const val CHANNEL_ID = "link"
        private const val NOTIFICATION_ID = 1
        private const val RECONNECT_FIRST_MS = 30_000L
        private const val RECONNECT_MAX_MS = 5 * 60_000L

        /** The name the watch gives the radio when it says hello. */
        const val APP_NAME = "WearMesh"

        /** Call only once Bluetooth permission is granted and the app is on screen. */
        fun connect(context: Context, address: String) {
            context.startForegroundService(
                Intent(context, SpikeService::class.java).setAction(ACTION_CONNECT).putExtra(EXTRA_ADDRESS, address),
            )
        }

        fun stop(context: Context) {
            context.startService(Intent(context, SpikeService::class.java).setAction(ACTION_STOP))
        }
    }
}
