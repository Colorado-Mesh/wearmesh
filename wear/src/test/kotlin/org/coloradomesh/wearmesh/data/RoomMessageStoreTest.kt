package org.coloradomesh.wearmesh.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.coloradomesh.meshcore.protocol.Channel
import org.coloradomesh.meshcore.protocol.Contact
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The real database, in memory, on Robolectric's Android. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomMessageStoreTest {
    private lateinit var db: WearMeshDatabase
    private lateinit var store: RoomMessageStore
    private lateinit var other: RoomMessageStore
    private val address = "F3:9E:35:24:99:76"
    private val otherAddress = "EC:94:9C:51:86:24"

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WearMeshDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = RoomMessageStore(db, address).also { it.open() }
        other = RoomMessageStore(db, otherAddress).also { it.open() }
    }

    @After
    fun close() = db.close()

    private fun message(body: String, conversation: String = "c:0", sender: String = "Ann", sentAt: Long = 1_700_000_900) =
        IncomingMessage(conversation, sender, body, sentAt, receivedAt = 1_800_000_000_000)

    private fun bodies(conversation: String = "c:0", radio: String = address) = db.dao().messages(radio, conversation).map { it.body }

    private fun contact(key: Char, name: String) = Contact("$key".repeat(64), Contact.TYPE_COMPANION, name, 1_700_000_000, 0.0, 0.0)

    @Test
    fun theSameMessageIsStoredOnce() {
        assertTrue(store.addMessage(message("Hey!")))
        assertFalse(store.addMessage(message("Hey!")))
        assertEquals(listOf("Hey!"), bodies())
    }

    @Test
    fun aMessageWithNoSenderIsStillStoredOnce() {
        assertTrue(store.addMessage(message("meet at 5", conversation = "d:a1b2c3d4e5f6", sender = "")))
        assertFalse(store.addMessage(message("meet at 5", conversation = "d:a1b2c3d4e5f6", sender = "")))
        assertEquals(1, store.counts().messages)
    }

    @Test
    fun theSameTextSentAgainLaterOrElsewhereIsANewMessage() {
        assertTrue(store.addMessage(message("Hey!")))
        assertTrue(store.addMessage(message("Hey!", sentAt = 1_700_001_000)))
        assertTrue(store.addMessage(message("Hey!", sender = "Bo")))
        assertTrue(store.addMessage(message("Hey!", conversation = "c:1")))
        assertEquals(StoreCounts(messages = 4, conversations = 2, contacts = 0, channels = 0), store.counts())
    }

    @Test
    fun eachConversationKeepsItsNewestHundred() {
        for (i in 0 until 105) store.addMessage(message("m$i", sentAt = 1_700_000_000L + i))
        store.addMessage(message("elsewhere", conversation = "c:1"))
        val kept = bodies()
        assertEquals(MessageStore.KEEP_PER_CONVERSATION, kept.size)
        assertEquals("m5", kept.first())
        assertEquals("m104", kept.last())
        assertEquals(listOf("elsewhere"), bodies("c:1"))
    }

    @Test
    fun newestMeansLastStoredNotLatestSenderClock() {
        for (i in 0 until 100) store.addMessage(message("m$i", sentAt = 1_700_000_000L + i))
        store.addMessage(message("from a slow clock", sentAt = 1_000))
        val kept = bodies()
        assertEquals("m1", kept.first())
        assertEquals("from a slow clock", kept.last())
    }

    @Test
    fun eachRadioKeepsItsOwnHistory() {
        store.addMessage(message("on the T1"))
        store.saveChannels(listOf(Channel(0, "Public", Channel.Kind.PUBLIC)))
        store.saveContacts(listOf(contact('a', "Ann")), readAt = 1)
        other.addMessage(message("on the test radio"))
        // The same message through both radios is stored once for each.
        assertTrue(other.addMessage(message("on the T1")))
        assertEquals(listOf("on the T1"), bodies())
        assertEquals(listOf("on the test radio", "on the T1"), bodies(radio = otherAddress))
        assertEquals(StoreCounts(1, 1, 1, 1), store.counts())
        assertEquals(StoreCounts(2, 1, 0, 0), other.counts())
    }

    @Test
    fun eachRadioTrimsOnlyItsOwnConversations() {
        for (i in 0 until 100) other.addMessage(message("o$i", sentAt = 1_700_000_000L + i))
        store.addMessage(message("kept"))
        other.addMessage(message("one more", sentAt = 1))
        assertEquals(listOf("kept"), bodies())
        assertEquals(100, bodies(radio = otherAddress).size)
    }

    @Test
    fun openingARadioAgainKeepsWhatItHas() {
        store.addMessage(message("Hey!"))
        store.setContactsSince(1_700_000_600)
        store.markFullRead(5)
        store.open()
        assertEquals(1, store.counts().messages)
        assertEquals(StoredRadio(address, 1_700_000_600, 5), store.radio())
    }

    @Test
    fun aRadioNeverOpenedHasNoState() {
        assertNull(RoomMessageStore(db, "AA:BB:CC:DD:EE:FF").radio())
    }

    @Test
    fun forgettingARadioDeletesOnlyItsHistory() {
        store.addMessage(message("Hey!"))
        store.saveChannels(listOf(Channel(0, "Public", Channel.Kind.PUBLIC)))
        store.saveContacts(listOf(contact('a', "Ann")), readAt = 1)
        other.addMessage(message("stays"))
        store.forget()
        assertEquals(StoreCounts(0, 0, 0, 0), store.counts())
        assertNull(store.radio())
        assertEquals(listOf("stays"), bodies(radio = otherAddress))
        assertEquals(otherAddress, other.radio()?.address)
    }

    @Test
    fun remembersTheContactTimeAndTheLastFullRead() {
        store.setContactsSince(1_700_000_600)
        store.saveChannels(listOf(Channel(0, "Public", Channel.Kind.PUBLIC)))
        assertEquals(StoredRadio(address, 1_700_000_600, fullReadAt = 0), store.radio())
        store.markFullRead(1_800_000_000_000)
        assertEquals(StoredRadio(address, 1_700_000_600, 1_800_000_000_000), store.radio())
        assertEquals(StoredRadio(otherAddress, 0, 0), other.radio())
    }

    @Test
    fun aSlotThatNowHoldsAnotherChannelLosesItsMessages() {
        store.saveChannels(
            listOf(Channel(0, "Public", Channel.Kind.PUBLIC), Channel(3, "#testing", Channel.Kind.HASHTAG), Channel(5, "Team")),
        )
        store.addMessage(message("on Public", conversation = "c:0"))
        store.addMessage(message("on #testing", conversation = "c:3"))
        store.addMessage(message("on Team", conversation = "c:5"))
        other.addMessage(message("other radio's slot 3", conversation = "c:3"))
        store.saveChannels(listOf(Channel(0, "Public", Channel.Kind.PUBLIC), Channel(3, "#bot", Channel.Kind.HASHTAG)))
        assertEquals(listOf("on Public"), bodies("c:0"))
        assertEquals(emptyList<String>(), bodies("c:3"))
        assertEquals(emptyList<String>(), bodies("c:5"))
        assertEquals(listOf("other radio's slot 3"), bodies("c:3", radio = otherAddress))
        assertEquals(listOf("Public" to "PUBLIC", "#bot" to "HASHTAG"), db.dao().channels(address).map { it.name to it.kind })
    }

    @Test
    fun aSlotTheRadioDidNotAnswerKeepsItsChannelAndMessages() {
        store.saveChannels(listOf(Channel(0, "Public", Channel.Kind.PUBLIC), Channel(5, "Team")))
        store.addMessage(message("on Team", conversation = "c:5"))
        store.saveChannels(listOf(Channel(0, "Public", Channel.Kind.PUBLIC)), unanswered = setOf(5))
        assertEquals(listOf("on Team"), bodies("c:5"))
        assertEquals(listOf(0, 5), db.dao().channels(address).map { it.slot })
    }

    @Test
    fun aFullReadDropsContactsTheRadioNoLongerHas() {
        store.saveContacts(listOf(contact('a', "Ann"), contact('b', "Bo")), readAt = 1)
        store.saveContacts(listOf(contact('b', "Bo renamed")), readAt = 2)
        other.saveContacts(listOf(contact('a', "Ann on the other radio")), readAt = 1)
        store.dropContactsNotSeenSince(2)
        assertEquals(1, store.counts().contacts)
        assertEquals(1, other.counts().contacts)
        val bo = db.query("SELECT name, prefix FROM contacts WHERE radio = '$address'", null).use {
            it.moveToFirst()
            it.getString(0) to it.getString(1)
        }
        assertEquals("Bo renamed" to "bbbbbbbbbbbb", bo)
    }

    @Test
    fun aMessageToSendWaitsUntilItsStatusIsSet() {
        val first = store.addOutgoing("c:0", "W0RMT", "OK", 1_800_000_060_000)
        val second = store.addOutgoing("d:a1b2c3d4e5f6", "W0RMT", "On my way", 1_800_000_061_000)
        assertEquals(listOf(first, second), store.pendingOutgoing().map { it.id })
        assertTrue(other.pendingOutgoing().isEmpty())

        store.setOutgoingStatus(first, OutgoingStatus.SENT)
        assertEquals(listOf(second), store.pendingOutgoing().map { it.id })
        val sent = db.dao().messages(address, "c:0").single()
        assertTrue(sent.outgoing)
        assertTrue(sent.read)
        assertEquals("SENT", sent.status)
    }

    @Test
    fun theSameTextInTheSameSecondIsWrittenOnce() {
        assertTrue(store.addOutgoing("c:0", "W0RMT", "OK", 1_800_000_060_000) > 0)
        assertEquals(-1L, store.addOutgoing("c:0", "W0RMT", "OK", 1_800_000_060_900))
        assertTrue(store.addOutgoing("c:0", "W0RMT", "OK", 1_800_000_061_000) > 0)
    }

    @Test
    fun anotherRadiosStatusCannotBeSet() {
        val id = store.addOutgoing("c:0", "W0RMT", "OK", 1_800_000_060_000)
        other.setOutgoingStatus(id, OutgoingStatus.FAILED)
        assertEquals(listOf(id), store.pendingOutgoing().map { it.id })
    }
}
