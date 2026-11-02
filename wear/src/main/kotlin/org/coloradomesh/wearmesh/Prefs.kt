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

    /** The channel the complication counts, such as "c:3"; the key is this plus the radio's address. Unset: all notifying conversations. */
    const val COMPLICATION_CHANNEL = "complication_channel_"

    /**
     * Settings › Keep contacts on radio (decided 1 November 2026, off by default): after each full
     * read, the people you message are made favourites on the radio, and any it dropped are put
     * back. The only time WearMesh changes the radio's contact list.
     */
    const val KEEP_CONTACTS = "keep_contacts"

    /** When the next check-in alarm is due, by the watch's clock; unset when none is set. For the Status tile. */
    const val NEXT_CHECK_IN_AT = "next_check_in_at"

    /** The radio's last battery reading in millivolts, and when it came (watch clock); for the Status tile. */
    const val RADIO_MILLIVOLTS = "radio_millivolts"
    const val RADIO_MILLIVOLTS_AT = "radio_millivolts_at"

    /** Why the last sync failed; removed when one works. For the Status tile. */
    const val LAST_PROBLEM = "last_problem"

    /**
     * Held while the radio in use changes ([org.coloradomesh.wearmesh.Radios]) and by
     * [writeForRadio], so a sync of one radio that ends after another is chosen saves nothing
     * under the new one.
     */
    val RADIO_LOCK = Any()

    /**
     * Applies [write] only if [radio] is still the one in use, checked and written under
     * [RADIO_LOCK]. Returns whether it wrote. `apply` updates the settings at once (the disk
     * later), so the next check sees it.
     */
    fun writeForRadio(context: Context, radio: String, write: (SharedPreferences, SharedPreferences.Editor) -> Unit): Boolean {
        val prefs = of(context)
        synchronized(RADIO_LOCK) {
            if (prefs.getString(RADIO_ADDRESS, null) != radio) return false
            val edit = prefs.edit()
            write(prefs, edit)
            edit.apply()
            return true
        }
    }

    fun of(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
