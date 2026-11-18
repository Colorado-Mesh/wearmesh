package org.coloradomesh.wearmesh

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.SystemClock

/**
 * One app per radio, on this watch too.
 *
 * Android shares one Bluetooth connection to a device between the apps on the watch. If another
 * app here (the WearTAK MeshCore plugin, say) already holds the radio, connecting would join that
 * app's link: the radio sees nothing new, both apps get each other's replies, and their writes
 * collide. So WearMesh doesn't connect to a radio the watch is already connected to, and says so.
 *
 * WearMesh's own link can stay up a moment after it closes, so a radio still connected within a
 * few seconds of WearMesh's own close is checked again shortly before being called in use.
 */
object SharedLink {
    /** Shown as the link state or the sync problem. */
    const val IN_USE = "In use by another app on this watch"

    /** How soon after WearMesh's own close a connection may still be its own. */
    private const val OWN_LINK_GRACE_MS = 10_000L

    /** How long to wait before checking again, within that time. */
    const val RECHECK_MS = 2_000L

    @Volatile private var ownClosedAt = 0L

    /** WearMesh closed a link of its own (Live or a check-in). */
    fun ownClosed() {
        ownClosedAt = SystemClock.elapsedRealtime()
    }

    /** Whether a connection to the radio now may still be WearMesh's own, just closed. */
    fun ownLinkMayLinger(): Boolean = SystemClock.elapsedRealtime() - ownClosedAt < OWN_LINK_GRACE_MS

    /**
     * Whether the watch already has a Bluetooth connection to [address], made by any app on it.
     * Needs BLUETOOTH_CONNECT; callers check it first.
     */
    @SuppressLint("MissingPermission")
    fun watchConnectedTo(context: Context, address: String): Boolean = try {
        context.getSystemService(BluetoothManager::class.java)
            ?.getConnectedDevices(BluetoothProfile.GATT)
            ?.any { it.address.equals(address, ignoreCase = true) } == true
    } catch (e: SecurityException) {
        SpikeLog.log("Could not ask Android which devices are connected: $e")
        false
    }
}
