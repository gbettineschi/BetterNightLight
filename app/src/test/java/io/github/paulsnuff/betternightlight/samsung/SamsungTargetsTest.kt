package io.github.paulsnuff.betternightlight.samsung
import io.github.paulsnuff.betternightlight.domain.SunTimesInfo
import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

class SamsungTargetsTest {
    @Test fun manualNightAfterMidnightUsesThePreviousEveningTarget() {
        val profile = SamsungProfile(sunset = SamsungPhase(2, 1080, 60), night = SamsungPhase(8, 60, 60))
        assertEquals(5, resolveSamsungLevel(profile, ZonedDateTime.parse("2026-10-27T00:30:00Z")))
    }
    @Test fun unavailableSunTimesUseTheManualTimes() {
        val profile = SamsungProfile(sunset = SamsungPhase(6, 1080, 60), useSunTimes = true, latitude = 90.0, longitude = 0.0)
        assertEquals(3, resolveSamsungLevel(profile, ZonedDateTime.parse("2026-12-21T17:30:00Z")) { SunTimesInfo(null, null) })
    }
    @Test fun springGapCollisionHasDeterministicNightPriorityAcrossNeighboringDates() {
        val profile = SamsungProfile(day = SamsungPhase(0, 150, 0), sunset = SamsungPhase(2, 1080, 0), night = SamsungPhase(8, 210, 0))
        for (date in listOf("2026-03-27", "2026-03-28", "2026-03-29", "2026-03-30")) {
            assertEquals(8, resolveSamsungLevel(profile, ZonedDateTime.parse("${date}T12:00:00+02:00[Europe/Zurich]")))
        }
        assertEquals(8, resolveSamsungLevel(profile, ZonedDateTime.parse("2026-03-29T03:30:00+02:00[Europe/Zurich]")))
    }
}
