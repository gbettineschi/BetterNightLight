package io.github.paulsnuff.betternightlight.samsung

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.paulsnuff.betternightlight.ui.components.status.StatusOverviewCard
import io.github.paulsnuff.betternightlight.ui.screens.about.AboutHeader

@Composable
fun SamsungAboutRoute(
    modifier: Modifier = Modifier,
    contentBottomPadding: Dp = 0.dp,
    viewModel: SamsungHomeViewModel = hiltViewModel(),
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleResumeEffect(Unit) { viewModel.refresh(); onPauseOrDispose { } }
    val status = samsungAboutPresentation(SamsungDevice.isSupported(context), ui.connection, ui.native)
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 20.dp).padding(bottom = contentBottomPadding),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        AboutHeader(Modifier.fillMaxWidth())
        StatusOverviewCard(
            title = "Samsung Eye comfort shield",
            statusText = if (status.supported) "Supported" else "Unavailable on this device",
            description = "This fork controls Samsung's native screen filter.",
            isPositive = status.supported,
        )
        StatusOverviewCard(
            title = "Shizuku connection",
            statusText = status.helperMessage,
            description = "Root is not required. Keep Shizuku running and restart it after a phone reboot.",
            isPositive = status.helperReady,
        )
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                val nativeState = when (status.nativeActive) { true -> "On"; false -> "Off"; null -> "Unknown" }
                val warmth = status.warmth?.let { "$it/10" } ?: when {
                    status.nativeActive == false -> "Not active"
                    status.nativeActive == true && ui.native?.customMode == false -> "Managed by Samsung"
                    else -> "Unknown"
                }
                val automation = when {
                    !ui.loaded -> "Loading…"
                    ui.profile.originalSettings != null && !ui.profile.enabled -> "Restore pending"
                    !ui.profile.enabled -> "Off"
                    ui.profile.manualOverride != null -> "Manual control"
                    ui.profile.paused -> "Paused"
                    else -> ui.automation.message
                }
                listOf("Native filter" to nativeState, "Warmth" to warmth, "Automation" to automation).forEach { (label, value) ->
                    Column {
                        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value, style = MaterialTheme.typography.titleMedium)
                    }
                }
                (ui.error ?: ui.automation.message.takeIf { ui.automation.error })?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
