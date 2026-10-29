package org.coloradomesh.wearmesh

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager

/**
 * The alarm that ends a Live reconnect pause (see [SpikeService]). A short wake lock bridges the
 * moment between this broadcast and the service taking its own, so the watch cannot fall back
 * asleep in between.
 */
class LiveReconnectReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SpikeLog.init(context)
        context.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WearMesh:live-reconnect-alarm")
            .acquire(BRIDGE_MS)
        try {
            SpikeService.reconnect(context)
        } catch (e: IllegalStateException) {
            // Only if Live had already stopped and the system no longer counts the app as running.
            SpikeLog.log("Live: reconnect alarm could not reach the service (${e.message})")
        }
    }

    private companion object {
        const val BRIDGE_MS = 10_000L
    }
}
