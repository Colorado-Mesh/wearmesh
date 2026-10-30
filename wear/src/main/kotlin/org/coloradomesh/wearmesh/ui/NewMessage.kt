package org.coloradomesh.wearmesh.ui

import androidx.compose.runtime.Composable
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.Text
import org.coloradomesh.wearmesh.data.ChannelRow
import org.coloradomesh.wearmesh.data.ContactRow
import org.coloradomesh.wearmesh.data.Conversations

/** One thing to start a message to: a contact or a channel. */
data class Pick(val conversation: String, val title: String, val detail: String?)

/** The New message list's three parts. */
data class Picks(val favourites: List<Pick>, val recent: List<Pick>, val channels: List<Pick>)

/**
 * What New message offers (decided for M3): the radio's favourites first, then the companions
 * heard most recently, then the channels. Only companions (people's radios) are listed for
 * direct messages: repeaters, room servers and sensors are not people to message.
 */
object NewMessage {
    /** How many recently heard companions to list; the radio holds hundreds. */
    const val RECENT = 20

    /** An advert this far ahead of the watch's clock still counts as just now. */
    private const val CLOCK_SLACK_MS = 60_000L

    fun build(favourites: List<ContactRow>, recent: List<ContactRow>, channels: List<ChannelRow>, now: Long): Picks = Picks(
        favourites = favourites.map { Pick(Conversations.direct(it.prefix), it.name, null) },
        recent = recent.map { Pick(Conversations.direct(it.prefix), it.name, heard(now, it.lastAdvert)) },
        channels = channels.sortedBy { it.slot }.map { Pick(Conversations.channel(it.slot), it.name, null) },
    )

    /**
     * "Heard 4 min ago", by the time the node gave in its advert (its own clock). None for an
     * advert dated ahead of the watch: its clock is wrong, so its age is unknown.
     */
    private fun heard(now: Long, lastAdvertSeconds: Long): String? {
        if (lastAdvertSeconds <= 0 || lastAdvertSeconds * 1000 > now + CLOCK_SLACK_MS) return null
        val age = TimeAgo.format(now, lastAdvertSeconds * 1000)
        return when {
            age == "now" -> "Heard just now"
            age.endsWith(" min") || age.endsWith(" h") -> "Heard $age ago"
            else -> "Heard $age"
        }
    }

    /**
     * For a direct conversation whose contact is not a favourite on the radio: the radio's full
     * contact list drops it in time, and with it the conversation (step 1). Null when it is one.
     */
    fun favouriteWarning(conversation: String, contact: ContactRow?): String? = when {
        !conversation.startsWith("d:") -> null
        contact == null -> "Your radio no longer lists this contact, so messages cannot go either way. An advert from them brings it back."
        contact.favourite -> null
        else -> "Not a favourite on your radio, which may drop this contact when its list is full. Mark it a favourite in the phone app."
    }
}

@Composable
fun NewMessageScreen(picks: Picks, onPick: (Pick) -> Unit) {
    ScreenList {
        item { ListHeader { Text("New message") } }
        if (picks.favourites.isNotEmpty()) {
            item { ListHeader { Text("Favourites") } }
            items(picks.favourites, key = { "f" + it.conversation }) { pick -> WideChip(pick.title) { onPick(pick) } }
        }
        if (picks.recent.isNotEmpty()) {
            item { ListHeader { Text("Recently heard") } }
            items(picks.recent, key = { "r" + it.conversation }) { pick ->
                WideChip(pick.title, detail = pick.detail, secondary = true) { onPick(pick) }
            }
        }
        if (picks.favourites.isEmpty() && picks.recent.isEmpty()) {
            item { Note("No companions on the radio's list yet. They appear once the radio has heard their adverts, at the next full read.") }
        }
        if (picks.channels.isNotEmpty()) {
            item { ListHeader { Text("Channels") } }
            items(picks.channels, key = { "c" + it.conversation }) { pick -> WideChip(pick.title, secondary = true) { onPick(pick) } }
        }
    }
}
