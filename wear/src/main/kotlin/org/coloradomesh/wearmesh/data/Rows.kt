package org.coloradomesh.wearmesh.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A radio the store holds data from, with its sync state. Every other table is keyed by the
 * radio's address too, so each radio keeps its own history (decided 28 October, replacing the
 * single history of step 2).
 */
@Entity(tableName = "radios")
data class RadioRow(
    @PrimaryKey val address: String,
    @ColumnInfo(name = "contacts_since") val contactsSince: Long = 0,
    @ColumnInfo(name = "full_read_at") val fullReadAt: Long = 0,
)

/** A programmed channel slot. Names and kinds only: channel keys never leave the radio. */
@Entity(tableName = "channels", primaryKeys = ["radio", "slot"])
data class ChannelRow(
    val radio: String,
    val slot: Int,
    val name: String,
    /** A [org.coloradomesh.meshcore.protocol.Channel.Kind] name. */
    val kind: String,
)

@Entity(
    tableName = "contacts",
    primaryKeys = ["radio", "public_key"],
    indices = [Index(value = ["radio", "prefix"])],
)
data class ContactRow(
    val radio: String,
    @ColumnInfo(name = "public_key") val publicKey: String,
    /** The first 12 hex characters of the key: all a direct message gives of its sender. */
    val prefix: String,
    val name: String,
    val type: Int,
    @ColumnInfo(name = "last_advert") val lastAdvert: Long,
    /** When this contact was last read from the radio, by the watch's clock. */
    @ColumnInfo(name = "seen_at") val seenAt: Long,
    /** Marked as a favourite on the radio (schema version 4). */
    @ColumnInfo(defaultValue = "0") val favourite: Boolean = false,
)

/**
 * One received message. The unique index is what keeps a message from being stored twice: the
 * same radio, conversation, sender, sent time and text. [sender] is never null, since SQLite
 * treats nulls as all different in a unique index.
 */
@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["radio", "conversation", "sent_at", "sender", "body"], unique = true),
        Index(value = ["radio", "conversation", "id"]),
    ],
)
data class MessageRow(
    /** Grows with each message stored, so it gives the order messages arrived in. */
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The address of the radio it came through. */
    val radio: String,
    val conversation: String,
    val sender: String,
    val body: String,
    @ColumnInfo(name = "sent_at") val sentAt: Long,
    @ColumnInfo(name = "received_at") val receivedAt: Long,
    /** For the screens and notifications (M2 steps 3 and 4). A message the watch sent is read. */
    val read: Boolean = false,
    /** True for a message this watch sent (M3, schema version 3). */
    @ColumnInfo(defaultValue = "0") val outgoing: Boolean = false,
    /** For a message this watch sent, an [OutgoingStatus] name; null for one received. */
    val status: String? = null,
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
    /** Whether the newest message is one this watch sent. */
    val outgoing: Boolean = false,
)
