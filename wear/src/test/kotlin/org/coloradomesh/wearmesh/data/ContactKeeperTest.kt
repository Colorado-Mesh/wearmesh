package org.coloradomesh.wearmesh.data

import org.coloradomesh.meshcore.protocol.Contact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactKeeperTest {
    private fun kept(key: Char, name: String) = KeptContact("$key".repeat(64), name, Contact.TYPE_COMPANION, 1_700_000_000)

    @Test
    fun dropsArePutBackAndOthersMadeFavourites() {
        val onRadio = listOf(
            Contact("a".repeat(64), Contact.TYPE_COMPANION, "Ann", 0, 39.9, -105.1, flags = 0, outPathLen = 3, outPathHex = "0102030000"),
            Contact("b".repeat(64), Contact.TYPE_COMPANION, "Bo", 0, 0.0, 0.0, flags = Contact.FLAG_FAVOURITE),
            Contact("e".repeat(64), Contact.TYPE_REPEATER, "Repeater", 0, 0.0, 0.0),
        )
        val plan = ContactKeeper.plan(onRadio, listOf(kept('a', "Ann"), kept('b', "Bo"), kept('c', "Cy"), kept('c', "Cy")))
        val cy = plan.restore.single()
        assertEquals("Cy", cy.name)
        assertEquals(Contact.FLAG_FAVOURITE, cy.flags)
        assertEquals(Contact.OUT_PATH_UNKNOWN, cy.outPathLen)
        val ann = plan.favourite.single()
        // Everything the radio had for Ann is kept but the flag.
        assertEquals(onRadio[0].copy(flags = Contact.FLAG_FAVOURITE), ann)
        assertEquals(listOf("Cy", "Ann"), plan.all.map { it.name })
    }

    @Test
    fun nothingToDoWhenEveryoneIsThere() {
        val onRadio = listOf(Contact("a".repeat(64), Contact.TYPE_COMPANION, "Ann", 0, 0.0, 0.0, flags = Contact.FLAG_FAVOURITE or 0x02))
        assertTrue(ContactKeeper.plan(onRadio, listOf(kept('a', "Ann"))).all.isEmpty())
        assertTrue(ContactKeeper.plan(onRadio, emptyList()).all.isEmpty())
    }
}
