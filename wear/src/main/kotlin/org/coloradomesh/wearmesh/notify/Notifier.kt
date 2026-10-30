package org.coloradomesh.wearmesh.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.coloradomesh.wearmesh.MainActivity
import org.coloradomesh.wearmesh.Prefs
import org.coloradomesh.wearmesh.R
import org.coloradomesh.wearmesh.SpikeLog
import org.coloradomesh.wearmesh.data.IncomingMessage
import org.coloradomesh.wearmesh.data.OutgoingMessage
import org.coloradomesh.wearmesh.data.OutgoingStatus
import org.coloradomesh.wearmesh.data.Storage
import org.coloradomesh.wearmesh.ui.QuickReplies

/**
 * Message notifications (M2 step 4): one per conversation, showing its newest unread messages,
 * grouped under one summary that alone buzzes. Each has Reply (voice, keyboard and emoji, as the
 * watch shows it), Quick reply (the app's quick replies, which the watch does not show in Reply)
 * and Mark read (M3 step 3, decided for M3: Reply on every
 * message notification). A message of the watch's that fails gets its own notification, with
 * Retry ([failed]). Which messages notify is [NotifyRules]; how often
 * a conversation buzzes is [AlertGate]. Direct messages and channels have their own Android
 * notification channels, so the watch's settings can give them different sounds and vibration.
 */
object Notifier {
    const val CHANNEL_DIRECT = "direct_messages"
    const val CHANNEL_CHANNELS = "channels"
    const val EXTRA_RADIO = "org.coloradomesh.wearmesh.RADIO"
    const val EXTRA_CONVERSATION = "org.coloradomesh.wearmesh.CONVERSATION"
    const val EXTRA_TITLE = "org.coloradomesh.wearmesh.TITLE"
    const val EXTRA_MESSAGE_ID = "org.coloradomesh.wearmesh.MESSAGE_ID"

    /** With the conversation extras: open its quick replies, not just the conversation. */
    const val EXTRA_QUICK_REPLY = "org.coloradomesh.wearmesh.QUICK_REPLY"

    /** The reply typed, spoken or chosen in a notification's Reply. */
    const val KEY_REPLY = "reply"

    private const val GROUP = "org.coloradomesh.wearmesh.MESSAGES"

    /** Not the Live (1) or Check-in (2) service notifications. */
    private const val SUMMARY_ID = 3
    private const val SHOWN_MESSAGES = 5

    private val gate = AlertGate()

    /** The conversation on screen, as "radio|conversation"; it never notifies. */
    @Volatile
    private var onScreen: String? = null

    private val choiceState = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    /** The radio in use's notification choices, as last loaded or changed. */
    val choices: StateFlow<Map<String, Boolean>> = choiceState

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_DIRECT, context.getString(R.string.channel_direct), NotificationManager.IMPORTANCE_HIGH),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_CHANNELS, context.getString(R.string.channel_channels), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    fun loadChoices(context: Context, radio: String?) {
        choiceState.value = if (radio == null) emptyMap() else readChoices(context, radio)
    }

    fun setChoice(context: Context, radio: String, conversation: String, kind: NotifyRules.Kind, on: Boolean) {
        val next = NotifyRules.choose(readChoices(context, radio), conversation, kind, on)
        Prefs.of(context).edit().putString(Prefs.NOTIFY_CHOICES + radio, NotifyRules.formatChoices(next)).apply()
        choiceState.value = next
    }

    /** Forgets a radio's choices, as when the radio is forgotten. */
    fun clearChoices(context: Context, radio: String) {
        Prefs.of(context).edit().remove(Prefs.NOTIFY_CHOICES + radio).apply()
    }

    /** A radio's notification choices as saved; also for the glance (M4). */
    fun readChoices(context: Context, radio: String) =
        NotifyRules.parseChoices(Prefs.of(context).getString(Prefs.NOTIFY_CHOICES + radio, null))

    /** The conversation now on screen, or null when none is; opening one also clears its notification. */
    fun onScreen(context: Context, radio: String?, conversation: String?) {
        onScreen = if (radio != null && conversation != null) "$radio|$conversation" else null
        if (radio != null && conversation != null) {
            gate.opened(conversation)
            cancel(context, radio, conversation)
        }
    }

    /**
     * Notifies for [messages] just stored from [radio], unless they are the [backlog] of its first
     * sync after being chosen. Call on the storage thread: it reads the store.
     */
    fun afterSync(context: Context, radio: String, messages: List<IncomingMessage>, backlog: Boolean) {
        if (messages.isEmpty()) return
        if (backlog) {
            SpikeLog.log("Notifications: none for the ${messages.size} messages waiting on a radio just chosen")
            return
        }
        if (!allowed(context)) {
            SpikeLog.log("Notifications: off for WearMesh in the watch's settings; ${messages.size} new messages not notified")
            return
        }
        val dao = Storage.dao(context)
        val channels = dao.channels(radio).associateBy { it.slot }
        val choices = readChoices(context, radio)
        val self = Prefs.of(context).getString(Prefs.SELF_NAME, null)
        val now = System.currentTimeMillis()
        var buzz = false
        var posted = 0
        var anyDirect = false
        // For the log: each conversation notified, marked when only a mention of this radio made it.
        val shown = ArrayList<String>()
        for ((conversation, arrived) in messages.groupBy { it.conversation }) {
            val channel = channels[conversation.removePrefix("c:").toIntOrNull() ?: -1]?.takeIf { conversation.startsWith("c:") }
            val kind = NotifyRules.kindOf(conversation, channel?.kind)
            val on = NotifyRules.isOn(conversation, kind, choices)
            val open = onScreen == "$radio|$conversation"
            if (arrived.none { NotifyRules.shouldNotify(on, it.body, self, open, backlog = false) }) continue
            val unread = dao.unreadMessages(radio, conversation, SHOWN_MESSAGES).reversed()
            if (unread.isEmpty()) continue
            val title = titleFor(context, radio, conversation)
            val style = NotificationCompat.MessagingStyle(Person.Builder().setName("You").build())
                .setConversationTitle(title)
                .setGroupConversation(kind != NotifyRules.Kind.DIRECT)
            for (message in unread) {
                val sender = if (message.sender.isNotEmpty()) message.sender else title
                style.addMessage(message.body, message.receivedAt, Person.Builder().setName(sender).build())
            }
            val notification = NotificationCompat.Builder(context, if (kind == NotifyRules.Kind.DIRECT) CHANNEL_DIRECT else CHANNEL_CHANNELS)
                .setSmallIcon(R.drawable.ic_notification)
                .setStyle(style)
                .setContentTitle(title)
                .setContentText(unread.last().let { if (it.sender.isEmpty()) it.body else "${it.sender}: ${it.body}" })
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setGroup(GROUP)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
                .setAutoCancel(true)
                .setContentIntent(openIntent(context, radio, conversation, title))
                .addAction(replyAction(context, radio, conversation, title))
                .addAction(quickReplyAction(context, radio, conversation, title))
                .addAction(markReadAction(context, radio, conversation))
                .build()
            if (gate.shouldAlert(conversation, now)) buzz = true
            if (kind == NotifyRules.Kind.DIRECT) anyDirect = true
            notify(context, idFor(radio, conversation), notification)
            posted++
            shown.add(if (on) title else "$title, mention")
        }
        if (posted == 0) return
        SpikeLog.log("Notified: ${shown.joinToString("; ")}; " + if (buzz) "buzzed" else "silent, as these buzzed within 5 min")
        // The summary alone buzzes, so it takes the direct-message channel's sound when one came.
        val summary = NotificationCompat.Builder(context, if (anyDirect) CHANNEL_DIRECT else CHANNEL_CHANNELS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText("New messages")
            .setGroup(GROUP)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
            .setSilent(!buzz)
            .setAutoCancel(true)
            .setContentIntent(homeIntent(context))
            .build()
        notify(context, SUMMARY_ID, summary)
    }

    /**
     * A conversation's name for a notification: the channel's, the contact's, or the key prefix.
     * Call on the storage thread: it reads the store.
     */
    fun titleFor(context: Context, radio: String, conversation: String): String {
        val dao = Storage.dao(context)
        return when {
            conversation.startsWith("d:") -> dao.contactName(radio, conversation.removePrefix("d:")) ?: conversation.removePrefix("d:")
            else -> {
                val slot = conversation.removePrefix("c:").toIntOrNull()
                dao.channels(radio).firstOrNull { it.slot == slot }?.name ?: "Channel ${conversation.removePrefix("c:")}"
            }
        }
    }

    /**
     * A message of the watch's that failed (decided for M3: a failure notifies), with Retry when
     * it can be tried again. Call on the storage thread.
     */
    fun failed(context: Context, radio: String, message: OutgoingMessage, status: OutgoingStatus, why: String?) {
        notSent(context, radio, message.conversation, message.body, reason(status, why), retryId = message.id)
    }

    /**
     * A reply from a notification that could not be sent, such as one over the byte limit, or one
     * that failed ([retryId] set). Call on the storage thread.
     */
    fun notSent(context: Context, radio: String, conversation: String, body: String, reason: String, retryId: Long? = null) {
        if (!allowed(context)) return
        val title = titleFor(context, radio, conversation)
        // One per failed message, or per rejected reply's text, so a second does not replace the first.
        val id = idFor(radio, if (retryId != null) "failed|$retryId" else "rejected|$conversation|$body")
        val builder = NotificationCompat.Builder(context, CHANNEL_DIRECT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Not sent to $title")
            .setContentText(reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$reason\n\n$body"))
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(true)
            .setContentIntent(openIntent(context, radio, conversation, title))
        if (retryId != null) {
            val retry = Intent(context, MessageActionReceiver::class.java)
                .setAction(MessageActionReceiver.ACTION_RETRY)
                .putExtra(EXTRA_RADIO, radio)
                .putExtra(EXTRA_MESSAGE_ID, retryId)
            val pending = PendingIntent.getBroadcast(context, id, retry, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            builder.addAction(NotificationCompat.Action.Builder(R.drawable.ic_notification, "Retry", pending).build())
        }
        notify(context, id, builder.build())
        SpikeLog.log("Notified: not sent to $title ($reason)")
    }

    private fun reason(status: OutgoingStatus, why: String?): String = when (status) {
        OutgoingStatus.NO_CONTACT -> "Your radio does not have this contact"
        else -> why?.replaceFirstChar { it.uppercase() } ?: "The radio would not send it"
    }

    /** Takes a conversation's notification down, as when it is replied to or marked read from it. */
    fun dismiss(context: Context, radio: String, conversation: String) = cancel(context, radio, conversation)

    /** Takes down the failure notification for the message [id], once it is waiting to send again. */
    fun dismissFailure(context: Context, radio: String, id: Long) =
        context.getSystemService(NotificationManager::class.java).cancel(idFor(radio, "failed|$id"))

    private fun replyAction(context: Context, radio: String, conversation: String, title: String): NotificationCompat.Action {
        val intent = Intent(context, MessageActionReceiver::class.java)
            .setAction(MessageActionReceiver.ACTION_REPLY)
            .putExtra(EXTRA_RADIO, radio)
            .putExtra(EXTRA_CONVERSATION, conversation)
        // Mutable: the system adds the reply to it.
        val pending = PendingIntent.getBroadcast(
            context,
            idFor(radio, "reply|$conversation"),
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val replies = QuickReplies.parse(Prefs.of(context).getString(Prefs.QUICK_REPLIES, null))
        val input = RemoteInput.Builder(KEY_REPLY)
            .setLabel("Reply to $title")
            .setChoices(replies.toTypedArray<CharSequence>())
            .setAllowFreeFormInput(true)
            .build()
        return NotificationCompat.Action.Builder(R.drawable.ic_notification, "Reply", pending)
            .addRemoteInput(input)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .build()
    }

    /**
     * Opens the app on this conversation's quick replies, one tap from sending. The Pixel Watch
     * shows a reply action as emoji, voice and keyboard only, without the quick replies given to
     * it as choices (found in the M3 field test), so this is how they are reached from a
     * notification.
     */
    private fun quickReplyAction(context: Context, radio: String, conversation: String, title: String): NotificationCompat.Action {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_RADIO, radio)
            .putExtra(EXTRA_CONVERSATION, conversation)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_QUICK_REPLY, true)
        val pending = PendingIntent.getActivity(
            context,
            idFor(radio, "quick|$conversation"),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Action.Builder(R.drawable.ic_notification, "Quick reply", pending)
            .setShowsUserInterface(true)
            .build()
    }

    private fun markReadAction(context: Context, radio: String, conversation: String): NotificationCompat.Action {
        val intent = Intent(context, MessageActionReceiver::class.java)
            .setAction(MessageActionReceiver.ACTION_MARK_READ)
            .putExtra(EXTRA_RADIO, radio)
            .putExtra(EXTRA_CONVERSATION, conversation)
        val pending = PendingIntent.getBroadcast(
            context,
            idFor(radio, "read|$conversation"),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Action.Builder(R.drawable.ic_notification, "Mark read", pending)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()
    }

    private fun allowed(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Whether the watch lets WearMesh show notifications; Settings says so when it does not. */
    fun enabled(context: Context): Boolean = allowed(context)

    private fun notify(context: Context, id: Int, notification: android.app.Notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            SpikeLog.log("Notification not shown: ${e.message}")
        }
    }

    private fun cancel(context: Context, radio: String, conversation: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val id = idFor(radio, conversation)
        manager.cancel(id)
        // The summary goes with the last conversation notification. Cancelling is not immediate,
        // so the one just cancelled may still be listed.
        if (manager.activeNotifications.none { it.notification.group == GROUP && it.id != SUMMARY_ID && it.id != id }) {
            manager.cancel(SUMMARY_ID)
        }
    }

    private fun openIntent(context: Context, radio: String, conversation: String, title: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_RADIO, radio)
            .putExtra(EXTRA_CONVERSATION, conversation)
            .putExtra(EXTRA_TITLE, title)
        return PendingIntent.getActivity(
            context,
            idFor(radio, conversation),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** Opens the app on Home, for the summary. */
    private fun homeIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        SUMMARY_ID,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** One id per radio and conversation, clear of the service and summary ids. */
    private fun idFor(radio: String, conversation: String): Int {
        val id = "$radio|$conversation".hashCode()
        return if (id in 0..SUMMARY_ID) id + 1000 else id
    }
}
