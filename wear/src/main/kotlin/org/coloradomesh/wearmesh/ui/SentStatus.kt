package org.coloradomesh.wearmesh.ui

import org.coloradomesh.wearmesh.data.OutgoingStatus

/** How a message the watch sent is shown under it in its conversation (M3). */
object SentStatus {
    /** What "Send test message" sends (M3 step 1). */
    const val TEST_TEXT = "WearMesh test"

    /** The words for a stored status; null for a received message (no status). */
    fun label(status: String?): String? = when (status?.let { name -> OutgoingStatus.entries.firstOrNull { it.name == name } }) {
        null -> null
        OutgoingStatus.SENDING -> "Sending…"
        OutgoingStatus.SENT -> "Sent to radio"
        OutgoingStatus.DELIVERED -> "Delivered"
        OutgoingStatus.UNCONFIRMED -> "Not confirmed"
        OutgoingStatus.FAILED -> "Failed"
    }
}
