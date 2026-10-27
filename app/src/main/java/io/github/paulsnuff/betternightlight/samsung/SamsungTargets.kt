package io.github.paulsnuff.betternightlight.samsung
import io.github.paulsnuff.betternightlight.domain.SunTimesInfo
import io.github.paulsnuff.betternightlight.domain.SunTimesCalculator
import java.time.LocalDate
import java.time.ZonedDateTime

fun resolveSamsungLevel(
    profile: SamsungProfile,
    now: ZonedDateTime,
    sunTimes: (LocalDate) -> SunTimesInfo = { SunTimesCalculator.compute(profile.latitude!!, profile.longitude!!, it, now.zone) },
): Int {
    return interpolateLevel(now.toInstant(), resolveSamsungTargets(profile, now, sunTimes))
}

private fun resolveSamsungTargets(
    profile: SamsungProfile,
    now: ZonedDateTime,
    sunTimes: (LocalDate) -> SunTimesInfo,
): List<TimedTarget> {
    require(profile.validationError() == null) { "Invalid Samsung schedule" }
    val targets = (-1L..2L).flatMap { offset ->
        val date = now.toLocalDate().plusDays(offset)
        fun manual(phase: SamsungPhase) = date.atTime(phase.minute / 60, phase.minute % 60).atZone(now.zone)
        val sun = if (profile.useSunTimes) try { samsungSolarTimes(profile, sunTimes(date)) } catch (_: Exception) { null } else null
        listOf(
            TimedTarget((sun?.sunrise ?: manual(profile.day)).toInstant(), profile.day.level, profile.day.fadeMinutes),
            TimedTarget((sun?.sunset ?: manual(profile.sunset)).toInstant(), profile.sunset.level, profile.sunset.fadeMinutes),
            TimedTarget(manual(profile.night).toInstant(), profile.night.level, profile.night.fadeMinutes),
        ).mapIndexed { phasePriority, target -> phasePriority to target }
    }
    // A spring-forward gap (or a solar/manual coincidence) can map two targets to
    // one instant. The later phase wins: Night, then Sunset, then Day.
    val distinct = targets.sortedBy { it.first }.associate { (_, target) -> target.at to target }.values.toList()
    return distinct
}

fun samsungSolarTimes(profile: SamsungProfile, sun: SunTimesInfo): SunTimesInfo = SunTimesInfo(
    sunrise = sun.sunrise?.plusMinutes(profile.sunriseOffsetMinutes.toLong()),
    sunset = sun.sunset?.plusMinutes(profile.sunsetOffsetMinutes.toLong()),
)

fun nextSamsungTransition(
    profile: SamsungProfile,
    now: ZonedDateTime,
    sunTimes: (LocalDate) -> SunTimesInfo = { SunTimesCalculator.compute(profile.latitude!!, profile.longitude!!, it, now.zone) },
): java.time.Instant? = nextScheduledTransition(now.toInstant(), resolveSamsungTargets(profile, now, sunTimes))
