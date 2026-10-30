package org.coloradomesh.wearmesh.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import org.coloradomesh.wearmesh.Outbox
import org.coloradomesh.wearmesh.Prefs
import org.coloradomesh.wearmesh.SpikeLog
import org.coloradomesh.wearmesh.data.Storage
import org.coloradomesh.wearmesh.ui.Drafts

/**
 * The buttons on message notifications (M3 step 3): Reply, Mark read, and Retry on a failure.
 *
 * A reply goes to the conversation, not to one person, so on a channel it carries no `@[name]`;
 * tapping a message in the app does that. A reply over the byte limit is not sent, and says so in
 * a notification of its own, since a notification has no room to show and edit it. Replying marks
 * the conversation read and takes its notification down.
 *
 * Sending starts the Live link's send or a Check-in connection, which Android allows from here:
 * a tap on a notification lets the app start a foreground service for a short while. The storage
 * writes each action queues finish before the broadcast does.
 */
class MessageActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SpikeLog.init(context)
        val radio = intent.getStringExtra(Notifier.EXTRA_RADIO) ?: return
        if (radio != Prefs.of(context).getString(Prefs.RADIO_ADDRESS, null)) {
            SpikeLog.log("Notification action for a radio no longer in use; ignored")
            return
        }
        // Each action queues writes on the storage thread. Holding the broadcast open until they are
        // done keeps Android from ending the process first, when nothing else of the app is running.
        val pending = goAsync()
        try {
            when (intent.action) {
                ACTION_REPLY -> reply(context, radio, intent)
                ACTION_MARK_READ -> intent.getStringExtra(Notifier.EXTRA_CONVERSATION)?.let { markRead(context, radio, it) }
                ACTION_RETRY -> {
                    val id = intent.getLongExtra(Notifier.EXTRA_MESSAGE_ID, -1)
                    if (id >= 0) {
                        SpikeLog.log("Notification: Retry")
                        // Takes the notification down once the message is waiting again.
                        Outbox.retry(context, id)
                    }
                }
            }
        } finally {
            // Queued after everything above, on the same single thread, so it runs last.
            Storage.later { pending.finish() }
        }
    }

    private fun reply(context: Context, radio: String, intent: Intent) {
        val conversation = intent.getStringExtra(Notifier.EXTRA_CONVERSATION) ?: return
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(Notifier.KEY_REPLY)?.toString()
        val body = Drafts.body(text.orEmpty(), to = null)
        markRead(context, radio, conversation)
        val problem = Drafts.problem(conversation, body, Prefs.of(context).getString(Prefs.SELF_NAME, null))
        if (problem != null) {
            SpikeLog.log("Notification reply not sent: $problem")
            if (body.isNotBlank()) Storage.later { Notifier.notSent(context.applicationContext, radio, conversation, body, problem) }
            return
        }
        SpikeLog.log("Notification: reply in $conversation")
        Outbox.send(context, conversation, body)
    }

    private fun markRead(context: Context, radio: String, conversation: String) {
        val dao = Storage.dao(context)
        Storage.later { dao.markRead(radio, conversation) }
        Notifier.dismiss(context, radio, conversation)
    }

    companion object {
        const val ACTION_REPLY = "org.coloradomesh.wearmesh.REPLY"
        const val ACTION_MARK_READ = "org.coloradomesh.wearmesh.MARK_READ"
        const val ACTION_RETRY = "org.coloradomesh.wearmesh.RETRY"
    }
}
