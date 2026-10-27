package org.coloradomesh.wearmesh

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Runs one check-in as a short foreground service of type connectedDevice, then stops.
 *
 * Being in the foreground keeps the process from being frozen or killed mid-check-in, which a
 * background job was not protected from in the first overnight test. The check-in holds its own
 * wake lock (see [CheckInRun]). A second start while one is running is ignored.
 */
class CheckInService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var running: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        SpikeLog.init(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.channel_check_in), NotificationManager.IMPORTANCE_MIN),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val why = intent?.getStringExtra(EXTRA_WHY) ?: "scheduled"
        try {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("Checking the radio")
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setSilent(true)
                .build()
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } catch (e: RuntimeException) {
            // For example a missing Bluetooth permission, which this service type requires.
            SpikeLog.log("Check-in ($why): could not run as a foreground service (${e.message})")
            stopSelf()
            return START_NOT_STICKY
        }
        if (running?.isActive == true) return START_NOT_STICKY
        running = scope.launch {
            try {
                CheckInRun.run(applicationContext, why, fullRead = why == CheckIn.ASKED_FOR)
            } finally {
                ServiceCompat.stopForeground(this@CheckInService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_WHY = "why"
        private const val CHANNEL_ID = "check_in"

        /** Different from the Live service's, so the two notifications never replace each other. */
        private const val NOTIFICATION_ID = 2

        /**
         * Throws ForegroundServiceStartNotAllowedException when the system will not let the app
         * start it from the background; [CheckInAlarmReceiver] catches that.
         */
        fun start(context: Context, why: String) {
            context.startForegroundService(Intent(context, CheckInService::class.java).putExtra(EXTRA_WHY, why))
        }
    }
}
