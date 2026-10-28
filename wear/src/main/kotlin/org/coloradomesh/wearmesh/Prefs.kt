package org.coloradomesh.wearmesh

import android.content.Context
import android.content.SharedPreferences

/** The app's saved settings: one file, shared by the screen, the Live service and Check-in. */
object Prefs {
    private const val FILE = "wearmesh"

    const val RADIO_ADDRESS = "radio_address"
    const val RADIO_NAME = "radio_name"

    /** Whether the periodic Check-in job is scheduled. */
    const val CHECK_IN = "check_in"

    /** Minutes between check-ins: 15, 30 or 60. */
    const val CHECK_IN_MINUTES = "check_in_minutes"

    /** Radios this watch knows, as [KnownRadios] formats them. */
    const val RADIOS = "radios"

    /** The radio's own node name, from its greeting; mentions of it stand out. */
    const val SELF_NAME = "self_name"

    /** When the last sync with the radio finished, by the watch's clock. */
    const val LAST_SYNC_AT = "last_sync_at"

    fun of(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
