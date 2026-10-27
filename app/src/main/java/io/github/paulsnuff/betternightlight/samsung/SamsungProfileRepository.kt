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
import java.time.Instant
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
        val portable = decode(dataStore.data.first(), includeOwnership = false).copy(
            enabled = false, paused = false, originalSettings = null, lastAppliedLevel = null, manualOverride = null,
        )
        runtime.edit {
            if (it[INITIALIZED] != true) { encode(it, portable); it[INITIALIZED] = true }
        }
        // Legacy pre-release backups may contain old snapshots. Never restore those to a new phone.
        dataStore.edit { removeOwnership(it) }
    }

    private fun removeOwnership(p: MutablePreferences) {
        p.remove(ENABLED); p.remove(PAUSED); p.remove(ORIGINAL); p.remove(LAST_LEVEL); p.remove(OVERRIDE)
    }

    private fun decode(p: Preferences, includeOwnership: Boolean = true): SamsungProfile {
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
            manualResumePolicy = p[RESUME_POLICY]?.let { name -> SamsungManualResumePolicy.entries.find { it.name == name } ?: SamsungManualResumePolicy.MANUAL }
                ?: SamsungManualResumePolicy.NEXT_CHANGE,
            manualOverride = if (includeOwnership) p[OVERRIDE]?.let(::decodeOverride) else null,
            // Never turn a malformed saved snapshot into null: that would permit overwriting it.
            originalSettings = p[ORIGINAL]?.split(',')?.map { it.toIntOrNull() ?: Int.MIN_VALUE },
            lastAppliedLevel = p[LAST_LEVEL],
        )
    }

    private fun encode(p: MutablePreferences, profile: SamsungProfile) {
        p[RESUME_POLICY] = profile.manualResumePolicy.name
        profile.manualOverride?.let { override ->
            p[OVERRIDE] = listOf(override.policy.name, override.changedAt.toEpochMilli(), override.resumeAt?.toEpochMilli() ?: "-",
                if (override.native.active) 1 else 0, override.native.level, if (override.native.customMode) 1 else 0).joinToString(",")
        } ?: p.remove(OVERRIDE)
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

    private fun decodeOverride(text: String): SamsungManualOverride {
        val fields = text.split(',')
        require(fields.size == 6) { "Invalid saved manual override" }
        fun flag(index: Int): Boolean {
            require(fields[index] == "0" || fields[index] == "1")
            return fields[index] == "1"
        }
        val level = fields[4].toInt()
        require(level in 0..10)
        return SamsungManualOverride(SamsungManualResumePolicy.valueOf(fields[0]), Instant.ofEpochMilli(fields[1].toLong()),
            fields[2].takeUnless { it == "-" }?.let { Instant.ofEpochMilli(it.toLong()) }, SamsungNativeState(flag(3), level, flag(5)))
    }

    private companion object {
        val RESUME_POLICY = stringPreferencesKey("samsung_manual_resume_policy")
        val OVERRIDE = stringPreferencesKey("samsung_manual_override")
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
