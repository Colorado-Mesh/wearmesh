package org.coloradomesh.wearmesh.glance

import android.content.ComponentName
import android.content.Context
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import org.coloradomesh.wearmesh.Prefs
import org.coloradomesh.wearmesh.SpikeLog
import org.coloradomesh.wearmesh.SpikeState
import org.coloradomesh.wearmesh.data.Storage
import org.coloradomesh.wearmesh.notify.Notifier

/**
 * Reads the [Glance] and tells the complication (and, from M4 step 2, the Tile) to show it
 * again. They never wake the radio: they show what the last sync stored, and are refreshed after
 * each sync and whenever messages are read.
 */
object Glances {
    /** The glance for the radio in use. Call on the storage thread: it reads the store. */
    fun read(context: Context): Glance {
        val prefs = Prefs.of(context)
        val radio = prefs.getString(Prefs.RADIO_ADDRESS, null)
        val live = SpikeState.state.value.takeIf { it.running }?.link
        val lastSyncAt = prefs.getLong(Prefs.LAST_SYNC_AT, 0)
        if (radio == null) return Glance(0, lastSyncAt, live)
        val dao = Storage.dao(context)
        val unread = Glance.unread(dao.lastMessages(radio), dao.channels(radio), Notifier.readChoices(context, radio))
        return Glance(unread, lastSyncAt, live)
    }

    /** Asks Wear OS to show the glance again; cheap, and safe to call after every sync or read. */
    fun refresh(context: Context) {
        try {
            ComplicationDataSourceUpdateRequester
                .create(context.applicationContext, ComponentName(context.applicationContext, WearMeshComplicationService::class.java))
                .requestUpdateAll()
        } catch (e: RuntimeException) {
            SpikeLog.log("Glance: could not ask for an update (${e.message})")
        }
    }
}
