package org.coloradomesh.wearmesh

import android.content.Context

/**
 * Live or Check-in (M2 step 4): one choice in Settings in place of separate Check-in and Live
 * switches. Check-in mode is Check-in turned on; Live mode is Check-in off, with the Live link held
 * whenever the app has a radio. A new install starts in Live and is offered Check-in once
 * (decided for M2).
 */
object Mode {
    enum class Kind { LIVE, CHECK_IN }

    fun get(context: Context): Kind = if (CheckIn.isOn(context)) Kind.CHECK_IN else Kind.LIVE

    /**
     * Switches to [kind]. Live starts a foreground service, which Android allows only with the app
     * on screen, so [startLive] false only records Live; the app starts the link next time it is
     * opened.
     */
    fun set(context: Context, kind: Kind, startLive: Boolean = true) {
        when (kind) {
            Kind.LIVE -> {
                CheckIn.setOn(context, false)
                val radio = Prefs.of(context).getString(Prefs.RADIO_ADDRESS, null)
                if (startLive && radio != null && !SpikeState.state.value.running) {
                    try {
                        SpikeService.connect(context, radio)
                    } catch (e: IllegalStateException) {
                        SpikeLog.log("Live: could not start (${e.message}); it starts when the app is next opened")
                    }
                }
            }
            Kind.CHECK_IN -> {
                if (SpikeState.state.value.running) SpikeService.stop(context)
                CheckIn.setOn(context, true)
            }
        }
        SpikeLog.log("Mode: ${if (kind == Kind.LIVE) "Live" else "Check-in"}")
    }

    fun promptDone(context: Context): Boolean = Prefs.of(context).getBoolean(Prefs.MODE_PROMPT_DONE, false)

    fun markPromptDone(context: Context) = Prefs.of(context).edit().putBoolean(Prefs.MODE_PROMPT_DONE, true).apply()
}
