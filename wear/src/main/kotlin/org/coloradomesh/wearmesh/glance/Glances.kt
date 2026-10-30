package org.coloradomesh.wearmesh.glance

import android.content.ComponentName
import android.content.Context
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import org.coloradomesh.wearmesh.Prefs
import org.coloradomesh.wearmesh.SpikeLog
import org.coloradomesh.wearmesh.SpikeState
import org.coloradomesh.wearmesh.data.Storage
import org.coloradomesh.wearmesh.notify.Notifier

/**
 * Reads the [Glance] and tells the complication and the Tile to show it again. They never wake the radio: they show what the last sync stored, and are refreshed after
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
        val channels = dao.channels(radio)
        val choices = Notifier.readChoices(context, radio)
        val unread = Glance.unread(dao.lastMessages(radio), channels, choices)
        // Enough rows that, with quiet channels left out, two that count are almost always among them.
        val newest = Glance.newest(dao.newestUnread(radio, NEWEST_ROWS), channels, choices).map {
            GlanceMessage(it.conversation, Notifier.titleFor(context, radio, it.conversation), it.sender, it.body)
        }
        return Glance(unread, lastSyncAt, live, newest)
    }

    /** Asks Wear OS to show the glance again on both; cheap, and safe to call after every sync or read. */
    fun refresh(context: Context) {
        val app = context.applicationContext
        try {
            ComplicationDataSourceUpdateRequester
                .create(app, ComponentName(app, WearMeshComplicationService::class.java))
                .requestUpdateAll()
        } catch (e: RuntimeException) {
            SpikeLog.log("Glance: could not ask for a complication update (${e.message})")
        }
        try {
            TileService.getUpdater(app).requestUpdate(WearMeshTileService::class.java)
        } catch (e: RuntimeException) {
            SpikeLog.log("Glance: could not ask for a Tile update (${e.message})")
        }
    }

    private const val NEWEST_ROWS = 50
}
