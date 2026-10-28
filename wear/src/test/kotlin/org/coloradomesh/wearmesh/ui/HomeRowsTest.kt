package org.coloradomesh.wearmesh.ui

import org.coloradomesh.wearmesh.data.ChannelRow
import org.coloradomesh.wearmesh.data.ConversationLast
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeRowsTest {
    private val channels = listOf(
        ChannelRow(3, "#testing", "HASHTAG"),
        ChannelRow(0, "Public", "PUBLIC"),
        ChannelRow(5, "Team", "PRIVATE"),
    )

    private fun last(conversation: String, at: Long, unread: Int = 0, sender: String = "Ann", contact: String? = null) =
        ConversationLast(conversation, sender, "hi", at, unread, contact)

    @Test
    fun directMessagesComeFirstNewestFirstThenEveryChannelBySlot() {
        val rows = HomeRows.build(
            channels,
            listOf(
                last("c:0", at = 900, unread = 12),
                last("d:a1b2c3d4e5f6", at = 100, sender = "", contact = "Bo"),
                last("d:0123456789ab", at = 500, sender = ""),
                last("c:3", at = 950),
            ),
        )
        assertEquals(listOf("0123456789ab", "Bo", "Public", "#testing", "Team"), rows.map { it.title })
        assertEquals(listOf(true, true, false, false, false), rows.map { it.direct })
        assertEquals(12, rows[2].unread)
    }

    @Test
    fun aChannelWithNoMessagesIsStillListed() {
        val team = HomeRows.build(channels, emptyList()).last()
        assertEquals("Team", team.title)
        assertEquals(0, team.unread)
        assertEquals(null, team.last)
    }

    @Test
    fun messagesOnAnUnknownSlotComeLast() {
        val rows = HomeRows.build(channels, listOf(last("c:7", at = 1)))
        assertEquals("Channel 7", rows.last().title)
        assertEquals("c:7", rows.last().conversation)
    }

    @Test
    fun previewsWithAndWithoutASender() {
        assertEquals("Ann: hi", HomeRows.preview(last("c:0", at = 1)))
        assertEquals("hi", HomeRows.preview(last("d:a1b2c3d4e5f6", at = 1, sender = "")))
    }

    @Test
    fun aBlankContactNameFallsBackToThePrefix() {
        assertEquals("a1b2c3d4e5f6", HomeRows.directTitle(last("d:a1b2c3d4e5f6", at = 1, contact = " ")))
    }

    @Test
    fun statusLineWords() {
        val now = 1_800_000_000_000L
        assertEquals("Live: Connected", HomeRows.status("Connected", syncing = true, problem = "x", lastSyncAt = 0, now = now))
        assertEquals("Syncing…", HomeRows.status(null, syncing = true, problem = null, lastSyncAt = now, now = now))
        assertEquals("Radio did not answer", HomeRows.status(null, false, "Radio did not answer", now - 60_000, now))
        assertEquals("Synced just now", HomeRows.status(null, false, null, now - 5_000, now))
        assertEquals("Synced 4 min ago", HomeRows.status(null, false, null, now - 4 * 60_000, now))
        assertEquals("Synced 2 h ago", HomeRows.status(null, false, null, now - 2 * 3_600_000, now))
        assertEquals(true, HomeRows.status(null, false, null, now - 30L * 3_600_000, now).startsWith("Last synced "))
        assertEquals("Not synced yet", HomeRows.status(null, false, null, 0, now))
    }
}
