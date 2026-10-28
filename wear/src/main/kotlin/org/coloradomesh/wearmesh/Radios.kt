package org.coloradomesh.wearmesh

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.coloradomesh.wearmesh.data.Storage

/**
 * The radios this watch knows and the one it uses, kept in settings. Switching radios or
 * forgetting the one in use clears the message store (decided for M2): one history, one radio.
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

    /**
     * Makes [radio] the one in use and puts it first in the list. A different radio from the one
     * whose messages are stored clears the store now, so its messages are not shown as the new one's.
     */
    fun choose(context: Context, radio: KnownRadio) {
        val prefs = Prefs.of(context)
        val switching = prefs.getString(Prefs.RADIO_ADDRESS, null) != radio.address
        val known = KnownRadios.add(KnownRadios.parse(prefs.getString(Prefs.RADIOS, null)), radio)
        val edit = prefs.edit()
            .putString(Prefs.RADIO_ADDRESS, radio.address)
            .putString(Prefs.RADIO_NAME, radio.name)
            .putString(Prefs.RADIOS, KnownRadios.format(known))
        if (switching) {
            edit.remove(Prefs.SELF_NAME).remove(Prefs.LAST_SYNC_AT)
            SyncStatus.reset()
        }
        edit.apply()
        val store = Storage.store(context)
        Storage.later { store.useRadio(radio.address) }
        Storage.refreshCounts(context)
        SpikeLog.log("Radio: now using ${radio.name} (${radio.address})")
        load(context)
    }

    /** Adds [radio] to the list without using it yet, so a confirmation screen can name it. */
    fun remember(context: Context, radio: KnownRadio) {
        val prefs = Prefs.of(context)
        val known = KnownRadios.add(KnownRadios.parse(prefs.getString(Prefs.RADIOS, null)), radio)
        prefs.edit().putString(Prefs.RADIOS, KnownRadios.format(known)).apply()
        load(context)
    }

    /** Removes [address] from the list; if it was in use, deletes its messages and leaves no radio chosen. */
    fun forget(context: Context, address: String) {
        val prefs = Prefs.of(context)
        val known = KnownRadios.remove(KnownRadios.parse(prefs.getString(Prefs.RADIOS, null)), address)
        val edit = prefs.edit().putString(Prefs.RADIOS, KnownRadios.format(known))
        if (prefs.getString(Prefs.RADIO_ADDRESS, null) == address) {
            edit.remove(Prefs.RADIO_ADDRESS).remove(Prefs.RADIO_NAME).remove(Prefs.SELF_NAME).remove(Prefs.LAST_SYNC_AT)
            SyncStatus.reset()
        }
        edit.apply()
        val store = Storage.store(context)
        Storage.later { store.forget(address) }
        Storage.refreshCounts(context)
        SpikeLog.log("Radio: forgot $address")
        load(context)
    }
}
