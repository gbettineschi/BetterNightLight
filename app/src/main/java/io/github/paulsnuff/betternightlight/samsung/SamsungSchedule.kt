package io.github.paulsnuff.betternightlight.samsung

import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

/** A target is reached at [at]; its fade finishes, rather than starts, there. */
data class TimedTarget(val at: Instant, val level: Int, val fadeMinutes: Int) {
    init {
        require(level in 0..10) { "Samsung strength must be between 0 and 10" }
        require(fadeMinutes in 0..240) { "Fade must be between 0 and 240 minutes" }
    }
}

/** Uses real instants, so midnight and daylight-saving changes cannot reset progress. */
fun interpolateLevel(now: Instant, targets: List<TimedTarget>): Int {
    require(targets.size >= 2) { "At least two surrounding targets are required" }
    val ordered = targets.sortedBy { it.at }
    require(ordered.map { it.at }.distinct().size == ordered.size) { "Target times must differ" }
    val nextIndex = ordered.indexOfFirst { !it.at.isBefore(now) }
    require(nextIndex >= 0) { "Targets must extend beyond now" }
    val next = ordered[nextIndex]
    if (next.at == now) return next.level
    require(nextIndex > 0) { "Targets must begin before now" }
    val previous = ordered[nextIndex - 1]
    val gapMillis = Duration.between(previous.at, next.at).toMillis()
    val fadeMillis = minOf(Duration.ofMinutes(next.fadeMinutes.toLong()).toMillis(), gapMillis)
    if (fadeMillis == 0L) return previous.level
    val fadeStart = next.at.minusMillis(fadeMillis)
    if (!now.isAfter(fadeStart)) return previous.level
    val progress = Duration.between(fadeStart, now).toMillis().toDouble() / fadeMillis
    return (previous.level + (next.level - previous.level) * progress).roundToInt().coerceIn(0, 10)
}

fun shouldPauseForManualOff(lastAppliedLevel: Int?, isNativeActive: Boolean): Boolean =
    lastAppliedLevel != null && lastAppliedLevel > 0 && !isNativeActive
