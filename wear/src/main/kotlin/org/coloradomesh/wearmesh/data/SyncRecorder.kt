package org.coloradomesh.wearmesh.data

import org.coloradomesh.meshcore.protocol.Channel
import org.coloradomesh.meshcore.protocol.ChannelMessage
import org.coloradomesh.meshcore.protocol.CompanionClient
import org.coloradomesh.meshcore.protocol.Contact
import org.coloradomesh.meshcore.protocol.DirectMessage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger

/**
 * The radio sync step's listener: stores what one connection reads, then passes every call on to
 * [also] (the caller's own logging and status).
 *
 * Shared by Check-in and Live. The protocol client calls a listener with its lock held, so
 * nothing here touches storage directly: each write goes to [writer], which runs them one at a
 * time, in order. Contacts are gathered and written in one go when a contact list read ends,
 * since a full read brings about 350 of them; [finish] writes any left over, such as nodes heard
 * after the read, or a read the radio never finished.
 *
 * On a full read ([plan]), contacts the radio no longer has are dropped after the contact list
 * read (except those with a direct-message conversation, which [ContactKeeper] has [onKeep] save
 * back to the radio, as favourites, along with any such contact not yet a favourite), and the read is recorded as complete only once that and an answer for every channel slot
 * have both come; otherwise the next sync reads everything again. A message the store already has
 * is counted in [duplicates] and logged, not stored again.
 *
 * After [finish], nothing more is stored: anything the radio still sends (a session closing late,
 * or one replaced by a session with another radio) is logged and passed on only.
 */
class SyncRecorder(
    private val store: MessageStore,
    private val plan: SyncPlan,
    private val writer: Executor,
    private val log: (String) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
    private val also: CompanionClient.Listener = object : CompanionClient.Listener {},
    /** Each message newly stored, on the storage thread, right after it is written; for notifications. */
    private val onStored: (IncomingMessage) -> Unit = {},
    /**
     * After a full read's contact list is stored, on the storage thread: the contacts to save to
     * the radio (possibly none). Called once per full read; the caller hands each to the radio.
     */
    private val onKeep: (List<Contact>) -> Unit = {},
    /**
     * Whether the user has turned on Settings › Keep contacts on radio. Off, [onKeep] is given
     * nothing, and the log says when someone you message is missing from the radio.
     */
    private val keepContacts: Boolean = false,
) : CompanionClient.Listener by also {
    /** Names of the contacts handed to [onKeep] and not yet answered for, by key, for the log. */
    private val keeping = ConcurrentHashMap<String, Contact>()

    /** Set on the storage thread once a full read's contact sweep is stored; markFullRead waits on it. */
    @Volatile
    private var sweepStored = false
    /** Every contact this connection reads is marked with this time, and a full read with it. */
    private val readAt = clock()
    private val contacts = ArrayList<Contact>()
    private val stored = AtomicInteger(0)
    private val repeated = AtomicInteger(0)

    @Volatile
    private var finished = false

    // Set from listener calls, which the protocol client makes one at a time.
    private var contactsSwept = false
    private var channelsComplete = false
    private var fullReadMarked = false
    private var unansweredSlots: Set<Int> = emptySet()

    /** Messages stored so far. Complete once the writer has caught up. */
    val newMessages: Int get() = stored.get()

    /** Messages the store already had. Complete once the writer has caught up. */
    val duplicates: Int get() = repeated.get()

    override fun onContact(contact: Contact) {
        if (!finished) synchronized(contacts) { contacts.add(contact) }
        also.onContact(contact)
    }

    override fun onContactsRead(lastModified: Long) {
        if (afterFinish("a contact list")) return also.onContactsRead(lastModified)
        val batch = takeContacts()
        // Only the first read of a full start-up is the whole list; later ones follow adverts.
        val sweep = plan.fullRead && !contactsSwept
        write {
            store.saveContacts(batch, readAt)
            if (sweep) {
                for (gone in store.dropContactsNotSeenSince(readAt)) log(Favourites.dropped(gone))
                // A node heard advertising during the read can come twice; the store keeps one of each.
                log(Favourites.summary(batch.distinctBy { it.publicKeyHex }.size, store.favouriteNames()))
                val keep = ContactKeeper.plan(batch, store.directContacts())
                if (keepContacts) {
                    for (contact in keep.restore) log("Contacts: putting ${contact.name} (${contact.publicKeyHex.take(12)}) back on the radio, as a favourite")
                    for (contact in keep.favourite) log("Contacts: making ${contact.name} (${contact.publicKeyHex.take(12)}) a favourite on the radio, as someone you message")
                    for (contact in keep.all) keeping[contact.publicKeyHex] = contact
                    onKeep(keep.all)
                } else {
                    if (keep.restore.isNotEmpty()) {
                        val who = keep.restore.joinToString { it.name }
                        log("Contacts: not on the radio, though you message them: $who. Settings › Keep contacts on radio would put them back")
                    }
                    onKeep(emptyList())
                }
                sweepStored = true
            }
            store.setContactsSince(lastModified)
        }
        if (sweep) {
            contactsSwept = true
            markFullReadIfComplete()
        }
        also.onContactsRead(lastModified)
    }

    /**
     * A save of [contact] handed to the radio from elsewhere (the outbox putting back a refused
     * recipient), so that when the radio confirms it the store shows it as a favourite too.
     */
    fun saving(contact: Contact) {
        keeping[contact.publicKeyHex] = contact
    }

    override fun onContactSaved(publicKeyHex: String, saved: Boolean, errorCode: Int?) {
        val contact = keeping.remove(publicKeyHex)
        val name = contact?.name ?: publicKeyHex.take(12)
        // On the radio now, as a favourite: the store shows it so at once, not at the next read.
        if (saved && contact != null && !finished) write { store.saveContacts(listOf(contact), readAt) }
        if (!saved) {
            val why = errorCode?.let { "error $it" } ?: "no answer"
            log("Contacts: the radio did not take $name ($why); if its list is full, set it to overwrite the oldest, or to add only chat users")
        }
        also.onContactSaved(publicKeyHex, saved, errorCode)
    }

    override fun onChannelSlotsUnanswered(slots: Set<Int>) {
        unansweredSlots = slots
        log("No answer for channel slot(s) ${slots.sorted().joinToString()}; keeping what was stored for them")
        also.onChannelSlotsUnanswered(slots)
    }

    override fun onChannels(channels: List<Channel>) {
        if (afterFinish("the channel list")) return also.onChannels(channels)
        val missed = unansweredSlots
        write {
            val dropped = store.saveChannels(channels, missed)
            if (dropped > 0) log("A channel slot now holds another channel, or none; $dropped messages waiting to send on it were not sent")
        }
        channelsComplete = missed.isEmpty()
        markFullReadIfComplete()
        also.onChannels(channels)
    }

    override fun onChannelMessage(message: ChannelMessage) {
        if (!afterFinish("a channel message")) {
            val split = MessageText.split(message.text)
            add(IncomingMessage(Conversations.channel(message.channelIndex), split.sender, split.body, message.timestamp, clock()))
        }
        also.onChannelMessage(message)
    }

    override fun onDirectMessage(message: DirectMessage) {
        if (!afterFinish("a direct message")) {
            add(IncomingMessage(Conversations.direct(message.senderPrefixHex), "", message.text, message.timestamp, clock()))
        }
        also.onDirectMessage(message)
    }

    /** Writes contacts not yet written, then stops storing. Call once the connection has ended. */
    fun finish() {
        finished = true
        val rest = takeContacts()
        if (rest.isNotEmpty()) write { store.saveContacts(rest, readAt) }
    }

    private fun markFullReadIfComplete() {
        if (!plan.fullRead || fullReadMarked || !contactsSwept || !channelsComplete) return
        fullReadMarked = true
        // Queued after the contact and channel writes, so it is recorded only once they are, and
        // not when the contact sweep failed: the next check-in then reads everything again.
        write {
            if (sweepStored) store.markFullRead(readAt) else log("Full read not recorded: its contact list was not stored")
        }
    }

    /** True, with a log line, when [what] came after [finish] and will not be stored. */
    private fun afterFinish(what: String): Boolean {
        if (finished) log("The radio sent $what after the sync ended; not stored")
        return finished
    }

    private fun add(message: IncomingMessage) = write {
        if (store.addMessage(message)) {
            stored.incrementAndGet()
            onStored(message)
        } else {
            repeated.incrementAndGet()
            log("Already stored, not kept twice: ${message.conversation}, sent ${message.sentAt}, from \"${message.sender}\"")
        }
    }

    private fun takeContacts(): List<Contact> = synchronized(contacts) {
        val batch = contacts.toList()
        contacts.clear()
        batch
    }

    private fun write(block: () -> Unit) = writer.execute {
        try {
            block()
        } catch (e: Exception) {
            // A failed write loses that write only; the radio link carries on.
            log("Storage failed: ${e::class.simpleName}: ${e.message}")
        }
    }
}
