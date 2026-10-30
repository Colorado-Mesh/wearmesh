package org.coloradomesh.wearmesh.glance

import org.coloradomesh.wearmesh.data.ChannelRow
import org.coloradomesh.wearmesh.data.ConversationLast
import org.coloradomesh.wearmesh.notify.NotifyRules

/**
 * What the complication and the Tile show (M4), read from the store: unread messages in every
 * conversation whose notifications are on (decided for M4, so the count matches what buzzes),
 * and when the last sync was.
 */
data class Glance(
    val unread: Int,
    /** When the last sync with the radio finished, by the watch's clock; 0 if none yet. */
    val lastSyncAt: Long,
    /** Live's link state while Live runs, such as "Connected"; null in Check-in. */
    val liveLink: String?,
) {
    companion object {
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
