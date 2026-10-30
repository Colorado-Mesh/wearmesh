package org.coloradomesh.wearmesh.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FavouritesTest {
    @Test
    fun summaries() {
        assertEquals("Contacts: 350 on the radio, no favourites", Favourites.summary(350, emptyList()))
        assertEquals("Contacts: 350 on the radio, 1 favourite (Ann)", Favourites.summary(350, listOf("Ann")))
        assertEquals(
            "Contacts: 350 on the radio, 6 favourites (a, b, c, d, e, …)",
            Favourites.summary(350, listOf("a", "b", "c", "d", "e", "f")),
        )
    }
}
