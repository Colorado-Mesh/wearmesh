package org.coloradomesh.wearmesh.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageTextTest {
    private fun split(text: String) = MessageText.split(text).let { it.sender to it.body }

    @Test
    fun splitsTheSenderFromTheText() {
        assertEquals("TheOmegaKira-01M" to "Hello", split("TheOmegaKira-01M: Hello"))
    }

    @Test
    fun keepsEmojiAndSpacesInNames() {
        assertEquals("🔥 W0RMT 02" to "@flux howdy", split("🔥 W0RMT 02: @flux howdy"))
        assertEquals("Mes# TH🏔️T Div÷de" to "T", split("Mes# TH🏔️T Div÷de: T"))
    }

    @Test
    fun splitsAtTheFirstSeparatorOnly() {
        assertEquals(
            "☢️ Ob5e55ed" to "ack @[W5KV] | 5b31,e5e5 (2 hops) | Received at: 20:53:07",
            split("☢️ Ob5e55ed: ack @[W5KV] | 5b31,e5e5 (2 hops) | Received at: 20:53:07"),
        )
    }

    @Test
    fun aColonWithoutASpaceIsNotASeparator() {
        assertEquals("" to "http://example.com:8080/x", split("http://example.com:8080/x"))
        assertEquals("Ann" to "see 10:30", split("Ann: see 10:30"))
    }

    @Test
    fun textWithNoNameIsKeptWhole() {
        assertEquals("" to "just text", split("just text"))
        assertEquals("" to ": starts with a separator", split(": starts with a separator"))
        assertEquals("" to "   : blank name", split("   : blank name"))
    }

    @Test
    fun aPrefixLongerThanANodeNameIsPartOfTheMessage() {
        val thirtyOne = "a".repeat(31)
        assertEquals(thirtyOne to "hi", split("$thirtyOne: hi"))
        val thirtyTwo = "a".repeat(32)
        assertEquals("" to "$thirtyTwo: hi", split("$thirtyTwo: hi"))
        // Counted in bytes, as the radio stores names: ten 4-byte emoji are 40 bytes.
        val emoji = "🔥".repeat(10)
        assertEquals("" to "$emoji: hi", split("$emoji: hi"))
    }

    @Test
    fun aLineBreakBeforeTheSeparatorMeansNoName() {
        assertEquals("" to "first line\nsecond: line", split("first line\nsecond: line"))
    }

    @Test
    fun anEmptyMessageAfterTheNameIsKept() {
        assertEquals("Ann" to "", split("Ann: "))
    }
}
