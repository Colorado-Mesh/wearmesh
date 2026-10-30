package org.coloradomesh.wearmesh.data

import org.coloradomesh.meshcore.protocol.Channel
import org.coloradomesh.meshcore.protocol.Contact
import java.util.concurrent.Callable

/** [MessageStore] on the Room database, for the radio [address]. Each call is one transaction. */
class RoomMessageStore(private val db: WearMeshDatabase, override val address: String) : MessageStore {
    private val dao = db.dao()

    override fun radio(): StoredRadio? = dao.radio(address)?.let { StoredRadio(it.address, it.contactsSince, it.fullReadAt) }

    override fun open() = dao.addRadio(RadioRow(address))

    override fun forget() = db.runInTransaction(
        Runnable {
            dao.clearMessages(address)
            dao.clearChannels(address)
            dao.clearContacts(address)
            dao.clearRadio(address)
        },
    )

    override fun saveContacts(contacts: List<Contact>, readAt: Long) {
        if (contacts.isEmpty()) return
        dao.saveContacts(
            contacts.map {
                ContactRow(address, it.publicKeyHex, it.publicKeyHex.take(PREFIX_CHARS), it.name, it.type, it.lastAdvert, readAt, it.isFavourite)
            },
        )
    }

    override fun dropContactsNotSeenSince(readAt: Long): List<DroppedContact> = db.runInTransaction(
        Callable {
            val notable = dao.notableNotSeenSince(address, readAt).map { DroppedContact(it.name, it.prefix, it.favourite) }
            dao.dropContactsNotSeenSince(address, readAt)
            notable
        },
    )

    override fun favouriteNames(): List<String> = dao.favouriteNames(address)

    override fun setContactsSince(since: Long) = dao.setContactsSince(address, since)

    override fun saveChannels(channels: List<Channel>, unanswered: Set<Int>): Int = db.runInTransaction(
        Callable {
            var dropped = 0
            val now = channels.associateBy { it.index }
            val kept = ArrayList<ChannelRow>()
            for (old in dao.channels(address)) {
                if (old.slot in unanswered && old.slot !in now) {
                    kept.add(old)
                    continue
                }
                val same = now[old.slot]
                if (same == null || same.name != old.name) {
                    val conversation = Conversations.channel(old.slot)
                    dropped += dao.pendingCount(address, conversation)
                    dao.clearConversation(address, conversation)
                }
            }
            dao.clearChannels(address)
            dao.insertChannels(channels.map { ChannelRow(address, it.index, it.name, it.kind.name) } + kept)
            dropped
        },
    )

    override fun markFullRead(at: Long) = dao.setFullReadAt(address, at)

    override fun addMessage(message: IncomingMessage): Boolean = db.runInTransaction(
        Callable {
            val row = MessageRow(
                radio = address,
                conversation = message.conversation,
                sender = message.sender,
                body = message.body,
                sentAt = message.sentAt,
                receivedAt = message.receivedAt,
            )
            if (dao.insertMessage(row) == -1L) return@Callable false
            dao.trim(address, message.conversation, MessageStore.KEEP_PER_CONVERSATION)
            true
        },
    )

    override fun counts(): StoreCounts = db.runInTransaction(
        Callable {
            StoreCounts(dao.messageCount(address), dao.conversationCount(address), dao.contactCount(address), dao.channelCount(address))
        },
    )

    override fun addOutgoing(conversation: String, sender: String, body: String, at: Long): Long = db.runInTransaction(
        Callable {
            val row = MessageRow(
                radio = address,
                conversation = conversation,
                sender = sender,
                body = body,
                sentAt = at / 1000,
                receivedAt = at,
                read = true,
                outgoing = true,
                status = OutgoingStatus.SENDING.name,
            )
            val id = dao.insertMessage(row)
            if (id != -1L) dao.trim(address, conversation, MessageStore.KEEP_PER_CONVERSATION)
            id
        },
    )

    override fun pendingOutgoing(): List<OutgoingMessage> =
        dao.pendingOutgoing(address).map { OutgoingMessage(it.id, it.conversation, it.body, it.receivedAt) }

    override fun setOutgoingStatus(id: Long, status: OutgoingStatus) = dao.setStatus(address, id, status.name)

    override fun retryOutgoing(id: Long, at: Long): Boolean = dao.retry(address, id, at) > 0

    override fun hasContact(prefix: String): Boolean = dao.hasContact(address, prefix.lowercase())

    private companion object {
        const val PREFIX_CHARS = 12
    }
}
