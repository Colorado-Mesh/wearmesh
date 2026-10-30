package org.coloradomesh.wearmesh.glance

import org.coloradomesh.wearmesh.data.ChannelRow
import org.coloradomesh.wearmesh.data.ConversationLast
import org.coloradomesh.wearmesh.data.MessageRow
import org.coloradomesh.wearmesh.notify.NotifyRules

/**
 * What the complication and the Tile show (M4), read from the store: unread messages in every
 * conversation whose notifications are on (decided for M4, so the count matches what buzzes),
 * when the last sync was, and (for the Tile) the newest of those unread messages.
 */
data class Glance(
    val unread: Int,
    /** When the last sync with the radio finished, by the watch's clock; 0 if none yet. */
    val lastSyncAt: Long,
    /** Live's link state while Live runs, such as "Connected"; null in Check-in. */
    val liveLink: String?,
    /** The newest unread messages that count, newest first; at most [TILE_MESSAGES]. */
    val newest: List<GlanceMessage> = emptyList(),
) {
    companion object {
        /** How many messages the Tile shows (decided for M4). */
        const val TILE_MESSAGES = 2

        /** Of [rows], unread and newest first, those in conversations that count, as [unread] counts them. */
        fun newest(rows: List<MessageRow>, channels: List<ChannelRow>, choices: Map<String, Boolean>, limit: Int = TILE_MESSAGES): List<MessageRow> {
            val kinds = channels.associate { "c:${it.slot}" to it.kind }
            return rows.filter { row ->
                !row.read && !row.outgoing &&
                    NotifyRules.isOn(row.conversation, NotifyRules.kindOf(row.conversation, kinds[row.conversation]), choices)
            }.sortedByDescending { it.id }.take(limit)
        }

        fun unread(lasts: List<ConversationLast>, channels: List<ChannelRow>, choices: Map<String, Boolean>): Int {
            val kinds = channels.associate { "c:${it.slot}" to it.kind }
            return lasts.filter { last ->
                val kind = NotifyRules.kindOf(last.conversation, kinds[last.conversation])
                NotifyRules.isOn(last.conversation, kind, choices)
            }.sumOf { it.unread }
        }

        /** The short text: the count, or nothing (the icon alone) when nothing is unread (decided for M4). */
        fun countText(unread: Int): String = when {
            unread <= 0 -> ""
            unread > 99 -> "99+"
            else -> unread.toString()
        }
    }
}

/** One unread message on the Tile. [title] is the conversation's: a contact's or channel's name. */
data class GlanceMessage(
    val conversation: String,
    val title: String,
    val sender: String,
    val body: String,
) {
    /** Who it is from: the contact in a direct conversation, or "Ann · Public" on a channel. */
    val from: String
        get() = if (conversation.startsWith("d:") || sender.isBlank()) title else "$sender · $title"
}
