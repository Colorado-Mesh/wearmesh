package org.coloradomesh.wearmesh.ui

import org.coloradomesh.wearmesh.data.ChannelRow
import org.coloradomesh.wearmesh.data.ContactRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewMessageTest {
    private val now = 1_800_000_000_000L

    private fun contact(prefix: String, name: String, heardSecondsAgo: Long = 60, favourite: Boolean = false) =
        ContactRow("R", prefix + "0".repeat(52), prefix, name, 1, now / 1000 - heardSecondsAgo, now, favourite)

    @Test
    fun favouritesThenRecentThenChannelsInSlotOrder() {
        val picks = NewMessage.build(
            favourites = listOf(contact("aaaaaaaaaaaa", "Ann", favourite = true)),
            recent = listOf(contact("bbbbbbbbbbbb", "Bo", heardSecondsAgo = 240)),
            channels = listOf(ChannelRow("R", 3, "#testing", "HASHTAG"), ChannelRow("R", 0, "Public", "PUBLIC")),
            now = now,
        )
        assertEquals(listOf(Pick("d:aaaaaaaaaaaa", "Ann", null)), picks.favourites)
        assertEquals(listOf(Pick("d:bbbbbbbbbbbb", "Bo", "Heard 4 min ago")), picks.recent)
        assertEquals(listOf("c:0", "c:3"), picks.channels.map { it.conversation })
    }

    @Test
    fun anAdvertFromAClockAheadHasNoTime() {
        val picks = NewMessage.build(
            favourites = emptyList(),
            recent = listOf(contact("aaaaaaaaaaaa", "Ahead", heardSecondsAgo = -600), contact("bbbbbbbbbbbb", "Slightly", heardSecondsAgo = -30)),
            channels = emptyList(),
            now = now,
        )
        assertEquals(listOf(null, "Heard just now"), picks.recent.map { it.detail })
    }

    @Test
    fun aDirectConversationIsFlaggedUnlessAFavourite() {
        assertNull(NewMessage.favouriteWarning("c:0", null))
        assertNull(NewMessage.favouriteWarning("d:aaaaaaaaaaaa", contact("aaaaaaaaaaaa", "Ann", favourite = true)))
        assertTrue(NewMessage.favouriteWarning("d:aaaaaaaaaaaa", contact("aaaaaaaaaaaa", "Ann"))!!.startsWith("Not a favourite"))
        assertTrue(NewMessage.favouriteWarning("d:aaaaaaaaaaaa", null)!!.startsWith("Your radio no longer lists"))
    }
}
