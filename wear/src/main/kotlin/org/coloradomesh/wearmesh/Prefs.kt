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

    fun of(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
