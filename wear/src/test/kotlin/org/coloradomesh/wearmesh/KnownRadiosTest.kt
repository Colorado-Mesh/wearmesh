package org.coloradomesh.wearmesh

import org.junit.Assert.assertEquals
import org.junit.Test

class KnownRadiosTest {
    private val t1 = KnownRadio("F3:9E:35:24:99:76", "🔥 W0RMT 02")
    private val test = KnownRadio("EC:94:9C:51:86:24", "W0RMT-M9")

    @Test
    fun savesAndReadsBack() {
        val list = listOf(t1, test)
        assertEquals(list, KnownRadios.parse(KnownRadios.format(list)))
    }

    @Test
    fun nothingSavedIsAnEmptyList() {
        assertEquals(emptyList<KnownRadio>(), KnownRadios.parse(null))
        assertEquals(emptyList<KnownRadio>(), KnownRadios.parse(""))
    }

    @Test
    fun addingPutsTheRadioFirstAndReplacesAnOlderEntry() {
        val list = KnownRadios.add(listOf(t1, test), test.copy(name = "renamed"))
        assertEquals(listOf(test.copy(name = "renamed"), t1), list)
    }

    @Test
    fun keepsAtMostEight() {
        var list = emptyList<KnownRadio>()
        for (i in 0 until 10) list = KnownRadios.add(list, KnownRadio("00:00:00:00:00:0$i", "r$i"))
        assertEquals(KnownRadios.MAX, list.size)
        assertEquals("r9", list.first().name)
    }

    @Test
    fun removes() {
        assertEquals(listOf(test), KnownRadios.remove(listOf(t1, test), t1.address))
    }

    @Test
    fun aNameCannotBreakTheFormat() {
        val odd = KnownRadio("AA:BB:CC:DD:EE:FF", "tab\there\nnewline")
        assertEquals(listOf(odd.copy(name = "tab here newline")), KnownRadios.parse(KnownRadios.format(listOf(odd))))
    }

    @Test
    fun skipsBrokenLinesAndRepeats() {
        assertEquals(listOf(t1), KnownRadios.parse("garbage\n${t1.address}\t${t1.name}\n${t1.address}\tagain"))
    }
}
