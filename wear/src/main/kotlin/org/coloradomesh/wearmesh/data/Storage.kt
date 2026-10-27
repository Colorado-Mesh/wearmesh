package org.coloradomesh.wearmesh.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * The app's one message store and the one thread that writes to it, so writes from Live,
 * Check-in and the screen never interleave.
 */
object Storage {
    /** Runs every storage call, in the order given. */
    val writer: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "wearmesh-storage") }

    @Volatile
    private var instance: MessageStore? = null

    private val latest = MutableStateFlow<StoreCounts?>(null)

    /** What the store holds, as of the last [refreshCounts]; null until then. */
    val counts: StateFlow<StoreCounts?> = latest

    fun store(context: Context): MessageStore = instance ?: synchronized(this) {
        instance ?: RoomMessageStore(WearMeshDatabase.get(context)).also { instance = it }
    }

    /**
     * Runs [block] on the storage thread and waits up to [timeoutMs] for it. Blocks the caller, so
     * never call it on the main thread. Throws what [block] throws, wrapped, or on timeout.
     */
    fun <T> call(timeoutMs: Long = CALL_WAIT_MS, block: () -> T): T =
        writer.submit(Callable { block() }).get(timeoutMs, TimeUnit.MILLISECONDS)

    /**
     * Waits for every write queued so far. Returns false if they did not finish within
     * [timeoutMs]; they still finish later.
     */
    fun flush(timeoutMs: Long = CALL_WAIT_MS): Boolean = try {
        call(timeoutMs) { }
        true
    } catch (e: TimeoutException) {
        false
    }

    /** Queues a count of what is stored, for the screen. */
    fun refreshCounts(context: Context) {
        val target = store(context)
        writer.execute {
            try {
                latest.value = target.counts()
            } catch (e: Exception) {
                // Only the screen's figures are missed.
            }
        }
    }

    private const val CALL_WAIT_MS = 10_000L
}
