package org.coloradomesh.wearmesh

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Sets the Check-in alarm again when something has cleared it or made it possible: the watch
 * restarting, the app being updated, or "Alarms & reminders" being allowed. Does nothing while
 * Check-in is off.
 */
class CheckInRescheduler : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SpikeLog.init(context)
        if (!CheckIn.isOn(context)) return
        val why = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> "the watch restarted"
            Intent.ACTION_MY_PACKAGE_REPLACED -> "the app was updated"
            else -> "\"Alarms & reminders\" changed"
        }
        SpikeLog.log("Check-in: rescheduling, $why")
        CheckIn.scheduleNext(context, RESCHEDULE_DELAY_MS)
    }

    private companion object {
        const val RESCHEDULE_DELAY_MS = 60_000L
    }
}
