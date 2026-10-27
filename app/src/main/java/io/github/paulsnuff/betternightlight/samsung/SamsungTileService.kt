package io.github.paulsnuff.betternightlight.samsung

import android.app.PendingIntent
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import io.github.paulsnuff.betternightlight.MainActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class SamsungTileService : TileService() {
    @Inject lateinit var profiles: SamsungProfileRepository
    @Inject lateinit var bridge: SamsungBridge
    @Inject lateinit var automation: SamsungAutomation
    @Inject lateinit var scope: CoroutineScope
    private var listening: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        listening?.cancel()
        listening = scope.launch {
            bridge.refresh()
            if (bridge.state.value == SamsungConnection.CONNECTING) runCatching { bridge.requireWorker() }
            combine(profiles.profiles, bridge.state, automation.status) { profile, connection, status -> Triple(profile, connection, status) }
                .collect { (profile, connection, status) -> withContext(Dispatchers.Main) { render(profile, connection, status) } }
        }
    }

    override fun onStopListening() { listening?.cancel(); super.onStopListening() }
    override fun onDestroy() { listening?.cancel(); super.onDestroy() }

    override fun onClick() {
        super.onClick()
        // The application scope survives panel collapse; the foreground service keeps the change alive.
        scope.launch {
            try {
                bridge.refresh()
                if (bridge.state.value == SamsungConnection.CONNECTING) runCatching { bridge.requireWorker() }
                val profile = profiles.profiles.first()
                if (!SamsungDevice.isSupported(this@SamsungTileService) || bridge.state.value != SamsungConnection.READY || samsungTileAction(profile) == null) {
                    withContext(Dispatchers.Main) { openApp() }
                    return@launch
                }
                profiles.update {
                    samsungTileAction(it) ?: it
                }
                SamsungAutomationService.start(this@SamsungTileService)
                automation.reconcile()
                val updated = profiles.profiles.first()
                withContext(Dispatchers.Main) { render(updated, bridge.state.value, automation.status.value) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w("BnlSamsungTile", "Tile change failed", error)
                withContext(Dispatchers.Main) { openApp() }
            }
        }
    }

    private fun render(profile: SamsungProfile, connection: SamsungConnection, status: SamsungAutomationStatus) {
        val tile = qsTile ?: return
        val presentation = samsungTilePresentation(profile, connection, status, SamsungDevice.isSupported(this))
        tile.label = "Night Light"
        tile.state = when {
            !presentation.available -> Tile.STATE_UNAVAILABLE
            presentation.active -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.subtitle = presentation.subtitle
        tile.contentDescription = "Better Night Light Samsung: ${tile.subtitle}"
        tile.updateTile()
    }

    @Suppress("DEPRECATION")
    @SuppressLint("StartActivityAndCollapseDeprecated") // Intent overload is required only on API 29–33; API 34+ uses PendingIntent below.
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 40, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        else startActivityAndCollapse(intent)
    }
}
