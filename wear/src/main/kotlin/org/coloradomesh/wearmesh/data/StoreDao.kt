package org.coloradomesh.wearmesh.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Blocking queries are called from the storage thread (see [Storage]) or from tests. The Flow
 * queries are for the screens: Room runs them on its own threads and runs them again whenever a
 * table they read changes, so a check-in in the background updates what is on screen.
 */
@Dao
interface StoreDao {
    @Query("SELECT * FROM radio WHERE id = ${RadioRow.ONLY}")
    fun radio(): RadioRow?

    @Upsert
    fun saveRadio(radio: RadioRow)

    @Query("UPDATE radio SET contacts_since = :since WHERE id = ${RadioRow.ONLY}")
    fun setContactsSince(since: Long)

    @Query("UPDATE radio SET full_read_at = :at WHERE id = ${RadioRow.ONLY}")
    fun setFullReadAt(at: Long)

    @Upsert
    fun saveContacts(contacts: List<ContactRow>)

    @Query("DELETE FROM contacts WHERE seen_at < :readAt")
    fun dropContactsNotSeenSince(readAt: Long)

    @Query("SELECT * FROM channels ORDER BY slot")
    fun channels(): List<ChannelRow>

    @Query("DELETE FROM channels")
    fun clearChannels()

    @Insert
    fun insertChannels(channels: List<ChannelRow>)

    /** Returns the new row's id, or -1 when the same message is already stored. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertMessage(message: MessageRow): Long

    /** Keeps the [keep] most recently stored messages of [conversation]. */
    @Query(
        "DELETE FROM messages WHERE conversation = :conversation AND id NOT IN " +
            "(SELECT id FROM messages WHERE conversation = :conversation ORDER BY id DESC LIMIT :keep)",
    )
    fun trim(conversation: String, keep: Int)

    @Query("DELETE FROM messages WHERE conversation = :conversation")
    fun clearConversation(conversation: String)

    @Query("SELECT * FROM messages WHERE conversation = :conversation ORDER BY id")
    fun messages(conversation: String): List<MessageRow>

    @Query("SELECT * FROM channels ORDER BY slot")
    fun channelsFlow(): Flow<List<ChannelRow>>

    /** Each conversation's newest message, its unread count, and for a direct message the contact's name. */
    @Query(
        "SELECT m.conversation AS conversation, m.sender AS sender, m.body AS body, m.received_at AS receivedAt, " +
            "(SELECT COUNT(*) FROM messages u WHERE u.conversation = m.conversation AND u.read = 0) AS unread, " +
            "(SELECT c.name FROM contacts c WHERE c.prefix = substr(m.conversation, 3) LIMIT 1) AS contactName " +
            "FROM messages m WHERE m.id IN (SELECT MAX(id) FROM messages GROUP BY conversation)",
    )
    fun lastMessagesFlow(): Flow<List<ConversationLast>>

    @Query("SELECT * FROM messages WHERE conversation = :conversation ORDER BY id")
    fun messagesFlow(conversation: String): Flow<List<MessageRow>>

    @Query("UPDATE messages SET read = 1 WHERE conversation = :conversation AND read = 0")
    fun markRead(conversation: String)

    @Query("SELECT COUNT(*) FROM messages")
    fun messageCount(): Int

    @Query("SELECT COUNT(DISTINCT conversation) FROM messages")
    fun conversationCount(): Int

    @Query("SELECT COUNT(*) FROM contacts")
    fun contactCount(): Int

    @Query("SELECT COUNT(*) FROM channels")
    fun channelCount(): Int

    @Query("DELETE FROM messages")
    fun clearMessages()

    @Query("DELETE FROM contacts")
    fun clearContacts()

    @Query("DELETE FROM radio")
    fun clearRadio()
}
