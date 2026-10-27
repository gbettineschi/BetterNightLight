package io.github.paulsnuff.betternightlight.samsung
import org.junit.Assert.*
import org.junit.Test
class SamsungTilePresentationTest {
    @Test fun anUpdateFailureCannotLookLikeSuccessfulAutomaticControl() {
        assertFalse(samsungTilePresentation(SamsungProfile(enabled = true), SamsungConnection.READY, SamsungAutomationStatus("Waiting", error = true), true).active)
    }
    @Test fun losingTheHelperCannotLeaveTheTileActive() {
        assertFalse(samsungTilePresentation(SamsungProfile(enabled = true), SamsungConnection.STOPPED, SamsungAutomationStatus(), true).active)
    }
    @Test fun aHealthyScheduleIsActiveUntilPaused() {
        assertTrue(samsungTilePresentation(SamsungProfile(enabled = true), SamsungConnection.READY, SamsungAutomationStatus(), true).active)
        assertFalse(samsungTilePresentation(SamsungProfile(enabled = true, paused = true), SamsungConnection.READY, SamsungAutomationStatus(), true).active)
    }
    @Test fun nativeManualControlIsNotShownAsActiveAutomation() {
        val override = SamsungManualOverride(SamsungManualResumePolicy.MANUAL, java.time.Instant.EPOCH, null, SamsungNativeState(true, 7, true))
        val result = samsungTilePresentation(SamsungProfile(enabled = true, manualOverride = override), SamsungConnection.READY, SamsungAutomationStatus(), true)
        assertFalse(result.active); assertTrue(result.available)
    }
    @Test fun anUnsupportedDeviceCannotShowAnAvailableActiveTile() {
        val result = samsungTilePresentation(SamsungProfile(enabled = true), SamsungConnection.READY, SamsungAutomationStatus(), false)
        assertFalse(result.active); assertFalse(result.available)
    }
}
