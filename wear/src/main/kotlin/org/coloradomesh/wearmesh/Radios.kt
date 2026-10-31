package org.coloradomesh.wearmesh

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.coloradomesh.wearmesh.data.Storage
import org.coloradomesh.wearmesh.notify.Notifier

/**
 * The radios this watch knows and the one it uses, kept in settings. Each radio keeps its own
 * stored history, so switching loses nothing; forgetting a radio deletes its history.
 */
object Radios {
    data class State(val known: List<KnownRadio> = emptyList(), val chosen: KnownRadio? = null)

    private val current = MutableStateFlow<State?>(null)

    /** Null until [load] has run once. */
    val state: StateFlow<State?> = current

    fun load(context: Context): State {
        val prefs = Prefs.of(context)
        val address = prefs.getString(Prefs.RADIO_ADDRESS, null)
        val name = prefs.getString(Prefs.RADIO_NAME, null) ?: address
        val chosen = if (address != null) KnownRadio(address, name ?: address) else null
        var known = KnownRadios.parse(prefs.getString(Prefs.RADIOS, null))
        // The spike kept only the chosen radio; carry it into the list.
        if (chosen != null && known.none { it.address == chosen.address }) known = KnownRadios.add(known, chosen)
        return State(known, chosen).also { current.value = it }
    }

    /** Makes [radio] the one in use and puts it first in the list. Its stored history, if any, shows again. */
    fun choose(context: Context, radio: KnownRadio) {
        val prefs = Prefs.of(context)
        val switching = prefs.getString(Prefs.RADIO_ADDRESS, null) != radio.address
        val known = KnownRadios.add(KnownRadios.parse(prefs.getString(Prefs.RADIOS, null)), radio)
        val edit = prefs.edit()
            .putString(Prefs.RADIO_ADDRESS, radio.address)
            .putString(Prefs.RADIO_NAME, radio.name)
            .putString(Prefs.RADIOS, KnownRadios.format(known))
        if (switching) {
            // Its next sync brings what queued while it was not in use: stored, but not notified.
            edit.remove(Prefs.SELF_NAME).remove(Prefs.LAST_SYNC_AT).putString(Prefs.QUIET_SYNC_RADIO, radio.address)
            SyncStatus.reset()
        }
        edit.apply()
        Storage.refreshCounts(context)
        SpikeLog.log("Radio: now using ${radio.name} (${radio.address})")
        Notifier.loadChoices(context, radio.address)
        load(context)
    }

    /** Removes [address] from the list and deletes its stored history; if it was in use, leaves no radio chosen. */
    fun forget(context: Context, address: String) {
        val prefs = Prefs.of(context)
        val known = KnownRadios.remove(KnownRadios.parse(prefs.getString(Prefs.RADIOS, null)), address)
        // Its complication channel goes too, so a radio added again starts on all that notify.
        val edit = prefs.edit().putString(Prefs.RADIOS, KnownRadios.format(known)).remove(Prefs.COMPLICATION_CHANNEL + address)
        if (prefs.getString(Prefs.RADIO_ADDRESS, null) == address) {
            edit.remove(Prefs.RADIO_ADDRESS).remove(Prefs.RADIO_NAME).remove(Prefs.SELF_NAME).remove(Prefs.LAST_SYNC_AT)
            SyncStatus.reset()
        }
        edit.apply()
        val store = Storage.store(context, address)
        Storage.later { store.forget() }
        Notifier.clearChoices(context, address)
        Storage.refreshCounts(context)
        SpikeLog.log("Radio: forgot $address")
        load(context)
    }
}
