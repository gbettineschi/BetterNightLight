package io.github.paulsnuff.betternightlight.samsung

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.paulsnuff.betternightlight.domain.DeviceLocationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SamsungPhaseKind { DAY, SUNSET, NIGHT }
private data class ProfileLoad(val profile: SamsungProfile? = null, val error: String? = null)
private data class NativeLoad(val state: SamsungNativeState? = null)
private data class UiExtra(val locating: Boolean = false, val error: String? = null)

data class SamsungHomeState(
    val loaded: Boolean = false,
    val profile: SamsungProfile = SamsungProfile(),
    val connection: SamsungConnection = SamsungConnection.STOPPED,
    val native: SamsungNativeState? = null,
    val automation: SamsungAutomationStatus = SamsungAutomationStatus(),
    val locating: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class SamsungHomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profiles: SamsungProfileRepository,
    private val bridge: SamsungBridge,
    private val device: SamsungDevice,
    private val automation: SamsungAutomation,
    private val location: DeviceLocationProvider,
) : ViewModel() {
    private val extra = MutableStateFlow(UiExtra())
    val state = combine(
        profiles.profiles.map { ProfileLoad(it) }.onStart { emit(ProfileLoad()) }
            .catch { emit(ProfileLoad(error = "Could not read saved settings. Reopen the app to retry.")) },
        bridge.state,
        device.observe().map { NativeLoad(it) }.onStart { emit(NativeLoad()) }.catch { emit(NativeLoad()) },
        automation.status,
        extra,
    ) { load, connection, native, status, ui ->
        SamsungHomeState(load.profile != null, load.profile ?: SamsungProfile(), connection, native.state, status, ui.locating, load.error ?: ui.error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SamsungHomeState())

    init {
        viewModelScope.launch {
            runCatching {
                val profile = profiles.profiles.first()
                if (profile.enabled || profile.originalSettings != null) SamsungAutomationService.start(context)
            }.onFailure { showError("Could not restart automatic warmth. Check the app's background settings.") }
        }
    }

    fun refresh() {
        bridge.refresh()
        if (bridge.state.value == SamsungConnection.CONNECTING) bridge.requestAuthorization()
    }

    fun connect() { bridge.requestAuthorization() }
    fun showError(message: String) { extra.update { it.copy(error = message) } }

    private fun edit(transform: (SamsungProfile) -> SamsungProfile) {
        extra.update { it.copy(error = null) }
        viewModelScope.launch {
            try {
                val profile = profiles.update(transform)
                if (profile.enabled || profile.originalSettings != null) SamsungAutomationService.start(context)
                automation.reconcile()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                showError("Could not save or apply the change. Reconnect Shizuku and retry.")
            }
        }
    }

    fun setEnabled(enabled: Boolean) = edit { it.copy(enabled = enabled, paused = false, lastAppliedLevel = null) }
    fun setPaused(paused: Boolean) = edit { it.copy(paused = paused, lastAppliedLevel = null) }
    fun setSolar(solar: Boolean) = edit { it.copy(useSunTimes = solar) }
    fun setCoordinates(latitude: Double, longitude: Double) = edit { it.copy(latitude = latitude, longitude = longitude) }

    private fun phase(kind: SamsungPhaseKind, transform: (SamsungPhase) -> SamsungPhase) = edit {
        when (kind) {
            SamsungPhaseKind.DAY -> it.copy(day = transform(it.day))
            SamsungPhaseKind.SUNSET -> it.copy(sunset = transform(it.sunset))
            SamsungPhaseKind.NIGHT -> it.copy(night = transform(it.night))
        }
    }
    fun setLevel(kind: SamsungPhaseKind, level: Int) = phase(kind) { it.copy(level = level.coerceIn(0, 10)) }
    fun setFade(kind: SamsungPhaseKind, minutes: Int) = phase(kind) { it.copy(fadeMinutes = minutes.coerceIn(0, 240)) }
    fun setTime(kind: SamsungPhaseKind, minute: Int) = phase(kind) { it.copy(minute = minute.coerceIn(0, 1439)) }

    fun fetchLocation() {
        if (extra.value.locating) return
        extra.update { it.copy(locating = true, error = null) }
        viewModelScope.launch {
            try {
                val point = location.getCurrentLocation()
                if (point != null) setCoordinates(point.latitude, point.longitude)
                else showError("Location is unavailable. Try again or enter coordinates.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                showError("Could not get your location. Enter coordinates instead.")
            } finally { extra.update { it.copy(locating = false) } }
        }
    }
}
