package io.github.paulsnuff.betternightlight.samsung

/** Fixed allowlist and ordering: strength first, activation last, then service refresh. */
object SamsungSettings {
    val keys = listOf(
        "blue_light_filter_opacity",
        "blue_light_filter_adaptive_mode",
        "blue_light_filter_scheduled",
        "blue_light_filter_type",
        "blue_light_filter",
    )

    fun writesForLevel(level: Int): List<Pair<String, Int>> {
        require(level in 0..10) { "Samsung strength must be between 0 and 10" }
        return keys.zip(listOf(level, 0, 1, 0, if (level == 0) 0 else 1))
    }

    fun restoreWrites(values: List<Int>): List<Pair<String, Int>> {
        require(values.size == keys.size) { "Incomplete original Samsung settings" }
        require(values[0] in 0..10 && values[1] in 0..1 && values[2] in 0..1 && values[3] in 0..2 && values[4] in 0..1) {
            "Invalid original Samsung settings"
        }
        return keys.zip(values)
    }
}
