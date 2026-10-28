package org.coloradomesh.wearmesh

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** What Home's status line shows about syncing with the radio. Written by [CheckInRun]. */
object SyncStatus {
    data class State(
        val syncing: Boolean = false,
        /** When the last sync finished, by the watch's clock; 0 never. */
        val lastSyncAt: Long = 0,
        /** Why the last sync failed, or null when it worked. */
        val problem: String? = null,
        /** When the last sync ended, worked or not, by the watch's clock; 0 none in this run of the app. */
        val lastAttemptAt: Long = 0,
    )

    private val current = MutableStateFlow(State())
    val state: StateFlow<State> = current

    /** Picks up the last sync time saved by an earlier run of the app. */
    fun load(context: Context) {
        val at = Prefs.of(context).getLong(Prefs.LAST_SYNC_AT, 0)
        current.update { if (it.lastSyncAt == 0L) it.copy(lastSyncAt = at) else it }
    }

    fun started() = current.update { it.copy(syncing = true) }

    fun finished(context: Context, problem: String?) {
        val now = System.currentTimeMillis()
        if (problem == null) Prefs.of(context).edit().putLong(Prefs.LAST_SYNC_AT, now).apply()
        current.update {
            State(syncing = false, lastSyncAt = if (problem == null) now else it.lastSyncAt, problem = problem, lastAttemptAt = now)
        }
    }

    /** Forgets the last sync, as when another radio is chosen. A sync under way stays marked. */
    fun reset() = current.update { State(syncing = it.syncing) }

    /** A failure's reason as Home shows it, with a hint where one helps. */
    fun explain(reason: String): String = when {
        reason.startsWith("Could not connect") -> "Radio did not answer. Is another device, such as a phone, connected to it?"
        reason == "Bluetooth is off" -> "Bluetooth is off"
        else -> "Last sync failed: $reason"
    }
}
