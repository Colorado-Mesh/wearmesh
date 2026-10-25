package org.coloradomesh.wearmesh

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * Turns the periodic Check-in on and off, and runs one on request.
 *
 * WorkManager runs the job on the system's job scheduler, which keeps time by the wall clock and
 * survives the watch sleeping and restarting; that is why it is used rather than a Handler delay
 * (see the M0 lesson in docs/design.md). The scheduler will not run a periodic job more often than
 * every 15 minutes, and Doze can hold it back further while the watch is still; how far is what the
 * overnight test measures.
 */
object CheckIn {
    private const val PERIODIC = "check-in"
    private const val ONCE = "check-in-now"
    const val INTERVAL_MINUTES = 15L

    fun isOn(context: Context): Boolean = Prefs.of(context).getBoolean(Prefs.CHECK_IN, false)

    fun setOn(context: Context, on: Boolean) {
        Prefs.of(context).edit().putBoolean(Prefs.CHECK_IN, on).apply()
        val work = WorkManager.getInstance(context)
        if (on) {
            val request = PeriodicWorkRequestBuilder<CheckInWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES)
                .setInputData(workDataOf(CheckInWorker.KEY_WHY to "scheduled"))
                .build()
            work.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
            SpikeLog.log("Check-in: turned on, every $INTERVAL_MINUTES min")
        } else {
            work.cancelUniqueWork(PERIODIC)
            SpikeLog.log("Check-in: turned off")
        }
    }

    /** One check-in now, for testing; ignored while one is already waiting to run. */
    fun now(context: Context) {
        val request = OneTimeWorkRequestBuilder<CheckInWorker>()
            .setInputData(workDataOf(CheckInWorker.KEY_WHY to "asked for"))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(ONCE, ExistingWorkPolicy.KEEP, request)
    }
}
