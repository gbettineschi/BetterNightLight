package io.github.paulsnuff.betternightlight.samsung

import org.junit.Assert.*
import org.junit.Test

class SamsungTileActionTest {
    @Test fun tileCanAlwaysPauseEvenWhenTargetsNeedCorrection() {
        val profile = SamsungProfile(enabled = true, useSunTimes = true, lastAppliedLevel = 6)
        val result = samsungTileAction(profile)
        assertNotNull(result)
        assertTrue(result!!.paused)
        assertTrue(result.enabled)
        assertNull(result.lastAppliedLevel)
    }
    @Test fun invalidTargetsCannotBeEnabledOrResumed() {
        assertNull(samsungTileAction(SamsungProfile(useSunTimes = true)))
        assertNull(samsungTileAction(SamsungProfile(enabled = true, paused = true, useSunTimes = true)))
    }
}
