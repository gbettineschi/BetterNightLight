package io.github.paulsnuff.betternightlight.samsung

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SamsungProfileRepository @Inject constructor(private val dataStore: DataStore<Preferences>) : SamsungProfileStore {
    override val profiles = dataStore.data.map { decode(it) }

    override suspend fun update(transform: (SamsungProfile) -> SamsungProfile): SamsungProfile {
        val written = dataStore.edit { preferences -> encode(preferences, transform(decode(preferences))) }
        return decode(written)
    }

    private fun decode(p: Preferences): SamsungProfile {
        val defaults = SamsungProfile()
        fun phase(name: String, fallback: SamsungPhase) = SamsungPhase(
            p[intPreferencesKey("samsung_${name}_level")] ?: fallback.level,
            p[intPreferencesKey("samsung_${name}_minute")] ?: fallback.minute,
            p[intPreferencesKey("samsung_${name}_fade")] ?: fallback.fadeMinutes,
        )
        return SamsungProfile(
            enabled = p[ENABLED] ?: false,
            paused = p[PAUSED] ?: false,
            day = phase("day", defaults.day),
            sunset = phase("sunset", defaults.sunset),
            night = phase("night", defaults.night),
            useSunTimes = p[SOLAR] ?: false,
            latitude = p[LATITUDE], longitude = p[LONGITUDE],
            // Never turn a malformed saved snapshot into null: that would permit overwriting it.
            originalSettings = p[ORIGINAL]?.split(',')?.map { it.toIntOrNull() ?: Int.MIN_VALUE },
            lastAppliedLevel = p[LAST_LEVEL],
        )
    }

    private fun encode(p: MutablePreferences, profile: SamsungProfile) {
        p[ENABLED] = profile.enabled; p[PAUSED] = profile.paused; p[SOLAR] = profile.useSunTimes
        listOf("day" to profile.day, "sunset" to profile.sunset, "night" to profile.night).forEach { (name, phase) ->
            p[intPreferencesKey("samsung_${name}_level")] = phase.level
            p[intPreferencesKey("samsung_${name}_minute")] = phase.minute
            p[intPreferencesKey("samsung_${name}_fade")] = phase.fadeMinutes
        }
        profile.latitude?.let { p[LATITUDE] = it } ?: p.remove(LATITUDE)
        profile.longitude?.let { p[LONGITUDE] = it } ?: p.remove(LONGITUDE)
        profile.originalSettings?.let { p[ORIGINAL] = it.joinToString(",") } ?: p.remove(ORIGINAL)
        profile.lastAppliedLevel?.let { p[LAST_LEVEL] = it } ?: p.remove(LAST_LEVEL)
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("samsung_enabled")
        val PAUSED = booleanPreferencesKey("samsung_paused")
        val SOLAR = booleanPreferencesKey("samsung_solar")
        val LATITUDE = doublePreferencesKey("samsung_latitude")
        val LONGITUDE = doublePreferencesKey("samsung_longitude")
        val ORIGINAL = stringPreferencesKey("samsung_original_settings")
        val LAST_LEVEL = intPreferencesKey("samsung_last_level")
    }
}
