package io.github.paulsnuff.betternightlight.samsung

import java.time.Instant

enum class SamsungManualResumePolicy { NEXT_CHANGE, ONE_HOUR, MANUAL }

data class SamsungManualOverride(
    val policy: SamsungManualResumePolicy,
    val changedAt: Instant,
    val resumeAt: Instant?,
    val native: SamsungNativeState,
)

fun hasNativeManualChange(lastAppliedLevel: Int?, native: SamsungNativeState): Boolean = lastAppliedLevel != null &&
    (native.active != (lastAppliedLevel > 0) || native.level != lastAppliedLevel || !native.customMode)
