package org.coloradomesh.wearmesh.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
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
import java.io.File

/**
 * A database made as version 2 from its exported schema, with a message in it, is opened by the
 * current version: the migration runs, Room checks the result against what it expects of each
 * table (it throws if a column is wrong), and the message is still there.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-test.db"
    private var db: WearMeshDatabase? = null

    @Before
    fun clear() {
        context.deleteDatabase(name)
    }

    @After
    fun close() {
        db?.close()
        context.deleteDatabase(name)
    }

    /** Creates the tables, indices and Room's own record exactly as version [version] did. */
    private fun createFromSchema(version: Int): SQLiteDatabase {
        // Unit tests run in the module's directory.
        val file = File("schemas/org.coloradomesh.wearmesh.data.WearMeshDatabase/$version.json")
        val schema = JSONObject(file.readText()).getJSONObject("database")
        val path = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        val sqlite = SQLiteDatabase.openOrCreateDatabase(path, null)
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            sqlite.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = entity.optJSONArray("indices") ?: continue
            for (j in 0 until indices.length()) {
                sqlite.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) sqlite.execSQL(setup.getString(i))
        sqlite.version = version
        return sqlite
    }

    @Test
    fun version2KeepsItsMessagesAndGainsSentMessages() {
        createFromSchema(2).apply {
            execSQL(
                "INSERT INTO messages (radio, conversation, sender, body, sent_at, received_at, read) " +
                    "VALUES ('F3:9E:35:24:99:76', 'c:0', 'Ann', 'Hey!', 1700000900, 1800000000000, 1)",
            )
            close()
        }
        val opened = Room.databaseBuilder(context, WearMeshDatabase::class.java, name)
            .addMigrations(WearMeshDatabase.MIGRATION_2_3, WearMeshDatabase.MIGRATION_3_4, WearMeshDatabase.MIGRATION_4_5, WearMeshDatabase.MIGRATION_5_6)
            .allowMainThreadQueries()
            .build()
        db = opened
        val kept = opened.dao().messages("F3:9E:35:24:99:76", "c:0").single()
        assertEquals("Hey!", kept.body)
        assertFalse(kept.outgoing)
        assertNull(kept.status)

        // And the new columns work.
        val store = RoomMessageStore(opened, "F3:9E:35:24:99:76")
        val id = store.addOutgoing("c:0", "W0RMT", "OK", 1_800_000_060_000)
        assertEquals(listOf(OutgoingMessage(id, "c:0", "OK", 1_800_000_060_000)), store.pendingOutgoing())
    }

    @Test
    fun version3KeepsItsContactsAndGainsTheFavouriteMark() {
        createFromSchema(3).apply {
            execSQL(
                "INSERT INTO contacts (radio, public_key, prefix, name, type, last_advert, seen_at) " +
                    "VALUES ('F3:9E:35:24:99:76', '${"a".repeat(64)}', 'aaaaaaaaaaaa', 'Ann', 1, 1700000000, 1800000000000)",
            )
            close()
        }
        val opened = Room.databaseBuilder(context, WearMeshDatabase::class.java, name)
            .addMigrations(WearMeshDatabase.MIGRATION_2_3, WearMeshDatabase.MIGRATION_3_4, WearMeshDatabase.MIGRATION_4_5, WearMeshDatabase.MIGRATION_5_6)
            .allowMainThreadQueries()
            .build()
        db = opened
        val store = RoomMessageStore(opened, "F3:9E:35:24:99:76")
        assertTrue(store.hasContact("aaaaaaaaaaaa"))
        assertEquals(emptyList<String>(), store.favouriteNames())
    }

    @Test
    fun version4KeepsItsMessagesAndGainsSignalAndHops() {
        createFromSchema(4).apply {
            execSQL(
                "INSERT INTO messages (radio, conversation, sender, body, sent_at, received_at, read, outgoing) " +
                    "VALUES ('F3:9E:35:24:99:76', 'c:0', 'Ann', 'Hey!', 1700000900, 1800000000000, 1, 0)",
            )
            close()
        }
        val opened = Room.databaseBuilder(context, WearMeshDatabase::class.java, name)
            .addMigrations(WearMeshDatabase.MIGRATION_2_3, WearMeshDatabase.MIGRATION_3_4, WearMeshDatabase.MIGRATION_4_5, WearMeshDatabase.MIGRATION_5_6)
            .allowMainThreadQueries()
            .build()
        db = opened
        val kept = opened.dao().messages("F3:9E:35:24:99:76", "c:0").single()
        assertEquals("Hey!", kept.body)
        assertNull(kept.snr)
        assertNull(kept.hops)

        // And the new columns work.
        val store = RoomMessageStore(opened, "F3:9E:35:24:99:76")
        store.addMessage(IncomingMessage("c:0", "Bo", "73", 1_700_000_950, 1_800_000_100_000, snr = 6.25, hops = 3))
        val heard = opened.dao().messages("F3:9E:35:24:99:76", "c:0").last()
        assertEquals(6.25, heard.snr!!, 1e-9)
        assertEquals(3, heard.hops)
    }

    @Test
    fun version5KeepsItsContactsAndGainsKeptContacts() {
        createFromSchema(5).apply {
            execSQL(
                "INSERT INTO contacts (radio, public_key, prefix, name, type, last_advert, seen_at, favourite) " +
                    "VALUES ('F3:9E:35:24:99:76', '${"b".repeat(64)}', 'bbbbbbbbbbbb', 'Bo', 1, 1700000000, 1800000000000, 0)",
            )
            execSQL(
                "INSERT INTO messages (radio, conversation, sender, body, sent_at, received_at, read, outgoing) " +
                    "VALUES ('F3:9E:35:24:99:76', 'd:bbbbbbbbbbbb', '', 'hi', 1700000900, 1800000000000, 1, 0)",
            )
            close()
        }
        val opened = Room.databaseBuilder(context, WearMeshDatabase::class.java, name)
            .addMigrations(WearMeshDatabase.MIGRATION_2_3, WearMeshDatabase.MIGRATION_3_4, WearMeshDatabase.MIGRATION_4_5, WearMeshDatabase.MIGRATION_5_6)
            .allowMainThreadQueries()
            .build()
        db = opened
        val store = RoomMessageStore(opened, "F3:9E:35:24:99:76")
        store.deleteConversation("d:bbbbbbbbbbbb")
        assertEquals(listOf("Bo"), store.directContacts().map { it.name })
    }
}
