package org.coloradomesh.wearmesh.data

/**
 * Log lines about the radio's contact list (29 October): step 1 found it full, dropping older
 * contacts as it hears new ones, including people being messaged. These show whether a contact
 * marked as a favourite reaches the radio, and whether the radio drops one anyway.
 */
object Favourites {
    private const val NAMES_SHOWN = 5

    /** After a full read of [contacts] contacts, with these favourites. */
    fun summary(contacts: Int, favourites: List<String>): String {
        val names = favourites.take(NAMES_SHOWN).joinToString(", ") + if (favourites.size > NAMES_SHOWN) ", …" else ""
        return if (favourites.isEmpty()) {
            "Contacts: $contacts on the radio, no favourites"
        } else {
            "Contacts: $contacts on the radio, ${favourites.size} favourite${if (favourites.size == 1) "" else "s"} ($names)"
        }
    }

    /** A contact that mattered and is no longer on the radio's list. */
    fun dropped(contact: DroppedContact): String {
        val what = if (contact.favourite) "favourite" else "contact with a direct-message conversation"
        return "Contacts: the radio no longer lists ${contact.name} (${contact.prefix}), a $what"
    }
}
