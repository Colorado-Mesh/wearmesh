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
    private val address = "F3:9E:35:24:99:76"
    private val other = "EC:94:9C:51:86:24"

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WearMeshDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = RoomMessageStore(db)
        store.useRadio(address)
    }

    @After
    fun close() = db.close()

    private fun message(body: String, conversation: String = "c:0", sender: String = "Ann", sentAt: Long = 1_700_000_900) =
        IncomingMessage(conversation, sender, body, sentAt, receivedAt = 1_800_000_000_000)

    private fun bodies(conversation: String = "c:0") = db.dao().messages(conversation).map { it.body }

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
        // A sender with a clock years behind still counts as new when it arrives.
        for (i in 0 until 100) store.addMessage(message("m$i", sentAt = 1_700_000_000L + i))
        store.addMessage(message("from a slow clock", sentAt = 1_000))
        val kept = bodies()
        assertEquals("m1", kept.first())
        assertEquals("from a slow clock", kept.last())
    }

    @Test
    fun theSameRadioKeepsItsData() {
        store.addMessage(message("Hey!"))
        store.useRadio(address)
        assertEquals(1, store.counts().messages)
    }

    @Test
    fun anotherRadioStartsAfresh() {
        store.addMessage(message("Hey!"))
        store.saveContacts(listOf(contact('a', "Ann")), readAt = 1)
        store.saveChannels(listOf(Channel(0, "Public", Channel.Kind.PUBLIC)))
        store.markFullRead(5)
        store.setContactsSince(1_700_000_600)
        store.useRadio(other)
        assertEquals(StoreCounts(0, 0, 0, 0), store.counts())
        assertEquals(StoredRadio(other, contactsSince = 0, fullReadAt = 0), store.radio())
    }

    @Test
    fun forgettingTheRadioDeletesEverything() {
        store.addMessage(message("Hey!"))
        assertTrue(store.forget(address))
        assertEquals(StoreCounts(0, 0, 0, 0), store.counts())
        assertNull(store.radio())
    }

    @Test
    fun forgettingAnotherRadioKeepsThisOnesData() {
        store.addMessage(message("Hey!"))
        assertFalse(store.forget(other))
        assertEquals(1, store.counts().messages)
        assertEquals(address, store.radio()?.address)
    }

    @Test
    fun remembersTheContactTimeAndTheLastFullRead() {
        store.setContactsSince(1_700_000_600)
        store.saveChannels(listOf(Channel(0, "Public", Channel.Kind.PUBLIC)))
        assertEquals(StoredRadio(address, 1_700_000_600, fullReadAt = 0), store.radio())
        store.markFullRead(1_800_000_000_000)
        assertEquals(StoredRadio(address, 1_700_000_600, 1_800_000_000_000), store.radio())
    }

    @Test
    fun aSlotThatNowHoldsAnotherChannelLosesItsMessages() {
        store.saveChannels(
            listOf(Channel(0, "Public", Channel.Kind.PUBLIC), Channel(3, "#testing", Channel.Kind.HASHTAG), Channel(5, "Team")),
        )
        store.addMessage(message("on Public", conversation = "c:0"))
        store.addMessage(message("on #testing", conversation = "c:3"))
        store.addMessage(message("on Team", conversation = "c:5"))
        store.saveChannels(listOf(Channel(0, "Public", Channel.Kind.PUBLIC), Channel(3, "#bot", Channel.Kind.HASHTAG)))
        assertEquals(listOf("on Public"), bodies("c:0"))
        assertEquals(emptyList<String>(), bodies("c:3"))
        assertEquals(emptyList<String>(), bodies("c:5"))
        assertEquals(listOf("Public" to "PUBLIC", "#bot" to "HASHTAG"), db.dao().channels().map { it.name to it.kind })
    }

    @Test
    fun aFullReadDropsContactsTheRadioNoLongerHas() {
        store.saveContacts(listOf(contact('a', "Ann"), contact('b', "Bo")), readAt = 1)
        store.saveContacts(listOf(contact('b', "Bo renamed")), readAt = 2)
        store.dropContactsNotSeenSince(2)
        assertEquals(1, store.counts().contacts)
        val bo = db.query("SELECT name, prefix FROM contacts", null).use {
            it.moveToFirst()
            it.getString(0) to it.getString(1)
        }
        assertEquals("Bo renamed" to "bbbbbbbbbbbb", bo)
    }

    @Test
    fun aSlotTheRadioDidNotAnswerKeepsItsChannelAndMessages() {
        store.saveChannels(listOf(Channel(0, "Public", Channel.Kind.PUBLIC), Channel(5, "Team")))
        store.addMessage(message("on Team", conversation = "c:5"))
        store.saveChannels(listOf(Channel(0, "Public", Channel.Kind.PUBLIC)), unanswered = setOf(5))
        assertEquals(listOf("on Team"), bodies("c:5"))
        assertEquals(listOf(0, 5), db.dao().channels().map { it.slot })
    }
}
