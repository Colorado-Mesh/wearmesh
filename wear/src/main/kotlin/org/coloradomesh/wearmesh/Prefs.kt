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

    /**
     * A radio whose next sync brings a backlog that should not notify: set when a radio is chosen,
     * cleared once its first sync after that has collected its queue.
     */
    const val QUIET_SYNC_RADIO = "quiet_sync_radio"

    /** Whether the first-run offer to move from Live to Check-in has been answered. */
    const val MODE_PROMPT_DONE = "mode_prompt_done"

    /**
     * Set by Quit: nothing runs in the background (no Live link, no Check-in alarm) until the app
     * is opened again, which clears it and carries on in the mode that was in use.
     */
    const val QUIT = "quit"

    /** Notification choices for one radio's conversations; the key is this plus the radio's address. */
    const val NOTIFY_CHOICES = "notify_choices_"

    /** The quick replies, as [org.coloradomesh.wearmesh.ui.QuickReplies] formats them; unset means the starting set. */
    const val QUICK_REPLIES = "quick_replies"

    fun of(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
