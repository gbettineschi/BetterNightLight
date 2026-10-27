package io.github.paulsnuff.betternightlight.samsung

import io.github.paulsnuff.betternightlight.domain.SunTimesInfo
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class SamsungSolarOffsetsTest {
    private val solar = SamsungProfile(
        day = SamsungPhase(0, 420, 0), sunset = SamsungPhase(6, 1080, 0), night = SamsungPhase(8, 1380, 0),
        useSunTimes = true, latitude = 46.5, longitude = 6.6,
    )
    private fun level(profile: SamsungProfile, now: String, sunrise: String = "07:00", sunset: String = "18:00") =
        resolveSamsungLevel(profile, ZonedDateTime.parse(now)) { date ->
            SunTimesInfo(date.atTime(LocalTime.parse(sunrise)).atZone(ZoneOffset.UTC), date.atTime(LocalTime.parse(sunset)).atZone(ZoneOffset.UTC))
        }
    @Test fun negativeSunriseIsEarlierAndPositiveSunsetIsLater() {
        val profile = solar.copy(sunriseOffsetMinutes = -30, sunsetOffsetMinutes = 45)
        assertEquals(0, level(profile, "2026-10-27T06:45:00Z"))
        assertEquals(0, level(profile, "2026-10-27T18:30:00Z"))
        assertEquals(6, level(profile, "2026-10-27T18:45:00Z"))
    }
    @Test fun positiveSunriseAndNegativeSunsetShiftInTheOtherDirection() {
        val profile = solar.copy(sunriseOffsetMinutes = 20, sunsetOffsetMinutes = -20)
        assertEquals(8, level(profile, "2026-10-27T07:10:00Z"))
        assertEquals(6, level(profile, "2026-10-27T17:40:00Z"))
    }
    @Test fun fadeFinishesAtTheOffsetTarget() {
        val profile = solar.copy(sunset = solar.sunset.copy(fadeMinutes = 60), sunsetOffsetMinutes = 45)
        assertEquals(3, level(profile, "2026-10-27T18:15:00Z"))
    }
    @Test fun aLateSunsetOffsetRetainsItsNextDayAnchor() {
        val profile = solar.copy(sunset = solar.sunset.copy(fadeMinutes = 60), sunsetOffsetMinutes = 240)
        assertEquals(7, level(profile, "2026-10-28T01:00:00Z", sunset = "21:30"))
    }
    @Test fun missingSolarEventsAndManualModeUseUnshiftedManualTimes() {
        val profile = solar.copy(sunriseOffsetMinutes = -30, sunsetOffsetMinutes = 45)
        assertEquals(8, resolveSamsungLevel(profile, ZonedDateTime.parse("2026-10-27T06:45:00Z")) { SunTimesInfo(null, null) })
        assertEquals(6, level(profile.copy(useSunTimes = false), "2026-10-27T18:00:00Z"))
    }
    @Test fun offsetsFollowRealMinutesAcrossTheSpringGap() {
        val raw = SunTimesInfo(ZonedDateTime.parse("2026-03-29T03:30:00+02:00[Europe/Zurich]"), null)
        val shifted = samsungSolarTimes(solar.copy(sunriseOffsetMinutes = -60), raw)
        assertEquals(Instant.parse("2026-03-29T00:30:00Z"), shifted.sunrise!!.toInstant())
        assertNull(shifted.sunset)
    }
    @Test fun nextDaysEarlySunriseCannotOverrideANightTargetAtTheSameInstant() {
        val profile = solar.copy(sunriseOffsetMinutes = -240)
        assertEquals(8, level(profile, "2026-10-28T00:00:00Z", sunrise = "03:00"))
    }
    @Test fun invalidOffsetsAreRejectedBeforeScheduling() {
        assertNotNull(solar.copy(sunriseOffsetMinutes = -241).validationError())
        assertNotNull(solar.copy(sunsetOffsetMinutes = 241).validationError())
        assertNull(solar.copy(sunriseOffsetMinutes = -240, sunsetOffsetMinutes = 240).validationError())
    }
}
