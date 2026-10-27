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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SamsungProfileRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @SamsungRuntime private val runtime: DataStore<Preferences>,
) : SamsungProfileStore {
    private val mutex = Mutex()
    override val profiles = flow {
        mutex.withLock { initialize() }
        emitAll(runtime.data.map { decode(it) })
    }

    override suspend fun update(transform: (SamsungProfile) -> SamsungProfile): SamsungProfile = mutex.withLock {
        initialize()
        val written = runtime.edit { preferences -> encode(preferences, transform(decode(preferences))) }
        val profile = decode(written)
        // Only portable targets are backed up. Native ownership belongs to this device/session.
        dataStore.edit { encode(it, profile); removeOwnership(it) }
        profile
    }

    private suspend fun initialize() {
        if (runtime.data.first()[INITIALIZED] == true) return
        val portable = decode(dataStore.data.first()).copy(
            enabled = false, paused = false, originalSettings = null, lastAppliedLevel = null,
        )
        runtime.edit {
            if (it[INITIALIZED] != true) { encode(it, portable); it[INITIALIZED] = true }
        }
        // Legacy pre-release backups may contain old snapshots. Never restore those to a new phone.
        dataStore.edit { removeOwnership(it) }
    }

    private fun removeOwnership(p: MutablePreferences) {
        p.remove(ENABLED); p.remove(PAUSED); p.remove(ORIGINAL); p.remove(LAST_LEVEL)
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
            sunriseOffsetMinutes = p[SUNRISE_OFFSET] ?: 0,
            sunsetOffsetMinutes = p[SUNSET_OFFSET] ?: 0,
            // Never turn a malformed saved snapshot into null: that would permit overwriting it.
            originalSettings = p[ORIGINAL]?.split(',')?.map { it.toIntOrNull() ?: Int.MIN_VALUE },
            lastAppliedLevel = p[LAST_LEVEL],
        )
    }

    private fun encode(p: MutablePreferences, profile: SamsungProfile) {
        p[ENABLED] = profile.enabled; p[PAUSED] = profile.paused; p[SOLAR] = profile.useSunTimes
        p[SUNRISE_OFFSET] = profile.sunriseOffsetMinutes; p[SUNSET_OFFSET] = profile.sunsetOffsetMinutes
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
        val INITIALIZED = booleanPreferencesKey("samsung_local_initialized")
        val ENABLED = booleanPreferencesKey("samsung_enabled")
        val PAUSED = booleanPreferencesKey("samsung_paused")
        val SOLAR = booleanPreferencesKey("samsung_solar")
        val SUNRISE_OFFSET = intPreferencesKey("samsung_sunrise_offset")
        val SUNSET_OFFSET = intPreferencesKey("samsung_sunset_offset")
        val LATITUDE = doublePreferencesKey("samsung_latitude")
        val LONGITUDE = doublePreferencesKey("samsung_longitude")
        val ORIGINAL = stringPreferencesKey("samsung_original_settings")
        val LAST_LEVEL = intPreferencesKey("samsung_last_level")
    }
}
