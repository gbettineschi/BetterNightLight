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
    require(profile.validationError() == null) { "Invalid Samsung schedule" }
    val targets = (-1L..2L).flatMap { offset ->
        val date = now.toLocalDate().plusDays(offset)
        fun manual(phase: SamsungPhase) = date.atTime(phase.minute / 60, phase.minute % 60).atZone(now.zone)
        val sun = if (profile.useSunTimes) try { sunTimes(date) } catch (_: Exception) { null } else null
        listOf(
            TimedTarget((sun?.sunrise ?: manual(profile.day)).toInstant(), profile.day.level, profile.day.fadeMinutes),
            TimedTarget((sun?.sunset ?: manual(profile.sunset)).toInstant(), profile.sunset.level, profile.sunset.fadeMinutes),
            TimedTarget(manual(profile.night).toInstant(), profile.night.level, profile.night.fadeMinutes),
        )
    }
    return interpolateLevel(now.toInstant(), targets)
}
