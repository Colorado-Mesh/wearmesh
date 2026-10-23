package org.coloradomesh.wearmesh

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.coloradomesh.meshcore.protocol.Channel

/** What the screen shows about the radio link. Written by [SpikeService], read by [MainActivity]. */
object SpikeState {
    data class Snapshot(
        /** True while the service holds, or is trying to hold, a link. */
        val running: Boolean = false,
        val link: String = "Not connected",
        val radioName: String? = null,
        val firmware: String? = null,
        /** The largest frame the link carries, once it is up. */
        val packetSize: Int? = null,
        val channels: List<Channel> = emptyList(),
        val contacts: Int = 0,
        val messages: Int = 0,
        val radioMillivolts: Int? = null,
    )

    private val current = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = current

    fun update(change: (Snapshot) -> Snapshot) = current.update(change)
}
