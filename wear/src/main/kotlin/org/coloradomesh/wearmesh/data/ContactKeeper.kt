package org.coloradomesh.wearmesh.data

import org.coloradomesh.meshcore.protocol.Contact

/**
 * Keeps the people you message on the radio (decided 1 November 2026). A radio whose contact list
 * is full drops older contacts as it hears new nodes, and it can then neither send a direct
 * message to such a contact nor read one from it. So after each full read of the radio's list:
 * - a contact with a direct-message conversation that the radio no longer lists is put back, from
 *   the key, name and type the watch keeps, with no known route (the first message floods);
 * - every such contact is made a favourite on the radio, which the radio does not drop to make room.
 * A favourite already on the radio is left as it is. One still listed is saved back with its own
 * route and position, the favourite flag added.
 */
object ContactKeeper {
    data class Plan(
        /** Contacts to put back on the radio, as favourites. */
        val restore: List<Contact>,
        /** Contacts on the radio to mark as favourites. */
        val favourite: List<Contact>,
    ) {
        val all: List<Contact> get() = restore + favourite
    }

    fun plan(onRadio: List<Contact>, direct: List<KeptContact>): Plan {
        val listed = onRadio.associateBy { it.publicKeyHex }
        val restore = ArrayList<Contact>()
        val favourite = ArrayList<Contact>()
        for (kept in direct.distinctBy { it.publicKey }) {
            val there = listed[kept.publicKey]
            when {
                there == null -> restore.add(restored(kept))
                !there.isFavourite -> favourite.add(there.copy(flags = there.flags or Contact.FLAG_FAVOURITE))
            }
        }
        return Plan(restore, favourite)
    }

    /** [kept] as it goes back on the radio: a favourite with no known route and no position. */
    fun restored(kept: KeptContact): Contact =
        Contact(kept.publicKey, kept.type, kept.name, kept.lastAdvert, 0.0, 0.0, flags = Contact.FLAG_FAVOURITE)
}
