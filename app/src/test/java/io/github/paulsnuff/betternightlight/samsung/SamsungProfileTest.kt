package io.github.paulsnuff.betternightlight.samsung
import org.junit.Assert.*
import org.junit.Test
class SamsungProfileTest {
    @Test fun defaultManualProfileCanRun() { assertNull(SamsungProfile().validationError()) }
    @Test fun duplicateManualTimesCannotStartAnAmbiguousSchedule() {
        assertNotNull(SamsungProfile(sunset = SamsungPhase(5, 420, 60)).validationError())
    }
    @Test fun invalidLevelsTimesOrFadesCannotReachTheController() {
        assertNotNull(SamsungProfile(day = SamsungPhase(11, 420, 60)).validationError())
        assertNotNull(SamsungProfile(day = SamsungPhase(0, 1440, 60)).validationError())
        assertNotNull(SamsungProfile(day = SamsungPhase(0, 420, 241)).validationError())
    }
    @Test fun solarModeRequiresFiniteValidCoordinates() {
        assertNotNull(SamsungProfile(useSunTimes = true).validationError())
        assertNotNull(SamsungProfile(useSunTimes = true, latitude = Double.NaN, longitude = 6.0).validationError())
        assertNotNull(SamsungProfile(useSunTimes = true, latitude = 91.0, longitude = 6.0).validationError())
        assertNull(SamsungProfile(useSunTimes = true, latitude = 46.5, longitude = 6.6).validationError())
    }
}
