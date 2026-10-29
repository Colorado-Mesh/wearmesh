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
    /** Minutes between check-ins: 15 by default, 30 or 60 in Settings (decided for M2). */
    val INTERVAL_CHOICES = listOf(15, 30, 60)
    private const val DEFAULT_MINUTES = 15

    /** The first check-in after turning it on, soon enough to see it work. */
    private const val FIRST_DELAY_MS = 60_000L
    private const val REQUEST_CODE = 1

    fun isOn(context: Context): Boolean = Prefs.of(context).getBoolean(Prefs.CHECK_IN, false)

    fun intervalMinutes(context: Context): Int =
        Prefs.of(context).getInt(Prefs.CHECK_IN_MINUTES, DEFAULT_MINUTES).takeIf { it in INTERVAL_CHOICES } ?: DEFAULT_MINUTES

    /** Moves to the next interval choice and sets the next alarm by it; returns the new interval. */
    fun nextInterval(context: Context): Int {
        val choices = INTERVAL_CHOICES
        val next = choices.getOrElse(choices.indexOf(intervalMinutes(context)) + 1) { choices.first() }
        Prefs.of(context).edit().putInt(Prefs.CHECK_IN_MINUTES, next).apply()
        SpikeLog.log("Check-in: every $next min")
        scheduleNext(context, next * 60_000L)
        return next
    }

    fun exactAlarmsAllowed(context: Context): Boolean = alarms(context).canScheduleExactAlarms()

    fun setOn(context: Context, on: Boolean) {
        Prefs.of(context).edit().putBoolean(Prefs.CHECK_IN, on).apply()
        if (on) {
            SpikeLog.log("Check-in: turned on, every ${intervalMinutes(context)} min")
            scheduleNext(context, FIRST_DELAY_MS)
        } else {
            cancelAlarm(context)
            SpikeLog.log("Check-in: turned off")
        }
    }

    /** Cancels the next check-in without turning Check-in off, for Quit. */
    fun cancelAlarm(context: Context) = alarms(context).cancel(alarmIntent(context))

    /**
     * Sets the next alarm, replacing any already set. Called by each alarm, after a restart or an
     * app update (which clear alarms), and when the permission is granted. Does nothing while
     * Check-in is off, or after Quit until the app is opened again.
     */
    fun scheduleNext(context: Context, delayMs: Long? = null) {
        if (!isOn(context) || Mode.hasQuit(context)) return
        val delay = delayMs ?: (intervalMinutes(context) * 60_000L)
        if (!exactAlarmsAllowed(context)) {
            SpikeLog.log("Check-in: not scheduled, \"Alarms & reminders\" is not allowed for WearMesh")
            return
        }
        alarms(context).setExactAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + delay,
            alarmIntent(context),
        )
        SpikeLog.log("Check-in: next at ${SpikeLog.clock(System.currentTimeMillis() + delay)}")
    }

    /** Why a check-in runs when Check now is tapped: a brief read, like a scheduled one. */
    const val ASKED_FOR = "asked for"

    /** Why one runs from Settings' "Read channels and contacts again": a full read. */
    const val FULL_READ_ASKED_FOR = "full read asked for"

    /** Why one runs when the app comes on screen (decided for M2: opening the app syncs). */
    const val APP_OPENED = "app opened"

    /** One check-in now, while the app is on screen; [full] reads every channel slot and contact too. */
    fun now(context: Context, full: Boolean = false) = CheckInService.start(context, if (full) FULL_READ_ASKED_FOR else ASKED_FOR)

    /** The sync when the app is opened. */
    fun onOpen(context: Context) = CheckInService.start(context, APP_OPENED)

    private fun alarms(context: Context) = context.getSystemService(AlarmManager::class.java)

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, CheckInAlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
