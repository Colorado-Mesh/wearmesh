package org.coloradomesh.wearmesh.data

import org.coloradomesh.meshcore.protocol.CompanionClient
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncPlanTest {
    private val address = "F3:9E:35:24:99:76"
    private val now = 1_800_000_000_000L
    private val hour = 60 * 60_000L
    private val minute = 60_000L
    private val full = SyncPlan(fullRead = true, contactsSince = 0)

    private fun stored(fullReadAt: Long, since: Long = 1_700_000_500, at: String = address) = StoredRadio(at, since, fullReadAt)

    @Test
    fun theFirstSyncReadsEverything() {
        assertEquals(full, SyncPlan.decide(null, address, now, wanted = false))
    }

    @Test
    fun aRadioThatWasNeverFullyReadIsReadInFull() {
        assertEquals(full, SyncPlan.decide(stored(fullReadAt = 0), address, now, wanted = false))
    }

    @Test
    fun anotherRadioIsReadInFull() {
        assertEquals(full, SyncPlan.decide(stored(now - minute, at = "EC:94:9C:51:86:24"), address, now, wanted = false))
    }

    @Test
    fun aRecentFullReadMakesTheNextBrief() {
        val plan = SyncPlan.decide(stored(now - 15 * minute), address, now, wanted = false)
        assertEquals(SyncPlan(fullRead = false, contactsSince = 1_700_000_500), plan)
        assertEquals(CompanionClient.StartUp(contactsSince = 1_700_000_500, readChannels = false), plan.startUp)
    }

    @Test
    fun anHourOldFullReadIsDoneAgain() {
        assertEquals(full, SyncPlan.decide(stored(now - hour), address, now, wanted = false))
        assertEquals(false, SyncPlan.decide(stored(now - hour + 1), address, now, wanted = false).fullRead)
    }

    @Test
    fun aClockThatWentBackPastTheLastReadMeansAFullRead() {
        assertEquals(full, SyncPlan.decide(stored(now + hour), address, now, wanted = false))
    }

    @Test
    fun askingForAFullReadGetsOne() {
        val plan = SyncPlan.decide(stored(now - minute), address, now, wanted = true)
        assertEquals(full, plan)
        assertEquals(CompanionClient.StartUp(), plan.startUp)
    }
}
