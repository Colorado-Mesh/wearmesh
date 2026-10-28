package org.coloradomesh.wearmesh

/** A radio this watch knows: its Bluetooth address and the name it advertised. */
data class KnownRadio(val address: String, val name: String)

/**
 * The radios list, kept in settings as one line per radio, `address<TAB>name`, most recently
 * added or used first. Only one is used at a time (a radio serves one client).
 */
object KnownRadios {
    const val MAX = 8

    fun parse(saved: String?): List<KnownRadio> = saved.orEmpty().lines().mapNotNull { line ->
        val tab = line.indexOf('\t')
        if (tab <= 0) null else KnownRadio(line.substring(0, tab), line.substring(tab + 1))
    }.distinctBy { it.address }

    fun format(radios: List<KnownRadio>): String =
        radios.joinToString("\n") { "${it.address}\t${clean(it.name)}" }

    /** Puts [radio] first, replacing an older entry with the same address; keeps at most [MAX]. */
    fun add(radios: List<KnownRadio>, radio: KnownRadio): List<KnownRadio> =
        (listOf(radio.copy(name = clean(radio.name))) + radios.filter { it.address != radio.address }).take(MAX)

    fun remove(radios: List<KnownRadio>, address: String): List<KnownRadio> = radios.filter { it.address != address }

    /** A name cannot break the line format; a blank one shows as the address. */
    private fun clean(name: String) = name.replace('\t', ' ').replace('\n', ' ').trim()
}
