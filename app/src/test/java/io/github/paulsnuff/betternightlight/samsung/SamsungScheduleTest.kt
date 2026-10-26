package io.github.paulsnuff.betternightlight.samsung

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class SamsungScheduleTest {
    private fun t(time: String, level: Int, fade: Int = 60) = TimedTarget(Instant.parse(time), level, fade)
    private val ordinary = listOf(
        t("2026-10-25T23:00:00Z", 10),
        t("2026-10-26T07:00:00Z", 0),
        t("2026-10-26T18:00:00Z", 6),
        t("2026-10-26T23:00:00Z", 10),
        t("2026-10-27T07:00:00Z", 0),
    )
    @Test fun holdsDayUntilEveningFadeStarts() {
        assertEquals(0, interpolateLevel(Instant.parse("2026-10-26T12:00:00Z"), ordinary))
        assertEquals(0, interpolateLevel(Instant.parse("2026-10-26T17:00:00Z"), ordinary))
    }
    @Test fun fadesIntoSunsetAndReachesItsTarget() {
        assertEquals(3, interpolateLevel(Instant.parse("2026-10-26T17:30:00Z"), ordinary))
        assertEquals(6, interpolateLevel(Instant.parse("2026-10-26T18:00:00Z"), ordinary))
    }
    @Test fun morningFadesBackToDay() {
        assertEquals(5, interpolateLevel(Instant.parse("2026-10-26T06:30:00Z"), ordinary))
        assertEquals(0, interpolateLevel(Instant.parse("2026-10-26T07:00:00Z"), ordinary))
    }
    @Test fun midnightDoesNotRestartTheNightFade() {
        val targets = listOf(t("2026-10-26T18:00:00Z", 2), t("2026-10-27T01:00:00Z", 8))
        assertEquals(2, interpolateLevel(Instant.parse("2026-10-27T00:00:00Z"), targets))
        assertEquals(5, interpolateLevel(Instant.parse("2026-10-27T00:30:00Z"), targets))
        assertEquals(8, interpolateLevel(Instant.parse("2026-10-27T01:00:00Z"), targets))
    }
    @Test fun zeroDurationChangesOnlyAtTheTarget() {
        val targets = listOf(t("2026-10-26T07:00:00Z", 0), t("2026-10-26T18:00:00Z", 6, 0))
        assertEquals(0, interpolateLevel(Instant.parse("2026-10-26T17:59:59Z"), targets))
        assertEquals(6, interpolateLevel(Instant.parse("2026-10-26T18:00:00Z"), targets))
    }
    @Test fun overlappingFadeIsLimitedToTheAvailableGap() {
        val targets = listOf(t("2026-10-26T17:00:00Z", 0), t("2026-10-26T18:00:00Z", 6, 120))
        assertEquals(3, interpolateLevel(Instant.parse("2026-10-26T17:30:00Z"), targets))
    }
    @Test fun equalTargetsRemainConstant() {
        val targets = listOf(t("2026-10-26T07:00:00Z", 4), t("2026-10-26T18:00:00Z", 4))
        assertEquals(4, interpolateLevel(Instant.parse("2026-10-26T17:30:00Z"), targets))
    }
    @Test fun daylightSavingUsesElapsedTimeRatherThanWallClockMinutes() {
        val zone = ZoneId.of("Europe/Zurich")
        val start = LocalDateTime.parse("2026-03-29T01:00:00").atZone(zone).toInstant()
        val end = LocalDateTime.parse("2026-03-29T04:00:00").atZone(zone).toInstant()
        assertEquals(5, interpolateLevel(Instant.parse("2026-03-29T01:00:00Z"), listOf(TimedTarget(start, 10, 0), TimedTarget(end, 0, 120))))
    }
    @Test fun rejectsInvalidTargetsBeforeInterpolation() {
        assertThrows(IllegalArgumentException::class.java) { t("2026-10-26T18:00:00Z", 11) }
        assertThrows(IllegalArgumentException::class.java) { t("2026-10-26T18:00:00Z", -1) }
        assertThrows(IllegalArgumentException::class.java) { t("2026-10-26T18:00:00Z", 5, -1) }
        assertThrows(IllegalArgumentException::class.java) { interpolateLevel(Instant.parse("2026-10-26T18:00:00Z"), listOf(t("2026-10-26T18:00:00Z", 3), t("2026-10-26T18:00:00Z", 8))) }
    }
    @Test fun manualOffDoesNotConfuseAnAutomaticDaytimeOff() {
        assertTrue(shouldPauseForManualOff(6, false))
        assertFalse(shouldPauseForManualOff(0, false))
        assertFalse(shouldPauseForManualOff(null, false))
        assertFalse(shouldPauseForManualOff(6, true))
    }
}
