package org.coloradomesh.wearmesh

import android.app.Application

/**
 * Starts before anything else in the app: opens the log, and writes any crash to it before
 * Android closes the app (M5 step 2), so a tester's log says what went wrong.
 */
class WearMeshApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SpikeLog.init(this)
        val system = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            SpikeLog.crash(thread, error)
            // Then as before: Android reports it and closes the app.
            system?.uncaughtException(thread, error)
        }
    }
}
