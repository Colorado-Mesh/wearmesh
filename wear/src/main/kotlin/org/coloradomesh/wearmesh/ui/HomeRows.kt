package org.coloradomesh.wearmesh.ui

import org.coloradomesh.wearmesh.data.ChannelRow
import org.coloradomesh.wearmesh.data.ConversationLast
import org.coloradomesh.wearmesh.data.Conversations

/** One row on Home. */
data class HomeRow(
    val conversation: String,
    val title: String,
    val direct: Boolean,
    val unread: Int,
    /** The newest message, or null when the conversation has none yet. */
    val last: ConversationLast?,
)

/**
 * Home's order, decided for M2 step 3: direct messages first, newest first, then every
 * programmed channel in the radio's slot order, with or without messages, so each keeps its
 * place. Messages on a slot with no stored channel (read before the channel list was) come last.
 */
object HomeRows {
    private const val DIRECT = "d:"

    fun build(channels: List<ChannelRow>, lasts: List<ConversationLast>): List<HomeRow> {
        val byConversation = lasts.associateBy { it.conversation }
        val directs = lasts.filter { it.conversation.startsWith(DIRECT) }
            .sortedByDescending { it.receivedAt }
            .map { HomeRow(it.conversation, directTitle(it), direct = true, unread = it.unread, last = it) }
        val slots = channels.sortedBy { it.slot }.map { channel ->
            val key = Conversations.channel(channel.slot)
            val last = byConversation[key]
            HomeRow(key, channel.name, direct = false, unread = last?.unread ?: 0, last = last)
        }
        val known = slots.map { it.conversation }.toSet()
        val orphans = lasts.filter { !it.conversation.startsWith(DIRECT) && it.conversation !in known }
            .sortedBy { it.conversation }
            .map { HomeRow(it.conversation, "Channel ${it.conversation.removePrefix("c:")}", direct = false, unread = it.unread, last = it) }
        return directs + slots + orphans
    }

    /** The contact's name, or the 12-character key prefix when the contact is not stored. */
    fun directTitle(last: ConversationLast): String = last.contactName?.takeIf { it.isNotBlank() } ?: last.conversation.removePrefix(DIRECT)

    /** The status line's words: Live's state, a sync under way or failed, or when the last one was. */
    fun status(liveLink: String?, syncing: Boolean, problem: String?, lastSyncAt: Long, now: Long): String = when {
        liveLink != null -> "Live: $liveLink"
        syncing -> "Syncing…"
        problem != null -> problem
        lastSyncAt > 0 -> {
            val age = TimeAgo.format(now, lastSyncAt)
            when {
                age == "now" -> "Synced just now"
                age.endsWith(" min") || age.endsWith(" h") -> "Synced $age ago"
                else -> "Last synced $age"
            }
        }
        else -> "Not synced yet"
    }

    /** "Ann: hello", "You: hello" for a message the watch sent, or the text alone when there is no sender (a direct message). */
    fun preview(last: ConversationLast): String = when {
        last.outgoing -> "You: ${last.body}"
        last.sender.isEmpty() -> last.body
        else -> "${last.sender}: ${last.body}"
    }
}
