package io.github.paulsnuff.betternightlight.samsung

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
                        ) else it
                    }
                    return@repeat
                }
                (if (profile.paused) null else profile.validationError())?.let {
                    _status.value = SamsungAutomationStatus(it, error = true)
                    return@withLock
                }
                if (original == null) {
                    val captured = control.snapshot()
                    SamsungSettings.restoreWrites(captured)
                    store.update { if (it.originalSettings == null) it.copy(originalSettings = captured.toList()) else it }
                    return@repeat
                }
                val native = control.readState()
                if (store.profiles.first() != profile) return@repeat
                if (profile.paused) {
                    if (native.active) control.pause()
                    store.update { if (it.paused) it.copy(lastAppliedLevel = null) else it }
                    _status.value = SamsungAutomationStatus("Paused until you resume.")
                    return@withLock
                }
                if (shouldPauseForManualOff(profile.lastAppliedLevel, native.active)) {
                    store.update { if (it == profile) it.copy(paused = true, lastAppliedLevel = null) else it }
                    return@repeat
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
}
