package org.coloradomesh.wearmesh.data

import org.coloradomesh.meshcore.protocol.Channel
import org.coloradomesh.meshcore.protocol.Contact
import java.util.concurrent.Callable

/** [MessageStore] on the Room database. Each call is one transaction. */
class RoomMessageStore(private val db: WearMeshDatabase) : MessageStore {
    private val dao = db.dao()

    override fun radio(): StoredRadio? = dao.radio()?.let { StoredRadio(it.address, it.contactsSince, it.fullReadAt) }

    override fun useRadio(address: String) = db.runInTransaction(
        Runnable {
            if (dao.radio()?.address == address) return@Runnable
            clearAll()
            dao.saveRadio(RadioRow(address = address))
        },
    )

    override fun forget(address: String): Boolean = db.runInTransaction(
        Callable {
            if (dao.radio()?.address != address) return@Callable false
            clearAll()
            true
        },
    )

    override fun saveContacts(contacts: List<Contact>, readAt: Long) {
        if (contacts.isEmpty()) return
        dao.saveContacts(
            contacts.map {
                ContactRow(it.publicKeyHex, it.publicKeyHex.take(PREFIX_CHARS), it.name, it.type, it.lastAdvert, readAt)
            },
        )
    }

    override fun dropContactsNotSeenSince(readAt: Long) = dao.dropContactsNotSeenSince(readAt)

    override fun setContactsSince(since: Long) = dao.setContactsSince(since)

    override fun saveChannels(channels: List<Channel>, unanswered: Set<Int>) = db.runInTransaction(
        Runnable {
            val now = channels.associateBy { it.index }
            val kept = ArrayList<ChannelRow>()
            for (old in dao.channels()) {
                if (old.slot in unanswered && old.slot !in now) {
                    kept.add(old)
                    continue
                }
                val same = now[old.slot]
                if (same == null || same.name != old.name) dao.clearConversation(Conversations.channel(old.slot))
            }
            dao.clearChannels()
            dao.insertChannels(channels.map { ChannelRow(it.index, it.name, it.kind.name) } + kept)
        },
    )

    override fun markFullRead(at: Long) = dao.setFullReadAt(at)

    override fun addMessage(message: IncomingMessage): Boolean = db.runInTransaction(
        Callable {
            val row = MessageRow(
                conversation = message.conversation,
                sender = message.sender,
                body = message.body,
                sentAt = message.sentAt,
                receivedAt = message.receivedAt,
            )
            if (dao.insertMessage(row) == -1L) return@Callable false
            dao.trim(message.conversation, MessageStore.KEEP_PER_CONVERSATION)
            true
        },
    )

    override fun counts(): StoreCounts = db.runInTransaction(
        Callable { StoreCounts(dao.messageCount(), dao.conversationCount(), dao.contactCount(), dao.channelCount()) },
    )

    private fun clearAll() {
        dao.clearMessages()
        dao.clearChannels()
        dao.clearContacts()
        dao.clearRadio()
    }

    private companion object {
        const val PREFIX_CHARS = 12
    }
}
