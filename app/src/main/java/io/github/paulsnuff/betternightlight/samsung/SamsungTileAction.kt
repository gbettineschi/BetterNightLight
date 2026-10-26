package io.github.paulsnuff.betternightlight.samsung

/** The persisted result of a tile click, or null when setup must be corrected first. */
fun samsungTileAction(profile: SamsungProfile): SamsungProfile? {
    if ((!profile.enabled || profile.paused) && profile.validationError() != null) return null
    return if (!profile.enabled) profile.copy(enabled = true, paused = false, lastAppliedLevel = null)
    else profile.copy(paused = !profile.paused, lastAppliedLevel = null)
}
