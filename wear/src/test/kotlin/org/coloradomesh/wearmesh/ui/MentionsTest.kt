package org.coloradomesh.wearmesh.ui

import org.coloradomesh.wearmesh.ui.Mentions.Part.Mention
import org.coloradomesh.wearmesh.ui.Mentions.Part.Plain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MentionsTest {
    @Test
    fun splitsMentionsFromText() {
        assertEquals(
            listOf(Plain("Hey! "), Mention("🔥 W0RMT 02"), Plain(" and "), Mention("ke4iqr-1")),
            Mentions.parse("Hey! @[🔥 W0RMT 02] and @[ke4iqr-1]"),
        )
    }

    @Test
    fun textWithoutMentionsIsOnePart() {
        assertEquals(listOf(Plain("just text @ here [x]")), Mentions.parse("just text @ here [x]"))
        assertEquals(emptyList<Mentions.Part>(), Mentions.parse(""))
    }

    @Test
    fun anUnclosedOrEmptyMentionIsPlainText() {
        assertEquals(listOf(Plain("@[open and @[] too")), Mentions.parse("@[open and @[] too"))
    }

    @Test
    fun saysWhetherAMessageNamesThisRadio() {
        assertTrue(Mentions.names("@[🔥 W0RMT 02] howdy", "🔥 W0RMT 02"))
        assertFalse(Mentions.names("@[flux] howdy", "🔥 W0RMT 02"))
        assertFalse(Mentions.names("@[flux] howdy", null))
        assertFalse(Mentions.names("plain W0RMT", "W0RMT"))
    }
}
