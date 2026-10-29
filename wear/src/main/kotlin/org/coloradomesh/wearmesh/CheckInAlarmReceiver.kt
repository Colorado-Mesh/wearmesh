package org.coloradomesh.wearmesh

import android.app.ForegroundServiceStartNotAllowedException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch

/**
 * The Check-in alarm. Sets the next alarm first, so the schedule carries on whatever happens to
 * this one, then starts [CheckInService] for the check-in itself.
 *
 * If the system refuses to start the service from the background, it runs the check-in inside
 * this broadcast instead, with a shorter time limit, since a broadcast may only run for a short
 * while. The log says which way each check-in ran.
 */
class CheckInAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SpikeLog.init(context)
        // An alarm already on its way when Quit cancelled it.
        if (Mode.hasQuit(context)) return
        CheckIn.scheduleNext(context)
        try {
            CheckInService.start(context, "scheduled")
        } catch (e: ForegroundServiceStartNotAllowedException) {
            SpikeLog.log("Check-in: the system would not start the foreground service (${e.message}); running inside the alarm")
            val pending = goAsync()
            val app = context.applicationContext
            CheckInRun.scope.launch {
                try {
                    CheckInRun.run(app, "scheduled, inside the alarm", IN_ALARM_LIMIT_MS)
                } finally {
                    pending.finish()
                }
            }
        }
    }

    private companion object {
        /**
         * With the worst-case waits for the store (10 s before, 10 s after) and for the link to
         * close (5 s), this stays well inside the minute a background broadcast is given.
         */
        const val IN_ALARM_LIMIT_MS = 25_000L
    }
}
