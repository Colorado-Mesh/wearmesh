package org.coloradomesh.wearmesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogPrivacyTest {
    @Test
    fun textIsHiddenUnlessShown() {
        assertEquals("(text hidden, 13 characters)", LogPrivacy.text("Meet at 6 pm?", show = false))
        assertEquals("Meet at 6 pm?", LogPrivacy.text("Meet at 6 pm?", show = true))
    }

    @Test
    fun aSavedLogIsNamedForItsTime() {
        val name = LogPrivacy.saveName(System.currentTimeMillis())
        assertTrue(name, Regex("""wearmesh-log-\d{4}-\d{2}-\d{2}-\d{6}\.txt""").matches(name))
    }

    @Test
    fun aCrashGivesTheErrorItsFramesAndItsCause() {
        val cause = IllegalStateException("inner")
        val error = RuntimeException("outer", cause)
        val lines = CrashReport.lines("main", error)
        assertEquals("Crash on thread main: java.lang.RuntimeException: outer", lines.first())
        assertTrue(lines.any { it == "Caused by: java.lang.IllegalStateException: inner" })
        assertTrue(lines.drop(1).first().startsWith("    at "))
        // At most 15 frames for each.
        assertTrue(lines.count { it.startsWith("    at ") } <= 30)
    }

    @Test
    fun causesPastTheLimitAreCounted() {
        // Seven throwables: the error and six causes; five are shown, and the two deepest counted.
        var error: Throwable = RuntimeException("root")
        for (i in 1..6) error = RuntimeException("level $i", error)
        val lines = CrashReport.lines("t", error)
        assertEquals(4, lines.count { it.startsWith("Caused by: java") })
        assertEquals("Caused by: … 2 more causes, not shown", lines.last())
    }

    @Test
    fun aCauseThatLoopsEnds() {
        val a = RuntimeException("a")
        val b = RuntimeException("b", a)
        a.initCause(b)
        assertTrue(CrashReport.lines("t", a).size < 100)
    }
}
