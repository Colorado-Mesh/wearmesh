package org.coloradomesh.wearmesh.data

import org.coloradomesh.meshcore.protocol.Channel
import org.coloradomesh.meshcore.protocol.Contact
import org.coloradomesh.meshcore.protocol.DirectOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executor

class OutboxSenderTest {
    /** Keeps outgoing messages and their statuses, as the real store does. */
    private class FakeStore : MessageStore {
        val statuses = LinkedHashMap<Long, OutgoingStatus>()
        val messages = LinkedHashMap<Long, OutgoingMessage>()
        private var nextId = 1L

        override val address = "F3:9E:35:24:99:76"
        override fun radio(): StoredRadio? = null
        override fun open() {}
        override fun forget() {}
        override fun saveContacts(contacts: List<Contact>, readAt: Long) {}
        override fun dropContactsNotSeenSince(readAt: Long) = emptyList<DroppedContact>()
        override fun favouriteNames() = emptyList<String>()
        override fun setContactsSince(since: Long) {}
        override fun saveChannels(channels: List<Channel>, unanswered: Set<Int>) = 0
        override fun markFullRead(at: Long) {}
        override fun addMessage(message: IncomingMessage) = true
        override fun counts() = StoreCounts(0, 0, 0, 0)
        override fun addOutgoing(conversation: String, sender: String, body: String, at: Long): Long {
            val id = nextId++
            messages[id] = OutgoingMessage(id, conversation, body, at)
            statuses[id] = OutgoingStatus.SENDING
            return id
        }
        override fun pendingOutgoing() = messages.values.filter { statuses[it.id] == OutgoingStatus.SENDING }
        override fun setOutgoingStatus(id: Long, status: OutgoingStatus) {
            statuses[id] = status
        }
        val contacts = HashSet<String>()
        override fun hasContact(prefix: String) = prefix in contacts
        override fun retryOutgoing(id: Long, at: Long): Boolean {
            if (statuses[id] != OutgoingStatus.FAILED && statuses[id] != OutgoingStatus.NO_CONTACT) return false
            statuses[id] = OutgoingStatus.SENDING
            messages[id] = messages.getValue(id).copy(createdAt = at)
            return true
        }
    }

    /** Records what was handed to the radio; direct messages get ids from 100 up. */
    private class FakeRadio : OutboxSender.Port {
        val sent = ArrayList<String>()
        var directsRefused = false
        var onChannel: (Int, String) -> Unit = { _, _ -> }
        private var nextId = 100L

        override fun sendChannelText(channelIndex: Int, text: String) {
            sent.add("c:$channelIndex $text")
            onChannel(channelIndex, text)
        }

        override fun sendDirectText(publicKeyHex: String, text: String): Long? {
            if (directsRefused) return null
            sent.add("d:$publicKeyHex $text")
            return nextId++
        }
    }

    private val store = FakeStore()
    private val radio = FakeRadio()
    private val log = ArrayList<String>()
    private var now = 1_800_000_000_000L
    private val queued = ArrayList<Runnable>()

    /** Holds writer work until [runWrites], as the storage thread would run it later. */
    private val writer = Executor { queued.add(it) }

    private fun runWrites() {
        while (queued.isNotEmpty()) {
            val batch = queued.toList()
            queued.clear()
            batch.forEach { it.run() }
        }
    }

    private val sender = OutboxSender(store, writer, radio, { log.add(it) }, { now })

    private fun write(conversation: String, body: String, at: Long = now) = store.addOutgoing(conversation, "W0RMT", body, at)

    @Test
    fun aChannelMessageTheRadioTakesIsSent() {
        val id = write("c:3", "OK")
        var idle = 0
        sender.sendPending { idle++ }
        runWrites()
        assertEquals(listOf("c:3 OK"), radio.sent)
        assertEquals(0, idle)

        sender.onSendResult(3, "OK", accepted = true)
        runWrites()
        assertEquals(OutgoingStatus.SENT, store.statuses[id])
        assertEquals(1, idle)
        assertEquals("Outbox: Message on channel 3 sent", log.single())
    }

    @Test
    fun aRefusedChannelMessageFails() {
        val id = write("c:0", "hello")
        radio.onChannel = { slot, text -> sender.onSendResult(slot, text, accepted = false) }
        sender.sendPending()
        runWrites()
        assertEquals(OutgoingStatus.FAILED, store.statuses[id])
    }

    @Test
    fun directMessagesEndAsTheSharedCodeReports() {
        val delivered = write("d:a1b2c3d4e5f6", "On my way")
        val unconfirmed = write("d:a1b2c3d4e5f6", "Copy")
        val refused = write("d:0000000000ff", "Yes")
        sender.sendPending()
        runWrites()
        assertEquals(listOf("d:a1b2c3d4e5f6 On my way", "d:a1b2c3d4e5f6 Copy", "d:0000000000ff Yes"), radio.sent)
        sender.onDirectResult(100, DirectOutcome.DELIVERED)
        sender.onDirectResult(101, DirectOutcome.UNCONFIRMED)
        sender.onDirectResult(102, DirectOutcome.REFUSED)
        runWrites()
        assertEquals(OutgoingStatus.DELIVERED, store.statuses[delivered])
        assertEquals(OutgoingStatus.UNCONFIRMED, store.statuses[unconfirmed])
        // The store has no such contact: the radio dropped it, or never had it.
        assertEquals(OutgoingStatus.NO_CONTACT, store.statuses[refused])
        assertTrue(log.last().endsWith("failed: your radio does not have this contact (its list may be full)"))
    }

    @Test
    fun aMessageIsNeverHandedOverTwice() {
        write("c:0", "OK")
        sender.sendPending()
        sender.sendPending()
        runWrites()
        assertEquals(1, radio.sent.size)
    }

    @Test
    fun aMessageEndedButNotYetWrittenIsNotSentAgain() {
        write("c:0", "OK")
        sender.sendPending()
        runWrites()
        // The radio takes it, and before its new status is written another read lists it again.
        sender.onSendResult(0, "OK", accepted = true)
        val statusWrite = queued.toList()
        queued.clear()
        sender.sendPending()
        runWrites()
        statusWrite.forEach { it.run() }
        assertEquals(1, radio.sent.size)
    }

    @Test
    fun atMostFourAtOnceAndTheRestAsEarlierOnesEnd() {
        (1..6).forEach { write("c:0", "m$it") }
        sender.sendPending()
        runWrites()
        assertEquals(4, radio.sent.size)
        sender.onSendResult(0, "m1", accepted = true)
        runWrites()
        assertEquals(5, radio.sent.size)
        sender.onSendResult(0, "m2", accepted = true)
        runWrites()
        assertEquals(listOf("c:0 m1", "c:0 m2", "c:0 m3", "c:0 m4", "c:0 m5", "c:0 m6"), radio.sent)
    }

    @Test
    fun aMessageWaitingTooLongFailsWithoutBeingSent() {
        val old = write("c:0", "late", at = now - OutboxSender.STALE_MS - 1)
        sender.sendPending()
        runWrites()
        assertTrue(radio.sent.isEmpty())
        assertEquals(OutgoingStatus.FAILED, store.statuses[old])
        assertEquals("Outbox: Message on channel 0 failed: not sent within 30 min of writing it", log.single())
    }

    @Test
    fun whenTheConnectionEndsChannelMessagesWaitAndDirectOnesAreUnconfirmed() {
        val channel = write("c:0", "OK")
        val direct = write("d:a1b2c3d4e5f6", "Copy")
        var idle = 0
        sender.sendPending { idle++ }
        runWrites()
        sender.finish()
        runWrites()
        assertEquals(OutgoingStatus.SENDING, store.statuses[channel])
        assertEquals(OutgoingStatus.UNCONFIRMED, store.statuses[direct])
        assertEquals(1, idle)
        // Late answers change nothing.
        sender.onSendResult(0, "OK", accepted = true)
        runWrites()
        assertEquals(OutgoingStatus.SENDING, store.statuses[channel])
        assertFalse(sender.busy())
    }

    @Test
    fun aDirectMessageTheSharedCodeCannotQueueStaysWaiting() {
        val id = write("d:a1b2c3d4e5f6", "Copy")
        radio.directsRefused = true
        var idle = 0
        sender.sendPending { idle++ }
        runWrites()
        assertEquals(OutgoingStatus.SENDING, store.statuses[id])
        assertEquals(1, idle)
    }

    @Test
    fun nothingToSendIsIdleAtOnce() {
        var idle = 0
        sender.sendPending { idle++ }
        runWrites()
        assertEquals(1, idle)
    }

    @Test
    fun notIdleWhileARefillWaitsOnTheWriter() {
        (1..6).forEach { write("c:0", "m$it") }
        var idle = 0
        sender.sendPending { idle++ }
        runWrites()
        // All four end before the writer runs the refill queued after the first.
        (1..4).forEach { sender.onSendResult(0, "m$it", accepted = true) }
        assertEquals(0, idle)
        runWrites()
        assertEquals(6, radio.sent.size)
        assertEquals(0, idle)
        sender.onSendResult(0, "m5", accepted = true)
        sender.onSendResult(0, "m6", accepted = true)
        runWrites()
        assertEquals(1, idle)
    }

    @Test
    fun aDirectMessageTakenAsTheConnectionEndsIsNotConfirmed() {
        val id = write("d:a1b2c3d4e5f6", "Copy")
        val ending = object : OutboxSender.Port {
            override fun sendChannelText(channelIndex: Int, text: String) {}
            override fun sendDirectText(publicKeyHex: String, text: String): Long? {
                lateSender.finish()
                return 7
            }
        }
        lateSender = OutboxSender(store, writer, ending, { log.add(it) }, { now })
        lateSender.sendPending()
        runWrites()
        assertEquals(OutgoingStatus.UNCONFIRMED, store.statuses[id])
    }

    private lateinit var lateSender: OutboxSender

    @Test
    fun aRefusedDirectMessageToAKnownContactIsFailed() {
        store.contacts.add("a1b2c3d4e5f6")
        val id = write("d:a1b2c3d4e5f6", "Copy")
        sender.sendPending()
        runWrites()
        sender.onDirectResult(100, DirectOutcome.REFUSED)
        runWrites()
        assertEquals(OutgoingStatus.FAILED, store.statuses[id])
    }

    @Test
    fun aRetriedMessageIsSentAgainOnTheSameConnection() {
        val id = write("c:0", "OK")
        sender.sendPending()
        runWrites()
        sender.onSendResult(0, "OK", accepted = false)
        runWrites()
        assertEquals(OutgoingStatus.FAILED, store.statuses[id])

        now += 60_000
        assertTrue(store.retryOutgoing(id, now))
        sender.sendPending()
        runWrites()
        assertEquals(listOf("c:0 OK", "c:0 OK"), radio.sent)
        sender.onSendResult(0, "OK", accepted = true)
        runWrites()
        assertEquals(OutgoingStatus.SENT, store.statuses[id])
    }
}
