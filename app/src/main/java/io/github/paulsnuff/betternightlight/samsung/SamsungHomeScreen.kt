package io.github.paulsnuff.betternightlight.samsung

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.paulsnuff.betternightlight.R
import io.github.paulsnuff.betternightlight.domain.SunTimesCalculator
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SamsungHomeRoute(
    modifier: Modifier = Modifier,
    contentBottomPadding: Dp = 0.dp,
    viewModel: SamsungHomeViewModel = hiltViewModel(),
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleResumeEffect(Unit) { viewModel.refresh(); onPauseOrDispose { } }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) viewModel.fetchLocation()
        else viewModel.showError("Allow location access, or enter coordinates.")
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var coordinatesDialog by remember { mutableStateOf(false) }
    val profile = ui.profile
    val ready = ui.connection == SamsungConnection.READY
    val validation = profile.validationError()
    val formatter = remember(DateFormat.is24HourFormat(context)) {
        DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a")
    }
    val sun = remember(profile.useSunTimes, profile.latitude, profile.longitude, LocalDate.now()) {
        if (profile.useSunTimes && profile.latitude != null && profile.longitude != null && validation == null)
            runCatching { SunTimesCalculator.compute(profile.latitude, profile.longitude) }.getOrNull()
        else null
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp).padding(top = 20.dp, bottom = contentBottomPadding + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Better Night Light", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Samsung Eye comfort shield", color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (!ready) SectionCard("Shizuku connection") {
            Text(when (ui.connection) {
                SamsungConnection.STOPPED -> "Start Shizuku to control Samsung's native filter."
                SamsungConnection.NEEDS_PERMISSION -> "Allow this app to use Shizuku. Root is not required."
                SamsungConnection.CONNECTING -> "Connecting to the Samsung helper…"
                SamsungConnection.FAILED -> "The helper could not connect. Reconnect Shizuku and try again."
                SamsungConnection.READY -> "Connected"
            })
            Button(onClick = {
                if (ui.connection == SamsungConnection.STOPPED) {
                    val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                        ?: Intent(Intent.ACTION_VIEW, "https://github.com/RikkaApps/Shizuku/releases".toUri())
                    runCatching { context.startActivity(intent) }.onFailure { viewModel.showError("Open Shizuku, start it, then return here.") }
                } else viewModel.connect()
            }) { Text(if (ui.connection == SamsungConnection.STOPPED) "Open Shizuku" else "Connect") }
        } else Text("Shizuku connected", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)

        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Automatic warmth", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = profile.enabled, onCheckedChange = { enabled ->
                    if (enabled && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    viewModel.setEnabled(enabled)
                }, enabled = ui.loaded && (profile.enabled || (ready && validation == null)))
            }
            Text(when {
                !ui.loaded -> "Loading saved settings…"
                !profile.enabled && profile.originalSettings != null -> "Restore pending. Reconnect Shizuku to finish."
                else -> ui.automation.message
            }, color = if (ui.automation.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(ui.native?.let { if (it.active) "On screen: warmth ${it.level}/10" else "On screen: filter off" } ?: "Reading screen state…", style = MaterialTheme.typography.bodySmall)
            if (profile.enabled) OutlinedButton(onClick = { viewModel.setPaused(!profile.paused) }, enabled = ready) {
                Text(if (profile.paused) "Resume" else "Pause")
            }
            Text("Turning automation off restores your previous Samsung settings.", style = MaterialTheme.typography.bodySmall)
        }
        (ui.error ?: validation)?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Text("Daily targets", style = MaterialTheme.typography.titleLarge)
        Text("Each fade finishes at its target time. Samsung Custom mode has 11 warmth levels.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PhaseCard("Day", SamsungPhaseKind.DAY, profile.day, formatter,
            solarLabel = if (profile.useSunTimes) "Sunrise · ${sun?.sunrise?.format(formatter) ?: "manual fallback"}" else null,
            enabled = ui.loaded, viewModel = viewModel)
        PhaseCard("Sunset", SamsungPhaseKind.SUNSET, profile.sunset, formatter,
            solarLabel = if (profile.useSunTimes) "Sunset · ${sun?.sunset?.format(formatter) ?: "manual fallback"}" else null,
            enabled = ui.loaded, viewModel = viewModel)
        PhaseCard("Night", SamsungPhaseKind.NIGHT, profile.night, formatter, enabled = ui.loaded, viewModel = viewModel)

        SectionCard("Timing") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Use sunrise and sunset", modifier = Modifier.weight(1f))
                Switch(profile.useSunTimes, viewModel::setSolar, enabled = ui.loaded)
            }
            if (profile.useSunTimes) {
                Text(if (profile.latitude != null && profile.longitude != null) "Saved location: %.4f, %.4f".format(Locale.ROOT, profile.latitude, profile.longitude) else "Choose a location for sun times.")
                Text("If sun times are unavailable, your manual times are used.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) viewModel.fetchLocation()
                    else locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }, enabled = !ui.locating) { Text(if (ui.locating) "Getting location…" else "Use phone location") }
                TextButton(onClick = { coordinatesDialog = true }) { Text("Enter coordinates") }
            }
        }
        OutlinedButton(onClick = {
            if (Build.VERSION.SDK_INT >= 33) runCatching {
                context.getSystemService(StatusBarManager::class.java).requestAddTileService(
                    ComponentName(context, SamsungTileService::class.java), "Night Light",
                    Icon.createWithResource(context, R.drawable.ic_samsung_tile), context.mainExecutor,
                ) { }
            }.onFailure { viewModel.showError("Open Quick Settings, choose Edit, and add Night Light.") }
            else viewModel.showError("Open Quick Settings, choose Edit, and add Night Light.")
        }, modifier = Modifier.fillMaxWidth()) { Text("Add Quick Settings tile") }
        Text("Keep Shizuku running; restart it after a phone reboot. While it is stopped, Samsung's Eye comfort shield tile can turn the filter off.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())) }) {
            Text("Background and battery settings")
        }
    }
    if (coordinatesDialog) CoordinatesDialog(profile, onDismiss = { coordinatesDialog = false }) { latitude, longitude ->
        viewModel.setCoordinates(latitude, longitude); coordinatesDialog = false
    }
}

@Composable
private fun SectionCard(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhaseCard(label: String, kind: SamsungPhaseKind, phase: SamsungPhase, formatter: DateTimeFormatter,
    solarLabel: String? = null, enabled: Boolean, viewModel: SamsungHomeViewModel) {
    var level by remember(phase.level) { mutableFloatStateOf(phase.level.coerceIn(0, 10).toFloat()) }
    val displayMinute = phase.minute.coerceIn(0, 1439)
    var timeDialog by remember { mutableStateOf(false) }
    var fadeDialog by remember { mutableStateOf(false) }
    SectionCard {
        Row { Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); Text("${level.roundToInt()}/10", fontWeight = FontWeight.SemiBold) }
        Slider(level, onValueChange = { level = it }, onValueChangeFinished = { viewModel.setLevel(kind, level.roundToInt()) }, valueRange = 0f..10f, steps = 9, enabled = enabled,
            modifier = Modifier.semantics { contentDescription = "$label warmth" })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            if (solarLabel != null) Text(solarLabel, style = MaterialTheme.typography.bodyMedium)
            else TextButton(onClick = { timeDialog = true }, enabled = enabled) { Text("Reach by ${LocalTime.of(displayMinute / 60, displayMinute % 60).format(formatter)}") }
            TextButton(onClick = { fadeDialog = true }, enabled = enabled) { Text(if (phase.fadeMinutes == 0) "Instant" else "Fade: ${phase.fadeMinutes} min") }
        }
    }
    if (timeDialog) {
        val time = rememberTimePickerState(displayMinute / 60, displayMinute % 60, DateFormat.is24HourFormat(LocalContext.current))
        AlertDialog(onDismissRequest = { timeDialog = false }, title = { Text("$label target time") }, text = { TimePicker(time) },
            confirmButton = { TextButton(onClick = { viewModel.setTime(kind, time.hour * 60 + time.minute); timeDialog = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { timeDialog = false }) { Text("Cancel") } })
    }
    if (fadeDialog) {
        var value by remember { mutableStateOf(phase.fadeMinutes.toString()) }
        val minutes = value.toIntOrNull()
        AlertDialog(onDismissRequest = { fadeDialog = false }, title = { Text("$label transition") },
            text = { OutlinedTextField(value, { value = it.filter(Char::isDigit).take(3) }, label = { Text("Minutes (0–240)") }, supportingText = { Text("0 changes instantly; longer times fade more slowly.") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) },
            confirmButton = { TextButton(onClick = { viewModel.setFade(kind, minutes!!); fadeDialog = false }, enabled = minutes != null && minutes in 0..240) { Text("Save") } },
            dismissButton = { TextButton(onClick = { fadeDialog = false }) { Text("Cancel") } })
    }
}

@Composable
private fun CoordinatesDialog(profile: SamsungProfile, onDismiss: () -> Unit, onSave: (Double, Double) -> Unit) {
    var latitude by remember { mutableStateOf(profile.latitude?.toString() ?: "") }
    var longitude by remember { mutableStateOf(profile.longitude?.toString() ?: "") }
    val lat = latitude.replace(',', '.').toDoubleOrNull()
    val lon = longitude.replace(',', '.').toDoubleOrNull()
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Sunrise and sunset location") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(latitude, { latitude = it }, label = { Text("Latitude") }, singleLine = true)
            OutlinedTextField(longitude, { longitude = it }, label = { Text("Longitude") }, singleLine = true)
        }
    }, confirmButton = { TextButton(onClick = { onSave(lat!!, lon!!) }, enabled = lat != null && lon != null && lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
