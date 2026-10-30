package org.coloradomesh.wearmesh.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class QuickRepliesTest {
    @Test
    fun startsWithTheDecidedSet() {
        assertEquals(listOf("OK", "Yes", "No", "On my way", "Copy", "Call me", "73"), QuickReplies.parse(null))
    }

    @Test
    fun aSavedListRoundTripsAndMayBeEmpty() {
        val replies = listOf("OK", "At the trailhead")
        assertEquals(replies, QuickReplies.parse(QuickReplies.format(replies)))
        assertEquals(emptyList<String>(), QuickReplies.parse(""))
    }

    @Test
    fun addingSkipsBlanksRepeatsAndAFullList() {
        val start = listOf("OK")
        assertEquals(listOf("OK", "Back soon"), QuickReplies.add(start, "  Back\nsoon "))
        assertEquals(start, QuickReplies.add(start, "   "))
        assertEquals(start, QuickReplies.add(start, "OK"))
        val full = (1..QuickReplies.MAX).map { "r$it" }
        assertEquals(full, QuickReplies.add(full, "one more"))
    }

    @Test
    fun changingToBlankRemoves() {
        val start = listOf("OK", "Yes", "No")
        assertEquals(listOf("OK", "Yep", "No"), QuickReplies.change(start, 1, "Yep"))
        assertEquals(listOf("OK", "No"), QuickReplies.change(start, 1, " "))
        assertEquals(listOf("OK", "No"), QuickReplies.change(start, 1, "No"))
        assertEquals(start, QuickReplies.change(start, 7, "x"))
    }
}
