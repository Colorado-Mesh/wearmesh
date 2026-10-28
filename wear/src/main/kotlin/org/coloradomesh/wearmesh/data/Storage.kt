package org.coloradomesh.wearmesh.data

import android.content.Context
import org.coloradomesh.wearmesh.Prefs
import org.coloradomesh.wearmesh.SpikeLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * The app's message store, one view per radio, and the one thread that writes to it, so writes
 * from Live, Check-in and the screens never interleave.
 */
object Storage {
    /** Runs every storage call, in the order given. */
    val writer: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "wearmesh-storage") }

    private val latest = MutableStateFlow<StoreCounts?>(null)

    /** What the store holds for the radio in use, as of the last [refreshCounts]; null until then or with no radio. */
    val counts: StateFlow<StoreCounts?> = latest

    /** The store's view for the radio [address]. Cheap: the database is opened once. */
    fun store(context: Context, address: String): MessageStore = RoomMessageStore(WearMeshDatabase.get(context), address)

    /** For the screens' Flow queries; writes still go through [writer]. */
    fun dao(context: Context): StoreDao = WearMeshDatabase.get(context).dao()

    /** Queues [block] on the storage thread without waiting; a failure is logged, not thrown. */
    fun later(block: () -> Unit) = writer.execute {
        try {
            block()
        } catch (e: Exception) {
            SpikeLog.log("Storage failed: ${e::class.simpleName}: ${e.message}")
        }
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

    /** Queues a count of what is stored from the radio in use, for the screen. */
    fun refreshCounts(context: Context) {
        val address = Prefs.of(context).getString(Prefs.RADIO_ADDRESS, null)
        if (address == null) {
            latest.value = null
            return
        }
        val target = store(context, address)
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
