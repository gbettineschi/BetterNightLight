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
    @Test fun anUnsupportedDeviceCannotShowAnAvailableActiveTile() {
        val result = samsungTilePresentation(SamsungProfile(enabled = true), SamsungConnection.READY, SamsungAutomationStatus(), false)
        assertFalse(result.active); assertFalse(result.available)
    }
}
