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

data class StoreCounts(val messages: Int, val conversations: Int, val contacts: Int, val channels: Int)

/**
 * Messages, channels and contacts from one radio, kept on the watch.
 *
 * One history: switching to a different radio clears everything first, and forgetting the radio
 * deletes its messages too (decided for M2). Each conversation keeps its newest
 * [KEEP_PER_CONVERSATION] messages. A message already stored (same conversation, sender, sent
 * time and text) is not stored again.
 *
 * Calls block on storage, so they are made off the main thread, one at a time (see [Storage]).
 */
interface MessageStore {
    fun radio(): StoredRadio?

    /** Makes [address] the store's radio. If it holds another radio's data, that is cleared first. */
    fun useRadio(address: String)

    /** Deletes everything, if [address] is the store's radio. Returns whether it was. */
    fun forget(address: String): Boolean

    /** Adds or updates these contacts, marking them seen at [readAt]. */
    fun saveContacts(contacts: List<Contact>, readAt: Long)

    /** After a full contact list read at [readAt]: drops contacts the radio no longer has. */
    fun dropContactsNotSeenSince(readAt: Long)

    fun setContactsSince(since: Long)

    /**
     * Replaces the channel list with [channels]. A slot that now holds a different channel, or
     * none, loses its messages. A slot in [unanswered] (the radio did not answer for it) keeps
     * what was stored, since its channel is unknown rather than gone.
     */
    fun saveChannels(channels: List<Channel>, unanswered: Set<Int> = emptySet())

    /** Records a complete full read (every channel slot answered, the whole contact list read) at [at]. */
    fun markFullRead(at: Long)

    /** Stores [message] and trims its conversation; false when it was already stored. */
    fun addMessage(message: IncomingMessage): Boolean

    fun counts(): StoreCounts

    companion object {
        /** Decided for M2: the newest 100 messages per conversation. */
        const val KEEP_PER_CONVERSATION = 100
    }
}
