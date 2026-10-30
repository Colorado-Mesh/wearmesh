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
/** Each conversation's newest message with its unread count; shared by the screens and the glance (M4). */
private const val LAST_MESSAGES =
    "SELECT m.conversation AS conversation, m.sender AS sender, m.body AS body, m.received_at AS receivedAt, " +
        "(SELECT COUNT(*) FROM messages u WHERE u.radio = m.radio AND u.conversation = m.conversation AND u.read = 0) AS unread, " +
        "(SELECT c.name FROM contacts c WHERE c.radio = m.radio AND c.prefix = substr(m.conversation, 3) LIMIT 1) AS contactName, " +
        "m.outgoing AS outgoing " +
        "FROM messages m WHERE m.id IN (SELECT MAX(id) FROM messages WHERE radio = :radio GROUP BY conversation)"

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

    /**
     * Contacts the radio did not list in the read at [readAt] that matter to the user: favourites,
     * and nodes with a direct-message conversation. Read before they are dropped, for the log.
     */
    @Query(
        "SELECT * FROM contacts c WHERE c.radio = :radio AND c.seen_at < :readAt AND (c.favourite = 1 OR EXISTS " +
            "(SELECT 1 FROM messages m WHERE m.radio = c.radio AND m.conversation = 'd:' || c.prefix))",
    )
    fun notableNotSeenSince(radio: String, readAt: Long): List<ContactRow>

    @Query("SELECT name FROM contacts WHERE radio = :radio AND favourite = 1 ORDER BY name")
    fun favouriteNames(radio: String): List<String>

    /** Companions (type 1) marked as favourites, by name; for New message. */
    @Query("SELECT * FROM contacts WHERE radio = :radio AND type = 1 AND favourite = 1 ORDER BY name COLLATE NOCASE")
    fun favouriteCompanionsFlow(radio: String): Flow<List<ContactRow>>

    /** The [limit] companions heard most recently that are not favourites; for New message. */
    @Query("SELECT * FROM contacts WHERE radio = :radio AND type = 1 AND favourite = 0 ORDER BY last_advert DESC LIMIT :limit")
    fun recentCompanionsFlow(radio: String, limit: Int): Flow<List<ContactRow>>

    @Query("SELECT * FROM contacts WHERE radio = :radio AND prefix = :prefix LIMIT 1")
    fun contactFlow(radio: String, prefix: String): Flow<ContactRow?>

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
    @Query(LAST_MESSAGES)
    fun lastMessagesFlow(radio: String): Flow<List<ConversationLast>>

    /** The same, once, for the complication and the Tile (M4). */
    @Query(LAST_MESSAGES)
    fun lastMessages(radio: String): List<ConversationLast>

    /** The newest unread messages received in any conversation, newest first, for the Tile (M4). */
    @Query("SELECT * FROM messages WHERE radio = :radio AND read = 0 AND outgoing = 0 ORDER BY id DESC LIMIT :limit")
    fun newestUnread(radio: String, limit: Int): List<MessageRow>

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
