package org.coloradomesh.wearmesh.data

import org.coloradomesh.meshcore.protocol.Channel
import org.coloradomesh.meshcore.protocol.ChannelMessage
import org.coloradomesh.meshcore.protocol.CompanionClient
import org.coloradomesh.meshcore.protocol.Contact
import org.coloradomesh.meshcore.protocol.DirectMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executor

class SyncRecorderTest {
    /** Records what was asked of it; keeps messages to answer duplicates as the real store does. */
    private class FakeStore : MessageStore {
        val calls = ArrayList<String>()
        val messages = ArrayList<IncomingMessage>()
        var failNext = false

        override fun radio(): StoredRadio? = null
        override fun useRadio(address: String) { calls.add("useRadio $address") }
        override fun forget(address: String) = false
        override fun saveContacts(contacts: List<Contact>, readAt: Long) {
            calls.add("saveContacts ${contacts.map { it.name }} at $readAt")
        }
        override fun dropContactsNotSeenSince(readAt: Long) { calls.add("dropContactsNotSeenSince $readAt") }
        override fun setContactsSince(since: Long) { calls.add("setContactsSince $since") }
        override fun saveChannels(channels: List<Channel>, unanswered: Set<Int>) {
            calls.add("saveChannels ${channels.map { it.name }}" + if (unanswered.isEmpty()) "" else " keeping $unanswered")
        }
        override fun markFullRead(at: Long) { calls.add("markFullRead $at") }
        override fun addMessage(message: IncomingMessage): Boolean {
            if (failNext) {
                failNext = false
                throw IllegalStateException("disk full")
            }
            val key = listOf(message.conversation, message.sender, message.body, message.sentAt)
            if (messages.any { listOf(it.conversation, it.sender, it.body, it.sentAt) == key }) return false
            messages.add(message)
            return true
        }
        override fun counts() = StoreCounts(messages.size, 0, 0, 0)
    }

    private val store = FakeStore()
    private val log = ArrayList<String>()
    private val queued = ArrayList<Runnable>()
    private val passedOn = ArrayList<String>()
    private var now = 1_800_000_000_000L

    /** Holds writes until [runWrites], as the real writer thread would run them later. */
    private val writer = Executor { queued.add(it) }

    private fun runWrites() {
        val batch = queued.toList()
        queued.clear()
        batch.forEach { it.run() }
    }

    private val downstream = object : CompanionClient.Listener {
        override fun onContact(contact: Contact) { passedOn.add("contact ${contact.name}") }
        override fun onChannelMessage(message: ChannelMessage) { passedOn.add("channel ${message.text}") }
        override fun onBattery(millivolts: Int) { passedOn.add("battery $millivolts") }
        override fun onQueueEmpty() { passedOn.add("queue empty") }
    }

    private fun recorder(full: Boolean) =
        SyncRecorder(store, SyncPlan(full, if (full) 0 else 1_700_000_500), writer, { log.add(it) }, { now }, downstream)

    private fun contact(name: String) = Contact("ab".repeat(32), Contact.TYPE_COMPANION, name, 0, 0.0, 0.0)

    @Test
    fun aFullReadStoresContactsInOneGoThenDropsTheRest() {
        val recorder = recorder(full = true)
        recorder.onContact(contact("Ann"))
        recorder.onContact(contact("Bo"))
        assertTrue(queued.isEmpty())
        recorder.onContactsRead(1_700_000_600)
        runWrites()
        assertEquals(
            listOf(
                "saveContacts [Ann, Bo] at $now",
                "dropContactsNotSeenSince $now",
                "setContactsSince 1700000600",
            ),
            store.calls,
        )
    }

    @Test
    fun onlyTheFirstReadOfAFullStartUpDropsContacts() {
        val recorder = recorder(full = true)
        recorder.onContactsRead(1_700_000_600)
        recorder.onContact(contact("Cy"))
        recorder.onContactsRead(1_700_000_700)
        runWrites()
        assertEquals(1, store.calls.count { it.startsWith("dropContacts") })
        assertEquals("saveContacts [Cy] at $now", store.calls[3])
    }

    @Test
    fun aBriefReadNeverDropsContacts() {
        val recorder = recorder(full = false)
        recorder.onContact(contact("Ann"))
        recorder.onContactsRead(1_700_000_600)
        runWrites()
        assertEquals(listOf("saveContacts [Ann] at $now", "setContactsSince 1700000600"), store.calls)
    }

    @Test
    fun contactsFromAnUnfinishedReadAreStoredAtTheEndWithoutDroppingAny() {
        val recorder = recorder(full = true)
        recorder.onContact(contact("Ann"))
        recorder.finish()
        runWrites()
        assertEquals(listOf("saveContacts [Ann] at $now"), store.calls)
    }

    @Test
    fun finishingWithNothingLeftWritesNothing() {
        val recorder = recorder(full = true)
        recorder.finish()
        assertTrue(queued.isEmpty())
    }

    @Test
    fun storesChannels() {
        recorder(full = false).onChannels(listOf(Channel(0, "Public", Channel.Kind.PUBLIC), Channel(3, "#testing", Channel.Kind.HASHTAG)))
        runWrites()
        assertEquals(listOf("saveChannels [Public, #testing]"), store.calls)
    }

    @Test
    fun storesAChannelMessageWithItsSenderSplitOff() {
        val recorder = recorder(full = false)
        recorder.onChannelMessage(ChannelMessage(3, "ke4iqr-1: yep another test", 1_700_000_900))
        runWrites()
        assertEquals(IncomingMessage("c:3", "ke4iqr-1", "yep another test", 1_700_000_900, now), store.messages.single())
        assertEquals(1, recorder.newMessages)
    }

    @Test
    fun storesADirectMessageUnderItsSender() {
        val recorder = recorder(full = false)
        recorder.onDirectMessage(DirectMessage("A1B2C3D4E5F6", "meet at 5: north lot", 1_700_000_950))
        runWrites()
        assertEquals(IncomingMessage("d:a1b2c3d4e5f6", "", "meet at 5: north lot", 1_700_000_950, now), store.messages.single())
    }

    @Test
    fun aMessageAlreadyStoredIsCountedAndLoggedNotKeptTwice() {
        val recorder = recorder(full = false)
        val hey = ChannelMessage(0, "🌀flux: Hey! @[🔥 W0RMT 02]", 1_700_000_900)
        recorder.onChannelMessage(hey)
        recorder.onChannelMessage(hey)
        // Sent again later, with a new time: a new message.
        recorder.onChannelMessage(hey.copy(timestamp = 1_700_001_000))
        runWrites()
        assertEquals(2, store.messages.size)
        assertEquals(2, recorder.newMessages)
        assertEquals(1, recorder.duplicates)
        assertEquals(listOf("Already stored, not kept twice: c:0, sent 1700000900, from \"🌀flux\""), log)
    }

    @Test
    fun aFailedWriteIsLoggedAndTheNextOneStillRuns() {
        val recorder = recorder(full = false)
        store.failNext = true
        recorder.onChannelMessage(ChannelMessage(0, "Ann: one", 1_700_000_900))
        recorder.onChannelMessage(ChannelMessage(0, "Ann: two", 1_700_000_901))
        runWrites()
        assertEquals(listOf("two"), store.messages.map { it.body })
        assertEquals(listOf("Storage failed: IllegalStateException: disk full"), log)
    }

    @Test
    fun passesEveryCallOnToTheCallersListener() {
        val recorder = recorder(full = true)
        recorder.onContact(contact("Ann"))
        recorder.onChannelMessage(ChannelMessage(0, "Ann: hi", 1_700_000_900))
        recorder.onBattery(3_900)
        recorder.onQueueEmpty()
        assertEquals(listOf("contact Ann", "channel Ann: hi", "battery 3900", "queue empty"), passedOn)
    }

    @Test
    fun writesWaitForTheWriterNotTheListenerCall() {
        val recorder = recorder(full = false)
        recorder.onChannelMessage(ChannelMessage(0, "Ann: hi", 1_700_000_900))
        assertTrue(store.messages.isEmpty())
        assertEquals(1, queued.size)
        runWrites()
        assertEquals(1, store.messages.size)
    }

    @Test
    fun aFullReadIsRecordedOnceContactsAndEveryChannelSlotAreIn() {
        val recorder = recorder(full = true)
        recorder.onContactsRead(1_700_000_600)
        recorder.onChannels(listOf(Channel(0, "Public", Channel.Kind.PUBLIC)))
        runWrites()
        assertEquals("markFullRead $now", store.calls.last())
        assertEquals(1, store.calls.count { it.startsWith("markFullRead") })
    }

    @Test
    fun aFullReadWithAnUnansweredSlotIsNotRecordedAndKeepsThatSlot() {
        val recorder = recorder(full = true)
        recorder.onContactsRead(1_700_000_600)
        recorder.onChannelSlotsUnanswered(setOf(7))
        recorder.onChannels(listOf(Channel(0, "Public", Channel.Kind.PUBLIC)))
        runWrites()
        assertTrue(store.calls.none { it.startsWith("markFullRead") })
        assertEquals("saveChannels [Public] keeping [7]", store.calls.last())
        assertEquals("No answer for channel slot(s) 7; keeping what was stored for them", log.single())
    }

    @Test
    fun aFullReadWhoseContactListNeverEndedIsNotRecorded() {
        val recorder = recorder(full = true)
        recorder.onChannels(listOf(Channel(0, "Public", Channel.Kind.PUBLIC)))
        recorder.finish()
        runWrites()
        assertTrue(store.calls.none { it.startsWith("markFullRead") })
    }

    @Test
    fun aBriefReadIsNeverRecordedAsFull() {
        val recorder = recorder(full = false)
        recorder.onContactsRead(1_700_000_600)
        runWrites()
        assertTrue(store.calls.none { it.startsWith("markFullRead") })
    }

    @Test
    fun nothingIsStoredAfterFinishButEverythingIsPassedOn() {
        val recorder = recorder(full = true)
        recorder.finish()
        recorder.onContact(contact("Late"))
        recorder.onContactsRead(1_700_000_600)
        recorder.onChannelMessage(ChannelMessage(0, "Ann: late", 1_700_000_900))
        recorder.finish()
        runWrites()
        assertTrue(store.calls.isEmpty())
        assertTrue(store.messages.isEmpty())
        assertEquals(listOf("contact Late", "channel Ann: late"), passedOn)
        assertEquals(
            listOf(
                "The radio sent a contact list after the sync ended; not stored",
                "The radio sent a channel message after the sync ended; not stored",
            ),
            log,
        )
    }
}
