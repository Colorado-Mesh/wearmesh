package org.coloradomesh.wearmesh.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Every query is for one radio, named by its address. Blocking queries are called from the
 * storage thread (see [Storage]) or from tests. The Flow queries are for the screens: Room runs
 * them on its own threads and runs them again whenever a table they read changes, so a check-in
 * in the background updates what is on screen.
 */
@Dao
interface StoreDao {
    @Query("SELECT * FROM radios WHERE address = :radio")
    fun radio(radio: String): RadioRow?

    /** Adds the radio's row if it has none; an existing one is kept as it is. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun addRadio(row: RadioRow)

    @Query("UPDATE radios SET contacts_since = :since WHERE address = :radio")
    fun setContactsSince(radio: String, since: Long)

    @Query("UPDATE radios SET full_read_at = :at WHERE address = :radio")
    fun setFullReadAt(radio: String, at: Long)

    @Upsert
    fun saveContacts(contacts: List<ContactRow>)

    @Query("DELETE FROM contacts WHERE radio = :radio AND seen_at < :readAt")
    fun dropContactsNotSeenSince(radio: String, readAt: Long)

    @Query("SELECT * FROM channels WHERE radio = :radio ORDER BY slot")
    fun channels(radio: String): List<ChannelRow>

    @Insert
    fun insertChannels(channels: List<ChannelRow>)

    /** Returns the new row's id, or -1 when the same message is already stored. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertMessage(message: MessageRow): Long

    /**
     * Keeps the [keep] most recently stored messages of [conversation] on [radio], and any message
     * still waiting to send, however old.
     */
    @Query(
        "DELETE FROM messages WHERE radio = :radio AND conversation = :conversation " +
            "AND NOT (outgoing = 1 AND status = 'SENDING') AND id NOT IN " +
            "(SELECT id FROM messages WHERE radio = :radio AND conversation = :conversation ORDER BY id DESC LIMIT :keep)",
    )
    fun trim(radio: String, conversation: String, keep: Int)

    @Query("DELETE FROM messages WHERE radio = :radio AND conversation = :conversation")
    fun clearConversation(radio: String, conversation: String)

    @Query("SELECT * FROM messages WHERE radio = :radio AND conversation = :conversation ORDER BY id")
    fun messages(radio: String, conversation: String): List<MessageRow>

    @Query("SELECT * FROM channels WHERE radio = :radio ORDER BY slot")
    fun channelsFlow(radio: String): Flow<List<ChannelRow>>

    /** Each conversation's newest message, its unread count, and for a direct message the contact's name. */
    @Query(
        "SELECT m.conversation AS conversation, m.sender AS sender, m.body AS body, m.received_at AS receivedAt, " +
            "(SELECT COUNT(*) FROM messages u WHERE u.radio = m.radio AND u.conversation = m.conversation AND u.read = 0) AS unread, " +
            "(SELECT c.name FROM contacts c WHERE c.radio = m.radio AND c.prefix = substr(m.conversation, 3) LIMIT 1) AS contactName, " +
            "m.outgoing AS outgoing " +
            "FROM messages m WHERE m.id IN (SELECT MAX(id) FROM messages WHERE radio = :radio GROUP BY conversation)",
    )
    fun lastMessagesFlow(radio: String): Flow<List<ConversationLast>>

    @Query("SELECT * FROM messages WHERE radio = :radio AND conversation = :conversation ORDER BY id")
    fun messagesFlow(radio: String, conversation: String): Flow<List<MessageRow>>

    /** A conversation's newest unread messages, newest first, for its notification. */
    @Query(
        "SELECT * FROM messages WHERE radio = :radio AND conversation = :conversation AND read = 0 " +
            "ORDER BY id DESC LIMIT :limit",
    )
    fun unreadMessages(radio: String, conversation: String, limit: Int): List<MessageRow>

    /** Messages this watch has still to send, oldest first. */
    @Query("SELECT * FROM messages WHERE radio = :radio AND outgoing = 1 AND status = 'SENDING' ORDER BY id")
    fun pendingOutgoing(radio: String): List<MessageRow>

    @Query("SELECT COUNT(*) FROM messages WHERE radio = :radio AND conversation = :conversation AND outgoing = 1 AND status = 'SENDING'")
    fun pendingCount(radio: String, conversation: String): Int

    @Query("UPDATE messages SET status = :status WHERE radio = :radio AND id = :id AND outgoing = 1")
    fun setStatus(radio: String, id: Long, status: String)

    /** A failed message waits to send again, as if written at [at]; returns the rows changed. */
    @Query(
        "UPDATE messages SET status = 'SENDING', received_at = :at " +
            "WHERE radio = :radio AND id = :id AND outgoing = 1 AND status IN ('FAILED', 'NO_CONTACT')",
    )
    fun retry(radio: String, id: Long, at: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM contacts WHERE radio = :radio AND prefix = :prefix)")
    fun hasContact(radio: String, prefix: String): Boolean

    @Query("SELECT * FROM messages WHERE radio = :radio AND id = :id")
    fun messageFlow(radio: String, id: Long): Flow<MessageRow?>

    @Query("SELECT name FROM contacts WHERE radio = :radio AND prefix = :prefix LIMIT 1")
    fun contactName(radio: String, prefix: String): String?

    @Query("UPDATE messages SET read = 1 WHERE radio = :radio AND conversation = :conversation AND read = 0")
    fun markRead(radio: String, conversation: String)

    @Query("SELECT COUNT(*) FROM messages WHERE radio = :radio")
    fun messageCount(radio: String): Int

    @Query("SELECT COUNT(DISTINCT conversation) FROM messages WHERE radio = :radio")
    fun conversationCount(radio: String): Int

    @Query("SELECT COUNT(*) FROM contacts WHERE radio = :radio")
    fun contactCount(radio: String): Int

    @Query("SELECT COUNT(*) FROM channels WHERE radio = :radio")
    fun channelCount(radio: String): Int

    @Query("DELETE FROM messages WHERE radio = :radio")
    fun clearMessages(radio: String)

    @Query("DELETE FROM channels WHERE radio = :radio")
    fun clearChannels(radio: String)

    @Query("DELETE FROM contacts WHERE radio = :radio")
    fun clearContacts(radio: String)

    @Query("DELETE FROM radios WHERE address = :radio")
    fun clearRadio(radio: String)
}
