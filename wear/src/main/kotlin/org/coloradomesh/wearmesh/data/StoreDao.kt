package org.coloradomesh.wearmesh.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert

/** Blocking queries, called from the storage thread (see [Storage]) or from tests. */
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
