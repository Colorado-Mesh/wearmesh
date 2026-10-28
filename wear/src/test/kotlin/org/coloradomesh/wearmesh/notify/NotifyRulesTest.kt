package org.coloradomesh.wearmesh.notify

import org.coloradomesh.wearmesh.notify.NotifyRules.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotifyRulesTest {
    private val me = "🔥 W0RMT 02"

    @Test
    fun kindsFromTheConversationAndTheStoredChannel() {
        assertEquals(Kind.DIRECT, NotifyRules.kindOf("d:a1b2c3d4e5f6", null))
        assertEquals(Kind.PUBLIC, NotifyRules.kindOf("c:0", "PUBLIC"))
        assertEquals(Kind.HASHTAG, NotifyRules.kindOf("c:3", "HASHTAG"))
        assertEquals(Kind.PRIVATE, NotifyRules.kindOf("c:5", "PRIVATE"))
        assertEquals(Kind.PRIVATE, NotifyRules.kindOf("c:9", null))
    }

    @Test
    fun directMessagesAndPrivateChannelsAreOnByDefault() {
        assertTrue(NotifyRules.isOn("d:a1b2c3d4e5f6", Kind.DIRECT, emptyMap()))
        assertTrue(NotifyRules.isOn("c:5", Kind.PRIVATE, emptyMap()))
        assertFalse(NotifyRules.isOn("c:0", Kind.PUBLIC, emptyMap()))
        assertFalse(NotifyRules.isOn("c:3", Kind.HASHTAG, emptyMap()))
    }

    @Test
    fun aChoiceOverridesTheDefault() {
        val choices = mapOf("c:0" to true, "c:5" to false)
        assertTrue(NotifyRules.isOn("c:0", Kind.PUBLIC, choices))
        assertFalse(NotifyRules.isOn("c:5", Kind.PRIVATE, choices))
    }

    @Test
    fun aMentionOfThisRadioNotifiesOnAChannelThatIsOff() {
        assertTrue(NotifyRules.shouldNotify(false, "@[$me] you there?", me, openOnScreen = false, backlog = false))
        assertFalse(NotifyRules.shouldNotify(false, "@[flux] you there?", me, openOnScreen = false, backlog = false))
        assertFalse(NotifyRules.shouldNotify(false, "@[$me] you there?", null, openOnScreen = false, backlog = false))
    }

    @Test
    fun nothingNotifiesOnScreenOrInTheFirstSyncsBacklog() {
        assertTrue(NotifyRules.shouldNotify(true, "hi", me, openOnScreen = false, backlog = false))
        assertFalse(NotifyRules.shouldNotify(true, "hi", me, openOnScreen = true, backlog = false))
        assertFalse(NotifyRules.shouldNotify(true, "@[$me]", me, openOnScreen = false, backlog = true))
    }

    @Test
    fun choicesSaveAndReadBack() {
        val choices = mapOf("c:0" to true, "d:a1b2c3d4e5f6" to false)
        assertEquals(choices, NotifyRules.parseChoices(NotifyRules.formatChoices(choices)))
        assertEquals(emptyMap<String, Boolean>(), NotifyRules.parseChoices(null))
        assertEquals(mapOf("c:1" to true), NotifyRules.parseChoices("junk\nc:1=1\nc:2=x\n=1"))
    }

    @Test
    fun choosingTheDefaultDropsTheChoice() {
        var choices = NotifyRules.choose(emptyMap(), "c:0", Kind.PUBLIC, on = true)
        assertEquals(mapOf("c:0" to true), choices)
        choices = NotifyRules.choose(choices, "c:0", Kind.PUBLIC, on = false)
        assertEquals(emptyMap<String, Boolean>(), choices)
        assertEquals(mapOf("c:5" to false), NotifyRules.choose(emptyMap(), "c:5", Kind.PRIVATE, on = false))
    }

    @Test
    fun aConversationBuzzesOnceInFiveMinutesUnlessOpened() {
        val gate = AlertGate()
        val t = 1_800_000_000_000L
        assertTrue(gate.shouldAlert("c:5", t))
        assertFalse(gate.shouldAlert("c:5", t + 60_000))
        assertTrue(gate.shouldAlert("d:a1b2c3d4e5f6", t + 60_000))
        assertTrue(gate.shouldAlert("c:5", t + 5 * 60_000))
        gate.opened("c:5")
        assertTrue(gate.shouldAlert("c:5", t + 5 * 60_000 + 1))
    }

    @Test
    fun aClockThatWentBackDoesNotSilenceAConversation() {
        val gate = AlertGate()
        assertTrue(gate.shouldAlert("c:5", 1_800_000_000_000L))
        assertTrue(gate.shouldAlert("c:5", 1_700_000_000_000L))
    }
}
