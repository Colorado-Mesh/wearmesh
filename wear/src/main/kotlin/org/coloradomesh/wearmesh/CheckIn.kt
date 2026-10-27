package org.coloradomesh.wearmesh

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/**
 * Schedules Check-in on exact alarms, and runs one on request.
 *
 * The first overnight test (docs/M2-step1-check-in.md) found that Doze held a WorkManager job back
 * for 6 hours. An exact allow-while-idle alarm fires in Doze too, at most about every 9 minutes,
 * so a 15-minute schedule holds. There is no repeating form of it, so each alarm sets the next.
 * An exact alarm also lets the app start a foreground service from the background, which is how
 * [CheckInAlarmReceiver] runs each check-in.
 *
 * Exact alarms need the "Alarms & reminders" permission, which Android 14 leaves off for new
 * installs: the user turns it on once. Without it nothing is scheduled, and the log says why.
 */
object CheckIn {
    const val INTERVAL_MINUTES = 15L
    private const val INTERVAL_MS = INTERVAL_MINUTES * 60_000L

    /** The first check-in after turning it on, soon enough to see it work. */
    private const val FIRST_DELAY_MS = 60_000L
    private const val REQUEST_CODE = 1

    fun isOn(context: Context): Boolean = Prefs.of(context).getBoolean(Prefs.CHECK_IN, false)

    fun exactAlarmsAllowed(context: Context): Boolean = alarms(context).canScheduleExactAlarms()

    fun setOn(context: Context, on: Boolean) {
        Prefs.of(context).edit().putBoolean(Prefs.CHECK_IN, on).apply()
        if (on) {
            SpikeLog.log("Check-in: turned on, every $INTERVAL_MINUTES min")
            scheduleNext(context, FIRST_DELAY_MS)
        } else {
            alarms(context).cancel(alarmIntent(context))
            SpikeLog.log("Check-in: turned off")
        }
    }

    /**
     * Sets the next alarm, replacing any already set. Called by each alarm, after a restart or an
     * app update (which clear alarms), and when the permission is granted. Does nothing while
     * Check-in is off.
     */
    fun scheduleNext(context: Context, delayMs: Long = INTERVAL_MS) {
        if (!isOn(context)) return
        if (!exactAlarmsAllowed(context)) {
            SpikeLog.log("Check-in: not scheduled, \"Alarms & reminders\" is not allowed for WearMesh")
            return
        }
        alarms(context).setExactAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + delayMs,
            alarmIntent(context),
        )
        SpikeLog.log("Check-in: next at ${SpikeLog.clock(System.currentTimeMillis() + delayMs)}")
    }

    /** Why a check-in asked for on screen runs; such a check-in reads everything. */
    const val ASKED_FOR = "asked for"

    /** One check-in now, while the app is on screen. */
    fun now(context: Context) = CheckInService.start(context, ASKED_FOR)

    private fun alarms(context: Context) = context.getSystemService(AlarmManager::class.java)

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, CheckInAlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
