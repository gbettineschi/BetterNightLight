package io.github.paulsnuff.betternightlight.samsung

data class SamsungPhase(val level: Int, val minute: Int, val fadeMinutes: Int)

data class SamsungProfile(
    val enabled: Boolean = false,
    val paused: Boolean = false,
    val day: SamsungPhase = SamsungPhase(0, 420, 60),
    val sunset: SamsungPhase = SamsungPhase(5, 1080, 60),
    val night: SamsungPhase = SamsungPhase(10, 1380, 90),
    val useSunTimes: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val originalSettings: List<Int>? = null,
    val lastAppliedLevel: Int? = null,
) {
    fun validationError(): String? {
        val phases = listOf(day, sunset, night)
        if (phases.any { it.level !in 0..10 || it.minute !in 0..1439 || it.fadeMinutes !in 0..240 }) {
            return "Choose warmth 0–10, valid times, and fades between 0 and 240 minutes."
        }
        if (!useSunTimes && phases.map { it.minute }.distinct().size != 3) {
            return "Choose three different target times."
        }
        if (useSunTimes && (latitude == null || longitude == null || !latitude.isFinite() || !longitude.isFinite() || latitude !in -90.0..90.0 || longitude !in -180.0..180.0)) {
            return "Set a valid location for sunrise and sunset."
        }
        return null
    }
}
