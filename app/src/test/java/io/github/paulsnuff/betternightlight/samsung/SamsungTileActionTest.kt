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
    @Test fun tileResumesAndClearsANativeManualOverride() {
        val override = SamsungManualOverride(SamsungManualResumePolicy.MANUAL, java.time.Instant.EPOCH, null, SamsungNativeState(true, 7, true))
        val result = samsungTileAction(SamsungProfile(enabled = true, manualOverride = override))!!
        assertTrue(result.enabled); assertFalse(result.isPaused)
        assertNull(result.manualOverride); assertNull(result.lastAppliedLevel)
    }
    @Test fun anInvalidScheduleCannotResumeThroughAManualOverrideTile() {
        val override = SamsungManualOverride(SamsungManualResumePolicy.MANUAL, java.time.Instant.EPOCH, null, SamsungNativeState(true, 7, true))
        assertNull(samsungTileAction(SamsungProfile(enabled = true, useSunTimes = true, manualOverride = override)))
    }
    @Test fun invalidTargetsCannotBeEnabledOrResumed() {
        assertNull(samsungTileAction(SamsungProfile(useSunTimes = true)))
        assertNull(samsungTileAction(SamsungProfile(enabled = true, paused = true, useSunTimes = true)))
    }
}
