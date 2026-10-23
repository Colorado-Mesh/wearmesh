package org.coloradomesh.wearmesh

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Everything the M0 spike observes, in three places: the watch screen (newest first), logcat
 * under the tag [TAG], and a text file in the app's files folder that survives the screen going
 * off and the app closing. Pull the file with
 * `adb shell run-as org.coloradomesh.wearmesh cat files/m0-log.txt`.
 */
object SpikeLog {
    data class Line(val at: Long, val text: String)

    const val TAG = "WearMesh"
    private const val FILE_NAME = "m0-log.txt"
    private const val MAX_LINES = 300

    private val shown = MutableStateFlow<List<Line>>(emptyList())
    val lines: StateFlow<List<Line>> = shown

    private var file: File? = null
    private val fileStamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val screenStamp = SimpleDateFormat("HH:mm:ss", Locale.US)

    @Synchronized
    fun init(context: Context) {
        if (file == null) file = File(context.applicationContext.filesDir, FILE_NAME)
    }

    @Synchronized
    fun log(text: String) {
        val now = System.currentTimeMillis()
        Log.i(TAG, text)
        shown.value = (listOf(Line(now, text)) + shown.value).take(MAX_LINES)
        try {
            file?.appendText("${fileStamp.format(Date(now))} $text\n")
        } catch (e: IOException) {
            Log.w(TAG, "Could not write the log file", e)
        }
    }

    @Synchronized
    fun clock(at: Long): String = screenStamp.format(Date(at))

    /** Empties the screen and starts the file again, for a clean test run. */
    @Synchronized
    fun clear() {
        shown.value = emptyList()
        try {
            file?.writeText("")
        } catch (e: IOException) {
            Log.w(TAG, "Could not clear the log file", e)
        }
    }
}
