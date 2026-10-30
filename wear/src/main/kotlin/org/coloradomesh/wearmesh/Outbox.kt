package org.coloradomesh.wearmesh

import android.content.Context
import org.coloradomesh.meshcore.protocol.CompanionFrames
import org.coloradomesh.wearmesh.data.Storage

/**
 * Where the screens send from (M3). A message is stored in its conversation as waiting, then
 * sent: over Live's link when Live is running, or in Check-in mode over a connection opened at
 * once (decided for M3). Each connection to the radio also sends whatever is still waiting, so
 * a message written while the radio was out of reach goes with the next one, unless that is more
 * than half an hour later (see [org.coloradomesh.wearmesh.data.OutboxSender]).
 */
object Outbox {
    /** Why a message cannot be sent, or null when it can: for the screens. */
    fun problem(conversation: String, body: String, selfName: String?): String? {
        val over = body.toByteArray(Charsets.UTF_8).size - limit(conversation, selfName)
        return when {
            body.isBlank() -> "Nothing to send"
            over > 0 -> "Too long by $over bytes"
            else -> null
        }
    }

    /**
     * The most bytes a message in [conversation] can take. On a channel the radio puts its node
     * name and ": " in front, inside the same limit.
     */
    fun limit(conversation: String, selfName: String? = null): Int =
        if (conversation.startsWith("d:")) {
            CompanionFrames.MAX_DIRECT_TEXT_BYTES
        } else {
            CompanionFrames.MAX_CHANNEL_TEXT_BYTES - (selfName?.toByteArray(Charsets.UTF_8)?.size ?: 0) - 2
        }

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
        // Each reads the outbox on the storage thread, after the message above is written.
        try {
            if (SpikeState.state.value.running) SpikeService.sendPending(app) else CheckIn.send(app)
        } catch (e: IllegalStateException) {
            SpikeLog.log("Outbox: could not start sending (${e.message}); it goes with the next connection")
        }
    }
}
