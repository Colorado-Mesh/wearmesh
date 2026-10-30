package org.coloradomesh.wearmesh

import android.content.Context
import org.coloradomesh.wearmesh.data.Storage

/**
 * Where the screens send from (M3). A message is stored in its conversation as waiting, then
 * sent: over Live's link when Live is running, or in Check-in mode over a connection opened at
 * once (decided for M3). Each connection to the radio also sends whatever is still waiting, so
 * a message written while the radio was out of reach goes with the next one, unless that is more
 * than half an hour later (see [org.coloradomesh.wearmesh.data.OutboxSender]). What fits in a
 * message is [org.coloradomesh.wearmesh.ui.Drafts].
 */
object Outbox {
    /** Stores [body] to send in [conversation] on the radio in use, and starts sending it. Call on the main thread. */
    fun send(context: Context, conversation: String, body: String) {
        val app = context.applicationContext
        val prefs = Prefs.of(app)
        val radio = prefs.getString(Prefs.RADIO_ADDRESS, null)
        if (radio == null) {
            SpikeLog.log("Outbox: no radio chosen; not sent")
            return
        }
        val self = prefs.getString(Prefs.SELF_NAME, null).orEmpty()
        val store = Storage.store(app, radio)
        val at = System.currentTimeMillis()
        Storage.later {
            if (store.addOutgoing(conversation, self, body, at) == -1L) {
                SpikeLog.log("Outbox: the same text was just written in $conversation; not sent twice")
            }
        }
        startSending(app)
    }

    /** Sends the failed message [id] again, as if just written. Call on the main thread. */
    fun retry(context: Context, id: Long) {
        val app = context.applicationContext
        val radio = Prefs.of(app).getString(Prefs.RADIO_ADDRESS, null) ?: return
        val store = Storage.store(app, radio)
        val at = System.currentTimeMillis()
        Storage.later {
            if (!store.retryOutgoing(id, at)) SpikeLog.log("Outbox: that message is no longer failed; not sent again")
        }
        startSending(app)
    }

    /** Each way reads the outbox on the storage thread, after anything queued before it is written. */
    private fun startSending(app: Context) {
        try {
            if (SpikeState.state.value.running) SpikeService.sendPending(app) else CheckIn.send(app)
        } catch (e: IllegalStateException) {
            SpikeLog.log("Outbox: could not start sending (${e.message}); it goes with the next connection")
        }
    }
}
