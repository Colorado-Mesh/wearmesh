package org.coloradomesh.wearmesh.ble

import android.bluetooth.BluetoothManager
import android.content.Context
import org.coloradomesh.takbridge.protocol.CompanionClient
import org.coloradomesh.wearmesh.SpikeLog
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * One radio, from connect to disconnect: the Bluetooth link plus the protocol client that talks
 * over it. Adapted from MeshCore TAK Bridge's RadioSession. What the radio reports goes to
 * [listener]; [onLinkReady] runs when frames can flow, and [onClosed] runs once when the link ends.
 * A session is used once; make a new one to reconnect.
 */
class RadioSession(
    private val context: Context,
    private val address: String,
    listener: CompanionClient.Listener,
    private val onLinkReady: (packetSize: Int) -> Unit,
    private val onClosed: (reason: String) -> Unit,
) : GattLink.Events {
    private val link = GattLink(context, this)
    private val client = CompanionClient(transmit = { link.send(it) }, listener = listener, appName = APP_NAME)
    private val ticker = Executors.newSingleThreadScheduledExecutor()

    fun open() {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null || !adapter.isEnabled) {
            ticker.shutdownNow()
            onClosed.invoke("Bluetooth is off")
            return
        }
        link.connect(adapter.getRemoteDevice(address))
    }

    fun close() = link.close()

    override fun onLog(message: String) = SpikeLog.log("Link: $message")

    override fun onReady(packetSize: Int) {
        if (packetSize < COMFORTABLE_PACKET_BYTES) {
            onLog("Warning: packets are limited to $packetSize bytes, so long frames will not fit")
        }
        onLinkReady(packetSize)
        client.start()
        ticker.scheduleWithFixedDelay({ client.tick() }, 1, 1, TimeUnit.SECONDS)
    }

    override fun onFrame(frame: ByteArray) = client.onFrame(frame)

    override fun onClosed(reason: String) {
        ticker.shutdownNow()
        onClosed.invoke(reason)
    }

    private companion object {
        /** The name the watch gives the radio when it says hello. */
        const val APP_NAME = "WearMesh"

        /** Enough for the longest frame the companion protocol sends. */
        const val COMFORTABLE_PACKET_BYTES = 172
    }
}
