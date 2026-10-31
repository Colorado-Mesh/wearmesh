package org.coloradomesh.wearmesh.glance

import org.coloradomesh.wearmesh.data.ChannelRow
import org.coloradomesh.wearmesh.data.ConversationLast
import org.coloradomesh.wearmesh.data.MessageRow
import org.coloradomesh.wearmesh.notify.NotifyRules

/**
 * What the complication and the Tile show (M4), read from the store: unread messages in every
 * conversation whose notifications are on (decided for M4, so the count matches what buzzes),
 * when the last sync was, the last two messages heard (for the Tile), and the unread count in one
 * chosen channel (for the complication, when Settings › Complication names one).
 */
data class Glance(
    val unread: Int,
    /** When the last sync with the radio finished, by the watch's clock; 0 if none yet. */
    val lastSyncAt: Long,
    /** Live's link state while Live runs, such as "Connected"; null in Check-in. */
    val liveLink: String?,
    /**
     * The newest messages heard, in any conversation, read or not, newest first; at most
     * [TILE_MESSAGES] (decided 31 October 2026, replacing the newest unread ones).
     */
    val heard: List<GlanceMessage> = emptyList(),
    /** The newest unread message that counts, as [unread] counts: the Tile's Quick reply goes to its conversation. */
    val replyTo: GlanceMessage? = null,
    /** The channel Settings › Complication names, with its unread count; null for all notifying conversations. */
    val channel: ChannelCount? = null,
) {
    companion object {
        /** How many messages the Tile shows (decided for M4). */
        const val TILE_MESSAGES = 2

        /** Of [rows], the messages received (not sent by the watch), newest first. */
        fun heard(rows: List<MessageRow>, limit: Int = TILE_MESSAGES): List<MessageRow> =
            rows.filter { !it.outgoing }.sortedByDescending { it.id }.take(limit)

        /** Unread messages in [conversation], whether or not it notifies. */
        fun unreadIn(lasts: List<ConversationLast>, conversation: String): Int =
            lasts.firstOrNull { it.conversation == conversation }?.unread ?: 0

        /**
         * A channel's name short enough for a complication's title: without a leading "#", whole
         * up to 5 characters, otherwise its first 4 ("Public" is "Publ", "#testing" is "test").
         * Characters are counted as [graphemes], so an emoji made of several is never cut apart.
         */
        fun shortName(name: String): String {
            val bare = name.trim().removePrefix("#").ifEmpty { name.trim() }
            val characters = graphemes(bare)
            return if (characters.size <= 5) bare else characters.take(4).joinToString("")
        }

        /**
         * [text] split into the characters a reader sees: a code point with the marks, variation
         * selectors, skin tones and tags after it, emoji joined by zero-width joiners, and flags
         * (pairs of regional indicators). Written out rather than left to java.text.BreakIterator,
         * which before Java 20 splits joined emoji; Android's would not, but the JVM tests run on 17.
         */
        fun graphemes(text: String): List<String> {
            val out = ArrayList<String>()
            var i = 0
            while (i < text.length) {
                val start = i
                val first = text.codePointAt(i)
                i += Character.charCount(first)
                var flagHalf = isRegionalIndicator(first)
                while (i < text.length) {
                    val next = text.codePointAt(i)
                    when {
                        next == ZERO_WIDTH_JOINER -> {
                            i += Character.charCount(next)
                            if (i < text.length) i += Character.charCount(text.codePointAt(i))
                        }
                        extendsCharacter(next) -> i += Character.charCount(next)
                        flagHalf && isRegionalIndicator(next) -> {
                            i += Character.charCount(next)
                            flagHalf = false
                        }
                        else -> break
                    }
                }
                out.add(text.substring(start, i))
            }
            return out
        }

        private const val ZERO_WIDTH_JOINER = 0x200D

        private fun isRegionalIndicator(cp: Int) = cp in 0x1F1E6..0x1F1FF

        private fun extendsCharacter(cp: Int): Boolean {
            val type = Character.getType(cp)
            return type == Character.NON_SPACING_MARK.toInt() || type == Character.ENCLOSING_MARK.toInt() ||
                type == Character.COMBINING_SPACING_MARK.toInt() ||
                cp in 0xFE00..0xFE0F || cp in 0x1F3FB..0x1F3FF || cp in 0xE0020..0xE007F
        }

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

/** The chosen channel's name and unread count. */
data class ChannelCount(val name: String, val unread: Int) {
    val short: String get() = Glance.shortName(name)
}

/** One message on the Tile. [title] is the conversation's: a contact's or channel's name. */
data class GlanceMessage(
    val conversation: String,
    val title: String,
    val sender: String,
    val body: String,
    val unread: Boolean = false,
) {
    /** Who it is from: the contact in a direct conversation, or "Ann · Public" on a channel. */
    val from: String
        get() = if (conversation.startsWith("d:") || sender.isBlank()) title else "$sender · $title"
}
