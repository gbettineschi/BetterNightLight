package io.github.paulsnuff.betternightlight.samsung

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SamsungAutomationStatus(val message: String = "Automation is off.", val error: Boolean = false)

class SamsungAutomation(
    private val store: SamsungProfileStore,
    private val control: SamsungControl,
    private val clock: Clock = Clock.systemUTC(),
    private val nextTransitionAt: (SamsungProfile, Instant) -> Instant? = { profile, at -> nextSamsungTransition(profile, at.atZone(ZoneId.systemDefault())) },
    private val targetLevel: (SamsungProfile) -> Int,
) {
    private val _status = MutableStateFlow(SamsungAutomationStatus())
    val status = _status.asStateFlow()
    private val mutex = Mutex()

    suspend fun reconcile() = mutex.withLock {
        try {
            // Re-read after every slow operation. UI/tile edits must never be overwritten by a stale tick.
            repeat(8) {
                val profile = store.profiles.first()
                val original = profile.originalSettings
                if (original != null) SamsungSettings.restoreWrites(original)
                if (!profile.enabled) {
                    if (original == null) {
                        _status.value = SamsungAutomationStatus()
                        return@withLock
                    }
                    control.restore(original)
                    store.update {
                        if (it.originalSettings == original) it.copy(
                            originalSettings = null,
                            lastAppliedLevel = null,
                            paused = it.enabled && it.paused,
                            manualOverride = null,
                        ) else it
                    }
                    return@repeat
                }
                if (original == null) {
                    if (!profile.isPaused) profile.validationError()?.let {
                        _status.value = SamsungAutomationStatus(it, error = true)
                        return@withLock
                    }
                    val captured = control.snapshot()
                    SamsungSettings.restoreWrites(captured)
                    store.update { if (it.originalSettings == null) it.copy(originalSettings = captured.toList()) else it }
                    return@repeat
                }
                val native = control.readState()
                if (store.profiles.first() != profile) return@repeat
                if (profile.paused) {
                    if (native.active) control.pause()
                    store.update { if (it.paused) it.copy(lastAppliedLevel = null, manualOverride = null) else it }
                    _status.value = SamsungAutomationStatus("Paused until you resume.")
                    return@withLock
                }
                val now = Instant.ofEpochMilli(clock.millis())
                val held = profile.manualOverride
                if (held != null) {
                    // A new native edit, including one exactly at expiry, retains manual control.
                    val since = if (held.native != native || held.policy != profile.manualResumePolicy) now else held.changedAt
                    val updated = manualOverride(profile, native, since)
                    if (updated != held) {
                        store.update { if (it == profile) it.copy(manualOverride = updated) else it }
                        return@repeat
                    }
                    if (held.resumeAt != null && !now.isBefore(held.resumeAt)) {
                        store.update { if (it == profile) it.copy(manualOverride = null, lastAppliedLevel = null) else it }
                        return@repeat
                    }
                    _status.value = SamsungAutomationStatus(when (held.policy) {
                        SamsungManualResumePolicy.MANUAL -> "Manual control until you resume."
                        SamsungManualResumePolicy.ONE_HOUR -> "Manual control · resumes after one hour."
                        SamsungManualResumePolicy.NEXT_CHANGE -> if (held.resumeAt != null) "Manual control until the next scheduled transition." else "Manual control · set a valid schedule or tap Resume."
                    })
                    return@withLock
                }
                if (hasNativeManualChange(profile.lastAppliedLevel, native)) {
                    val override = manualOverride(profile, native, now)
                    store.update { if (it == profile) it.copy(manualOverride = override, lastAppliedLevel = null) else it }
                    return@repeat
                }
                profile.validationError()?.let {
                    _status.value = SamsungAutomationStatus(it, error = true)
                    return@withLock
                }
                val level = targetLevel(profile)
                require(level in 0..10)
                if (native.active == (level > 0) && native.level == level && native.customMode && profile.lastAppliedLevel == level) {
                    _status.value = SamsungAutomationStatus("Automatic · warmth $level/10")
                    return@withLock
                }
                if (store.profiles.first() != profile) return@repeat
                // Persist our write intent before touching native state. A failed automatic-off
                // must not be mistaken for a user switching the filter off after a restart.
                val intent = profile.copy(lastAppliedLevel = null)
                if (store.update { if (it == profile) intent else it } != intent) return@repeat
                control.applyLevel(level)
                store.update { it.copy(lastAppliedLevel = level) }
            }
            _status.value = SamsungAutomationStatus("Applying your latest changes…")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            _status.value = SamsungAutomationStatus(
                "Update or restore is waiting. Reconnect Shizuku; your saved original settings are retained.",
                error = true,
            )
        }
    }

    private fun manualOverride(profile: SamsungProfile, native: SamsungNativeState, since: Instant): SamsungManualOverride {
        val deadline = when (profile.manualResumePolicy) {
            SamsungManualResumePolicy.MANUAL -> null
            SamsungManualResumePolicy.ONE_HOUR -> since.plusSeconds(3600)
            SamsungManualResumePolicy.NEXT_CHANGE -> if (profile.validationError() == null) nextTransitionAt(profile, since) else null
        }?.let { Instant.ofEpochMilli(it.toEpochMilli()) }
        return SamsungManualOverride(profile.manualResumePolicy, since, deadline, native)
    }

}
