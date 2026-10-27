package io.github.paulsnuff.betternightlight.samsung

import org.junit.Assert.*
import org.junit.Test

class SamsungAboutPresentationTest {
    @Test fun samsungBackendAndShellHelperReportActualSupportAndNativeWarmth() {
        val result = samsungAboutPresentation(true, SamsungConnection.READY, SamsungNativeState(true, 6, true))
        assertTrue(result.supported); assertTrue(result.helperReady)
        assertEquals(true, result.nativeActive); assertEquals(6, result.warmth)
    }
    @Test fun aStoppedHelperDoesNotTurnACompatiblePhoneIntoUnsupportedHardware() {
        val result = samsungAboutPresentation(true, SamsungConnection.STOPPED, SamsungNativeState(false, 10, true))
        assertTrue(result.supported); assertFalse(result.helperReady)
        assertEquals(false, result.nativeActive); assertNull(result.warmth)
    }
    @Test fun missingNativeReadDoesNotClaimTheFilterIsOff() {
        val result = samsungAboutPresentation(true, SamsungConnection.NEEDS_PERMISSION, null)
        assertTrue(result.supported); assertFalse(result.helperReady)
        assertNull(result.nativeActive); assertNull(result.warmth)
    }
    @Test fun unsupportedServiceAndInvalidStrengthAreNotPresentedAsHealthyData() {
        val result = samsungAboutPresentation(false, SamsungConnection.READY, SamsungNativeState(true, 99, false))
        assertFalse(result.supported); assertNull(result.warmth)
    }
}
