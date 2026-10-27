package org.coloradomesh.wearmesh.data

import org.coloradomesh.meshcore.protocol.CompanionClient

/**
 * How much one connection reads from the radio before collecting messages.
 *
 * A full read takes every channel slot (40 on the Heltec T1) and the whole contact list (about
 * 350), which was most of each check-in's 7 s in the second overnight test. A brief read takes
 * only contacts changed since the last read and no channel slots, relying on what is stored.
 */
data class SyncPlan(val fullRead: Boolean, val contactsSince: Long) {
    val startUp: CompanionClient.StartUp
        get() = if (fullRead) CompanionClient.StartUp() else CompanionClient.StartUp(contactsSince, readChannels = false)

    companion object {
        /**
         * How often a brief check-in becomes a full read anyway: picks up renamed or new channels,
         * and drops contacts the radio has removed, which a read of changes never reports.
         */
        const val FULL_READ_EVERY_MS = 24 * 60 * 60_000L

        /**
         * A full read when asked for ([wanted]: Live, or "Check now"), when the store holds
         * nothing from this radio, or when the last full read is a day old or the clock has gone
         * back past it; otherwise a brief one.
         */
        fun decide(stored: StoredRadio?, address: String, now: Long, wanted: Boolean): SyncPlan {
            val known = stored != null && stored.address == address && stored.fullReadAt > 0
            if (wanted || !known) return SyncPlan(fullRead = true, contactsSince = 0)
            val age = now - stored!!.fullReadAt
            if (age < 0 || age >= FULL_READ_EVERY_MS) return SyncPlan(fullRead = true, contactsSince = 0)
            return SyncPlan(fullRead = false, contactsSince = stored.contactsSince)
        }
    }
}
