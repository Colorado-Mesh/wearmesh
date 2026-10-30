package org.coloradomesh.wearmesh.data

import org.coloradomesh.meshcore.protocol.Channel
import org.coloradomesh.meshcore.protocol.Contact

/** Conversation keys: one per channel slot and one per direct-message sender. */
object Conversations {
    fun channel(slot: Int) = "c:$slot"

    /** [senderPrefixHex] is the 12 hex characters the radio gives for a direct message's sender. */
    fun direct(senderPrefixHex: String) = "d:${senderPrefixHex.lowercase()}"
}

/** What the store keeps about the radio its messages came from. */
data class StoredRadio(
    val address: String,
    /** The radio's contact list time to ask for changes from; 0 asks for everything. */
    val contactsSince: Long,
    /** When the channel slots and the whole contact list were last read, by the watch's clock; 0 never. */
    val fullReadAt: Long,
)

data class IncomingMessage(
    val conversation: String,
    /** The sender's node name for a channel message; empty for a direct message, or when unknown. */
    val sender: String,
    val body: String,
    /** Unix seconds by the sender's clock, which may be wrong. */
    val sentAt: Long,
    /** Milliseconds by the watch's clock. */
    val receivedAt: Long,
)

/** Where a message this watch sent has got to (M3). */
enum class OutgoingStatus {
    /** Waiting for a connection, or handed to the radio and not yet answered. */
    SENDING,

    /** A channel message the radio took. MeshCore reports nothing more for channel messages. */
    SENT,

    /** A direct message its recipient acknowledged. */
    DELIVERED,

    /** A direct message sent on every try with no acknowledgement; it may still have arrived. */
    UNCONFIRMED,

    /** Not sent: the radio refused it, or it waited too long for a connection. */
    FAILED,
}

/** A message this watch has still to send. */
data class OutgoingMessage(
    val id: Long,
    val conversation: String,
    val body: String,
    /** When it was written, by the watch's clock, in milliseconds. */
    val createdAt: Long,
)

data class StoreCounts(val messages: Int, val conversations: Int, val contacts: Int, val channels: Int)

/**
 * Messages, channels and contacts from one radio, kept on the watch: a view of the store for the
 * radio [address]. Each radio the watch has used keeps its own history, so switching back to a
 * radio shows what was stored from it before (decided 28 October). Forgetting a radio deletes
 * its history. Each conversation keeps its newest [KEEP_PER_CONVERSATION] messages. A message
 * already stored (same conversation, sender, sent time and text) is not stored again.
 *
 * Calls block on storage, so they are made off the main thread, one at a time (see [Storage]).
 */
interface MessageStore {
    /** The radio this view is for. */
    val address: String

    /** This radio's sync state, or null before [open] first runs for it. */
    fun radio(): StoredRadio?

    /** Starts keeping data for this radio, if the store has none yet; what it has is kept. */
    fun open()

    /** Deletes everything stored from this radio. */
    fun forget()

    /** Adds or updates these contacts, marking them seen at [readAt]. */
    fun saveContacts(contacts: List<Contact>, readAt: Long)

    /** After a full contact list read at [readAt]: drops contacts the radio no longer has. */
    fun dropContactsNotSeenSince(readAt: Long)

    fun setContactsSince(since: Long)

    /**
     * Replaces the channel list with [channels]. A slot that now holds a different channel, or
     * none, loses its messages, including any still waiting to send: they were written for the
     * old channel. Returns how many of those there were. A slot in [unanswered] (the radio did not
     * answer for it) keeps what was stored, since its channel is unknown rather than gone.
     */
    fun saveChannels(channels: List<Channel>, unanswered: Set<Int> = emptySet()): Int

    /** Records a complete full read (every channel slot answered, the whole contact list read) at [at]. */
    fun markFullRead(at: Long)

    /** Stores [message] and trims its conversation; false when it was already stored. */
    fun addMessage(message: IncomingMessage): Boolean

    fun counts(): StoreCounts

    /**
     * Stores a message to send in [conversation], waiting ([OutgoingStatus.SENDING]); [sender] is
     * this radio's node name. Returns its id, or -1 when the same text was already written in the
     * same second (a double tap).
     */
    fun addOutgoing(conversation: String, sender: String, body: String, at: Long): Long

    /** Messages still to send, oldest first. */
    fun pendingOutgoing(): List<OutgoingMessage>

    fun setOutgoingStatus(id: Long, status: OutgoingStatus)

    companion object {
        /** Decided for M2: the newest 100 messages per conversation. */
        const val KEEP_PER_CONVERSATION = 100
    }
}
