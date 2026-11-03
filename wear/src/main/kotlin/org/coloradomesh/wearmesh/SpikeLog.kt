package org.coloradomesh.wearmesh

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The app's log, in three places: Diagnostics (newest first), logcat under the tag [TAG], and a
 * text file in the app's own files that survives the screen going off and the app closing.
 *
 * For testers (M5 step 2, decided 2 November 2026):
 * - Message text is left out unless Diagnostics › Message text in log is on ([logMessage]); the
 *   log still has who and when. Off by default, also in releases.
 * - The file is capped: past [MAX_FILE_BYTES] it becomes [OLD_FILE_NAME] and a new one starts, so
 *   about two files' worth is kept.
 * - Diagnostics › Save log copies both to the watch's Download/WearMesh folder ([save]), from
 *   where `adb pull` fetches it, as a release build's own files cannot be read with adb.
 * - A crash is written to it before the app closes ([crash]).
 */
object SpikeLog {
    data class Line(val at: Long, val text: String)

    const val TAG = "WearMesh"
    const val FILE_NAME = "wearmesh-log.txt"
    const val OLD_FILE_NAME = "wearmesh-log.old.txt"

    /** Where the log was before M5; moved on first start. */
    private const val M0_FILE_NAME = "m0-log.txt"
    private const val MAX_FILE_BYTES = 2_000_000L
    private const val MAX_LINES = 300

    /** The folder under Download that [save] writes to. */
    const val SAVE_FOLDER = "WearMesh"

    private val shown = MutableStateFlow<List<Line>>(emptyList())
    val lines: StateFlow<List<Line>> = shown

    private val showingText = MutableStateFlow(false)

    /** Whether message text goes into the log; see [setShowText]. */
    val showText: StateFlow<Boolean> = showingText

    private var file: File? = null
    private var oldFile: File? = null
    private val fileStamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val screenStamp = SimpleDateFormat("HH:mm:ss", Locale.US)

    /**
     * Sets up the log files and loads the saved message-text preference once. Moves the old M0
     * file if the current file does not exist; a failed move leaves the old file in place.
     * File access security exceptions propagate.
     */
    @Synchronized
    fun init(context: Context) {
        if (file != null) return
        val app = context.applicationContext
        val dir = app.filesDir
        val current = File(dir, FILE_NAME)
        val m0 = File(dir, M0_FILE_NAME)
        if (m0.exists() && !current.exists() && !m0.renameTo(current)) Log.w(TAG, "Could not move the old log file")
        file = current
        oldFile = File(dir, OLD_FILE_NAME)
        showingText.value = Prefs.of(app).getBoolean(Prefs.LOG_MESSAGE_TEXT, false)
    }

    /**
     * Adds text to Diagnostics and logcat, and to the current file after [init]. Before appending,
     * rollover is attempted if the file is larger than [MAX_FILE_BYTES]. File write IO failures are
     * caught; file access security exceptions propagate.
     */
    @Synchronized
    fun log(text: String) {
        val now = System.currentTimeMillis()
        Log.i(TAG, text)
        shown.value = (listOf(Line(now, text)) + shown.value).take(MAX_LINES)
        val target = file ?: return
        try {
            if (target.length() > MAX_FILE_BYTES) {
                oldFile?.let { old -> roll(target, old) }
            }
            target.appendText("${fileStamp.format(Date(now))} $text\n")
        } catch (e: IOException) {
            Log.w(TAG, "Could not write the log file", e)
        }
    }

    /**
     * Makes [current] the older file, preserving the existing older file under a temporary name
     * during the move. If moving [current] fails, attempts to restore the previous older file.
     * File access security exceptions propagate.
     */
    private fun roll(current: File, old: File) {
        val previous = File(old.parentFile, "$OLD_FILE_NAME.previous")
        if (old.exists() && !old.renameTo(previous)) return
        if (current.renameTo(old)) {
            previous.delete()
        } else if (previous.exists()) {
            previous.renameTo(old)
        }
    }

    /**
     * Logs a received message as [prefix] then its text, shown or hidden as the switch is at this
     * moment. The switch is read and the line written under the log's lock, so a message cannot be
     * written in clear just after the switch is turned off.
     */
    @Synchronized
    fun logMessage(prefix: String, text: String) = log(prefix + LogPrivacy.text(text, showingText.value))

    /** Turns message text in the log on or off, from the next line on; lines already written stay as they are. */
    @Synchronized
    fun setShowText(context: Context, show: Boolean) {
        Prefs.of(context).edit().putBoolean(Prefs.LOG_MESSAGE_TEXT, show).apply()
        showingText.value = show
        log("Log: message text ${if (show) "shown" else "hidden"} from now on")
    }

    @Synchronized
    fun clock(at: Long): String = screenStamp.format(Date(at))

    /** Attempts to write an uncaught [error] from [thread] to the log; failures are caught. */
    fun crash(thread: Thread, error: Throwable) {
        try {
            log(CrashReport.lines(thread.name, error).joinToString("\n"))
        } catch (e: Throwable) {
            Log.e(TAG, "Could not log the crash", e)
        }
    }

    /**
     * Clears Diagnostics and attempts to empty the log files. A file IO failure stops further
     * clearing and is caught; file access security exceptions propagate.
     */
    @Synchronized
    fun clear() {
        shown.value = emptyList()
        try {
            // If the older file cannot be deleted, it is emptied, so its lines go either way.
            oldFile?.let { old -> if (old.exists() && !old.delete()) old.writeText("") }
            file?.writeText("")
        } catch (e: IOException) {
            Log.w(TAG, "Could not clear the log file", e)
        }
    }

    /**
     * Copies the log, older part first, to Download/[SAVE_FOLDER] on the watch, named for the time.
     * Returns the file's name. Returns null for a read IO failure, a failed or rejected MediaStore
     * insert, or an exception writing the copy. A file access security exception during the read
     * can propagate. Call off the main thread.
     */
    fun save(context: Context): String? {
        val name = LogPrivacy.saveName(System.currentTimeMillis())
        val text = synchronized(this) {
            try {
                listOfNotNull(oldFile, file).filter { it.exists() }.joinToString("") { it.readText() }
            } catch (e: IOException) {
                Log.w(TAG, "Could not read the log file", e)
                return null
            }
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$SAVE_FOLDER")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.applicationContext.contentResolver
        val uri = try {
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not make the saved log", e)
            null
        } ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } ?: throw IOException("no stream")
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            log("Log: saved to Download/$SAVE_FOLDER/$name")
            name
        } catch (e: Exception) {
            Log.w(TAG, "Could not write the saved log", e)
            try {
                resolver.delete(uri, null, null)
            } catch (cleanup: RuntimeException) {
                Log.w(TAG, "Could not remove the unfinished saved log", cleanup)
            }
            null
        }
    }
}
