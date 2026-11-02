package org.coloradomesh.wearmesh.glance

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import org.coloradomesh.meshcore.protocol.RadioBattery
import org.coloradomesh.wearmesh.CheckIn
import org.coloradomesh.wearmesh.Mode
import org.coloradomesh.wearmesh.Prefs
import org.coloradomesh.wearmesh.SpikeLog
import org.coloradomesh.wearmesh.SpikeState
import org.coloradomesh.wearmesh.data.MessageRow
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
        val lasts = dao.lastMessages(radio)
        val unread = Glance.unread(lasts, channels, choices)
        fun message(row: MessageRow) =
            GlanceMessage(row.conversation, Notifier.titleFor(context, radio, row.conversation), row.sender, row.body, unread = !row.read)
        // As many as a large watch shows; a smaller one shows the first Glance.TILE_MESSAGES.
        val heard = Glance.heard(dao.newestReceived(radio, Glance.TILE_MESSAGES_LARGE), Glance.TILE_MESSAGES_LARGE).map(::message)
        // Enough rows that, with quiet channels left out, one that counts is almost always among them.
        val replyTo = Glance.newest(dao.newestUnread(radio, NEWEST_ROWS), channels, choices, limit = 1).firstOrNull()?.let(::message)
        val chosen = complicationChannel(context, radio)
        val channel = channels.firstOrNull { "c:${it.slot}" == chosen }?.let { ChannelCount(it.name, Glance.unreadIn(lasts, "c:${it.slot}"), open = Glance.isOpen(it.kind)) }
        return Glance(unread, lastSyncAt, live, heard, replyTo, channel)
    }

    /** The channel the complication counts for [radio], such as "c:3", or null for all notifying conversations. */
    fun complicationChannel(context: Context, radio: String): String? =
        Prefs.of(context).getString(Prefs.COMPLICATION_CHANNEL + radio, null)

    /** Sets what the complication counts, and shows it at once. */
    fun setComplicationChannel(context: Context, radio: String, conversation: String?) {
        val edit = Prefs.of(context).edit()
        if (conversation == null) edit.remove(Prefs.COMPLICATION_CHANNEL + radio) else edit.putString(Prefs.COMPLICATION_CHANNEL + radio, conversation)
        edit.apply()
        SpikeLog.log("Complication: counts ${conversation ?: "all notifying conversations"}")
        refresh(context)
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
        requestTile(app, WearMeshTileService::class.java)
        requestTile(app, WearMeshStatusTileService::class.java)
    }

    private fun requestTile(app: Context, tile: Class<out TileService>) {
        try {
            TileService.getUpdater(app).requestUpdate(tile)
        } catch (e: RuntimeException) {
            SpikeLog.log("Glance: could not ask for a Tile update (${e.message})")
        }
    }

    /** What the Status tile shows, from what the app has saved. Reads settings only. */
    fun status(context: Context): Status {
        val prefs = Prefs.of(context)
        val state = SpikeState.state.value
        return Status(
            mode = when {
                Mode.hasQuit(context) -> Status.Mode.QUIT
                Mode.get(context) == Mode.Kind.CHECK_IN -> Status.Mode.CHECK_IN
                else -> Status.Mode.LIVE
            },
            intervalMinutes = CheckIn.intervalMinutes(context),
            liveLink = state.takeIf { it.running }?.link,
            // The radio's node name, from its greeting.
            radioName = prefs.getString(Prefs.SELF_NAME, null),
            lastSyncAt = prefs.getLong(Prefs.LAST_SYNC_AT, 0),
            lastProblem = prefs.getString(Prefs.LAST_PROBLEM, null),
            nextCheckInAt = prefs.getLong(Prefs.NEXT_CHECK_IN_AT, 0),
            radioMillivolts = prefs.getInt(Prefs.RADIO_MILLIVOLTS, 0),
            radioMillivoltsAt = prefs.getLong(Prefs.RADIO_MILLIVOLTS_AT, 0),
        )
    }

    /**
     * Keeps the radio's battery reading for the Status tile, and redraws that tile when the
     * figure it shows changes (or the last reading had grown too old to show). Live reads the
     * battery every few minutes and the figure moves rarely, so redraws are rare. Cheap enough
     * for the protocol's callback: a settings write that does not wait for the disk, and a
     * request Wear OS handles later.
     */
    fun radioBattery(context: Context, millivolts: Int) {
        if (millivolts <= 0) return
        val prefs = Prefs.of(context)
        val now = System.currentTimeMillis()
        val before = prefs.getLong(Prefs.RADIO_MILLIVOLTS_AT, 0)
            .takeIf { now - it in 0 until Status.BATTERY_FRESH_MS }
            ?.let { RadioBattery.percent(prefs.getInt(Prefs.RADIO_MILLIVOLTS, 0)) }
        prefs.edit()
            .putInt(Prefs.RADIO_MILLIVOLTS, millivolts)
            .putLong(Prefs.RADIO_MILLIVOLTS_AT, now)
            .apply()
        if (RadioBattery.percent(millivolts) != before) requestTile(context.applicationContext, WearMeshStatusTileService::class.java)
    }

    /**
     * Forgets what the Status tile shows of the radio in use (its battery and a failed try), as
     * when another radio is chosen or it is forgotten, so none of it shows under another radio.
     */
    fun forgetRadioStatus(edit: SharedPreferences.Editor): SharedPreferences.Editor =
        edit.remove(Prefs.RADIO_MILLIVOLTS).remove(Prefs.RADIO_MILLIVOLTS_AT).remove(Prefs.LAST_PROBLEM)

    private const val NEWEST_ROWS = 50
}
