package io.github.paulsnuff.betternightlight.samsung

import io.github.paulsnuff.betternightlight.domain.SunTimesInfo
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class SamsungManualControlTest {
    private fun at(text: String) = Instant.parse(text)
    private val targets = listOf(
        TimedTarget(at("2026-10-27T07:00:00Z"), 0, 60),
        TimedTarget(at("2026-10-27T18:00:00Z"), 5, 60),
        TimedTarget(at("2026-10-27T23:00:00Z"), 10, 90),
        TimedTarget(at("2026-10-28T07:00:00Z"), 0, 60),
    )
    @Test fun nextChangeIsTheNextFadeStart() {
        assertEquals(at("2026-10-27T17:00:00Z"), nextScheduledTransition(at("2026-10-27T16:30:00Z"), targets))
    }
    @Test fun changingDuringAFadeSkipsItsIndividualStepsAndEnd() {
        assertEquals(at("2026-10-27T21:30:00Z"), nextScheduledTransition(at("2026-10-27T17:30:00Z"), targets))
        assertEquals(at("2026-10-27T21:30:00Z"), nextScheduledTransition(at("2026-10-27T17:00:00Z"), targets))
    }
    @Test fun overnightResumesAtTheMorningFadeStart() {
        assertEquals(at("2026-10-28T06:00:00Z"), nextScheduledTransition(at("2026-10-27T22:15:00Z"), targets))
    }
    @Test fun instantAndUnchangedPhasesUseTheirAnchor() {
        val instant = targets.map { it.copy(fadeMinutes = 0) }
        assertEquals(at("2026-10-27T18:00:00Z"), nextScheduledTransition(at("2026-10-27T17:30:00Z"), instant))
        assertEquals(at("2026-10-27T18:00:00Z"), nextScheduledTransition(at("2026-10-27T16:30:00Z"), targets.map { it.copy(level = 0) }))
    }
    @Test fun overlappingFadeIsClippedToThePreviousAnchor() {
        val clipped = listOf(targets[0], targets[1], TimedTarget(at("2026-10-27T19:00:00Z"), 10, 120))
        assertEquals(at("2026-10-27T18:00:00Z"), nextScheduledTransition(at("2026-10-27T17:30:00Z"), clipped))
    }
    @Test fun daylightSavingUsesActualElapsedFadeMinutes() {
        val dst = listOf(
            TimedTarget(at("2026-03-28T22:00:00Z"), 10, 0),
            TimedTarget(at("2026-03-29T01:30:00Z"), 0, 90),
        )
        assertEquals(at("2026-03-29T00:00:00Z"), nextScheduledTransition(at("2026-03-28T23:30:00Z"), dst))
    }
    @Test fun nextTransitionIncludesSolarOffsets() {
        val profile = SamsungProfile(useSunTimes = true, latitude = 46.5, longitude = 6.6, sunsetOffsetMinutes = 45)
        val next = nextSamsungTransition(profile, ZonedDateTime.parse("2026-10-27T17:30:00Z")) { date ->
            SunTimesInfo(date.atTime(7,0).atZone(ZoneOffset.UTC), date.atTime(18,0).atZone(ZoneOffset.UTC))
        }
        assertEquals(at("2026-10-27T17:45:00Z"), next)
    }
    @Test fun detectsSliderAndToggleChangesButNotOwnWriteIntent() {
        assertTrue(hasNativeManualChange(6, SamsungNativeState(true, 8, true)))
        assertTrue(hasNativeManualChange(6, SamsungNativeState(false, 6, true)))
        assertTrue(hasNativeManualChange(0, SamsungNativeState(true, 0, true)))
        assertTrue(hasNativeManualChange(6, SamsungNativeState(true, 6, false)))
        assertFalse(hasNativeManualChange(6, SamsungNativeState(true, 6, true)))
        assertFalse(hasNativeManualChange(null, SamsungNativeState(false, 0, true)))
    }
}
