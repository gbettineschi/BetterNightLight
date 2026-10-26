package io.github.paulsnuff.betternightlight.samsung

import org.junit.Assert.*
import org.junit.Test

class SamsungSettingsTest {
    @Test fun takeoverUsesSamsungKeysAndEnablesAfterSettingStrength() {
        val writes = SamsungSettings.writesForLevel(7)
        assertEquals(listOf("blue_light_filter_opacity" to 7, "blue_light_filter_adaptive_mode" to 0, "blue_light_filter_scheduled" to 1, "blue_light_filter_type" to 0, "blue_light_filter" to 1), writes)
    }
    @Test fun neutralDayDisablesTheNativeFilter() {
        assertEquals("blue_light_filter" to 0, SamsungSettings.writesForLevel(0).last())
    }
    @Test fun restorePreservesAnAdaptiveOriginalConfigurationExactly() {
        assertEquals(listOf("blue_light_filter_opacity" to 4, "blue_light_filter_adaptive_mode" to 1, "blue_light_filter_scheduled" to 0, "blue_light_filter_type" to 2, "blue_light_filter" to 1), SamsungSettings.restoreWrites(listOf(4, 1, 0, 2, 1)))
    }
    @Test fun rejectsInvalidCommandsBeforeAnyNativeWrite() {
        assertThrows(IllegalArgumentException::class.java) { SamsungSettings.writesForLevel(11) }
        assertThrows(IllegalArgumentException::class.java) { SamsungSettings.writesForLevel(-1) }
        assertThrows(IllegalArgumentException::class.java) { SamsungSettings.restoreWrites(listOf(1, 0)) }
        assertThrows(IllegalArgumentException::class.java) { SamsungSettings.restoreWrites(listOf(10, 0, 1, 0, 5)) }
    }
}
