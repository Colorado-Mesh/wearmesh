package org.coloradomesh.wearmesh.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** The one radio the store holds data from; a single row. */
@Entity(tableName = "radio")
data class RadioRow(
    @PrimaryKey val id: Int = ONLY,
    val address: String,
    @ColumnInfo(name = "contacts_since") val contactsSince: Long = 0,
    @ColumnInfo(name = "full_read_at") val fullReadAt: Long = 0,
) {
    companion object {
        const val ONLY = 1
    }
}

/** A programmed channel slot. Names and kinds only: channel keys never leave the radio. */
@Entity(tableName = "channels")
data class ChannelRow(
    @PrimaryKey val slot: Int,
    val name: String,
    /** A [org.coloradomesh.meshcore.protocol.Channel.Kind] name. */
    val kind: String,
)

@Entity(tableName = "contacts", indices = [Index("prefix")])
data class ContactRow(
    @PrimaryKey @ColumnInfo(name = "public_key") val publicKey: String,
    /** The first 12 hex characters of the key: all a direct message gives of its sender. */
    val prefix: String,
    val name: String,
    val type: Int,
    @ColumnInfo(name = "last_advert") val lastAdvert: Long,
    /** When this contact was last read from the radio, by the watch's clock. */
    @ColumnInfo(name = "seen_at") val seenAt: Long,
)

/**
 * One received message. The unique index is what keeps a message from being stored twice: the
 * same conversation, sender, sent time and text. [sender] is never null, since SQLite treats
 * nulls as all different in a unique index.
 */
@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["conversation", "sent_at", "sender", "body"], unique = true),
        Index(value = ["conversation", "id"]),
    ],
)
data class MessageRow(
    /** Grows with each message stored, so it gives the order messages arrived in. */
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversation: String,
    val sender: String,
    val body: String,
    @ColumnInfo(name = "sent_at") val sentAt: Long,
    @ColumnInfo(name = "received_at") val receivedAt: Long,
    /** For the screens and notifications (M2 steps 3 and 4). */
    val read: Boolean = false,
)

/** A conversation's newest message and how many of its messages are unread, for Home. */
data class ConversationLast(
    val conversation: String,
    val sender: String,
    val body: String,
    val receivedAt: Long,
    val unread: Int,
    /** For a direct message, the stored contact whose key starts with the sender's prefix. */
    val contactName: String?,
)
