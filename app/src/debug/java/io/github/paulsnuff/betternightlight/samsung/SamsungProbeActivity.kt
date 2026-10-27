package io.github.paulsnuff.betternightlight.samsung

import androidx.activity.ComponentActivity
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import rikka.shizuku.Shizuku
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** Debug-only physical-device check. No launcher entry and excluded from release APKs. */
@AndroidEntryPoint
class SamsungProbeActivity : ComponentActivity() {
    @Inject lateinit var bridge: SamsungBridge
    @Inject lateinit var profiles: SamsungProfileRepository
    @Inject lateinit var automation: SamsungAutomation
    @Inject lateinit var control: SamsungControl
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = TextView(this).apply { setPadding(24, 24, 24, 24); setText("Samsung backend check: allow Shizuku when prompted.") }
        setContentView(text)
        scope.launch {
            try {
                withTimeout(15_000) { bridge.state.first { it != SamsungConnection.STOPPED } }
                bridge.requestAuthorization()
                withTimeout(90_000) { bridge.state.first { it == SamsungConnection.READY } }
                val result = withContext(Dispatchers.IO) {
                    val worker = bridge.requireWorker()
                    val before = worker.snapshot()
                    worker.restore(before)
                    check(worker.snapshot().contentEquals(before)) { "Restoration mismatch" }
                    if (intent.getStringExtra("scenario") == "lifecycle") verifyLifecycle()
                    if (intent.getStringExtra("scenario") == "fade") verifyFade()
                    if (intent.getStringExtra("scenario") == "manual") verifyManualControl()
                    "PASS: root-free helper uid=${Shizuku.getUid()}, same-value restore verified, values=${before.toList()}" +
                        when (intent.getStringExtra("scenario")) {
                            "lifecycle" -> "; lifecycle: foreground/background, pause/resume, manual-off, helper reconnect, exact restore passed"
                            "fade" -> "; timed background fade and exact restore passed"
                            "manual" -> "; native manual hold/off/on, aged one-hour timestamp, real next-transition expiry and profile/native restoration passed"
                            else -> ""
                        }
                }
                text.text = result
                Log.i("BnlSamsungProbe", result)
                filesDir.resolve("samsung-probe-result.txt").writeText(result)
            } catch (error: Exception) {
                val result = "FAIL: ${error.javaClass.simpleName}: ${error.message}"
                text.text = result
                Log.e("BnlSamsungProbe", result, error)
                filesDir.resolve("samsung-probe-result.txt").writeText(result)
            }
        }
    }

    private suspend fun verifyManualControl() {
        val saved = profiles.profiles.first()
        val original = bridge.requireWorker().snapshot()
        suspend fun await(condition: suspend () -> Boolean) = withTimeout(20_000) {
            while (!condition()) delay(150)
        }
        fun log(message: String) {
            filesDir.resolve("samsung-manual-trace.txt").appendText("${Instant.now()} $message\n")
        }
        filesDir.resolve("samsung-manual-trace.txt").writeText("")
        try {
            profiles.update { SamsungProfile(enabled = true, originalSettings = original.toList(),
                day = SamsungPhase(3,420,0), sunset = SamsungPhase(3,1080,0), night = SamsungPhase(3,1380,0),
                manualResumePolicy = SamsungManualResumePolicy.MANUAL) }
            SamsungAutomationService.start(this)
            await { val state = control.readState(); state.active && state.level == 3 && profiles.profiles.first().lastAppliedLevel == 3 }
            withContext(Dispatchers.Main) { moveTaskToBack(true) }
            control.applyLevel(7) // Same native state change as the Samsung warmth slider.
            await { profiles.profiles.first().manualOverride?.native?.level == 7 }
            delay(6_000)
            check(control.readState() == SamsungNativeState(true,7,true))
            control.pause()
            await { profiles.profiles.first().manualOverride?.native?.active == false }
            control.applyLevel(5)
            await { profiles.profiles.first().manualOverride?.native == SamsungNativeState(true,5,true) }
            delay(6_000)
            check(control.readState() == SamsungNativeState(true,5,true))
            log("PASS manual slider/off/on preserved without native writes by automation")
            profiles.update { it.copy(manualResumePolicy = SamsungManualResumePolicy.ONE_HOUR) }
            await { profiles.profiles.first().manualOverride?.policy == SamsungManualResumePolicy.ONE_HOUR }
            val hour = profiles.profiles.first().manualOverride!!
            check(java.time.Duration.between(hour.changedAt, hour.resumeAt).seconds == 3600L)
            val aged = Instant.now().minusSeconds(3596)
            profiles.update { it.copy(manualOverride = it.manualOverride!!.copy(changedAt = aged, resumeAt = aged.plusSeconds(3600))) }
            await { profiles.profiles.first().manualOverride == null && control.readState() == SamsungNativeState(true,3,true) }
            log("PASS ONE_HOUR policy expired after aging this test record by 3596 seconds; native level3 resumed")
            val target = ZonedDateTime.now().truncatedTo(ChronoUnit.MINUTES).plusMinutes(2)
            fun minute(time: ZonedDateTime) = time.hour * 60 + time.minute
            profiles.update { it.copy(manualResumePolicy = SamsungManualResumePolicy.NEXT_CHANGE, manualOverride = null, lastAppliedLevel = null,
                day = SamsungPhase(3,minute(target.minusHours(6)),0), sunset = SamsungPhase(3,minute(target.minusHours(3)),0),
                night = SamsungPhase(8,minute(target),0)) }
            await { profiles.profiles.first().lastAppliedLevel == 3 }
            control.applyLevel(6)
            await { profiles.profiles.first().manualOverride != null }
            check(profiles.profiles.first().manualOverride!!.resumeAt == target.toInstant())
            log("Holding native6 until actual next scheduled transition $target")
            withTimeout(150_000) {
                while (Instant.now().isBefore(target.toInstant())) {
                    check(control.readState() == SamsungNativeState(true,6,true))
                    delay(300)
                }
                await { profiles.profiles.first().manualOverride == null && control.readState() == SamsungNativeState(true,8,true) }
            }
            log("PASS actual next-transition expiry resumed native8")
        } finally {
            withContext(NonCancellable) {
                profiles.update { it.copy(enabled = false) }
                automation.reconcile()
                check(bridge.requireWorker().snapshot().contentEquals(original)) { "Test native settings did not restore" }
                check(profiles.profiles.first().originalSettings == null)
                profiles.update { saved }
                automation.reconcile()
                log("Original profile restored; current user automation reconciled")
            }
        }
    }

    private suspend fun verifyFade() {
        val saved = profiles.profiles.first()
        check(!saved.enabled && saved.originalSettings == null)
        val original = bridge.requireWorker().snapshot()
        val target = ZonedDateTime.now().truncatedTo(ChronoUnit.MINUTES).plusMinutes(2)
        fun minute(time: ZonedDateTime) = time.hour * 60 + time.minute
        val trace = mutableListOf<Pair<String, Int>>()
        try {
            profiles.update { SamsungProfile(
                enabled = true,
                day = SamsungPhase(0, minute(target.minusHours(6)), 0),
                sunset = SamsungPhase(2, minute(target.minusHours(2)), 0),
                night = SamsungPhase(8, minute(target), 1),
            ) }
            SamsungAutomationService.start(this)
            withTimeout(15_000) {
                while (control.readState().level != 2 || !control.readState().active) delay(200)
            }
            withContext(Dispatchers.Main) { moveTaskToBack(true) }
            while (ZonedDateTime.now().isBefore(target.plusSeconds(7))) {
                val state = control.readState()
                check(state.active)
                trace += ZonedDateTime.now().toString() to state.level
                filesDir.resolve("samsung-fade-trace.txt").writeText(trace.joinToString("\n") { "${it.first} ${it.second}" })
                delay(2_000)
            }
            val levels = trace.map { it.second }
            check(levels.first() == 2 && levels.last() == 8) { "Wrong fade endpoints: $levels" }
            check(levels.zipWithNext().all { (a, b) -> b >= a }) { "Fade reversed: $levels" }
            check(levels.distinct().count { it in 3..7 } >= 3) { "Missing intermediate levels: $levels" }
        } finally {
            withContext(NonCancellable) {
                profiles.update { it.copy(enabled = false) }
                automation.reconcile()
                check(bridge.requireWorker().snapshot().contentEquals(original))
                check(profiles.profiles.first().originalSettings == null)
                profiles.update { saved }
            }
        }
    }
    private suspend fun verifyLifecycle() {
        val savedProfile = profiles.profiles.first()
        check(!savedProfile.enabled && savedProfile.originalSettings == null) { "Stop existing automation before the debug test" }
        val original = bridge.requireWorker().snapshot()
        var started = false
        suspend fun await(condition: suspend () -> Boolean) = withTimeout(15_000) {
            while (!condition()) delay(200)
        }
        fun fixedLevel(profile: SamsungProfile, level: Int) = profile.copy(
            day = profile.day.copy(level = level), sunset = profile.sunset.copy(level = level), night = profile.night.copy(level = level),
        )
        try {
            profiles.update { fixedLevel(SamsungProfile(enabled = true), 3) }
            started = true
            SamsungAutomationService.start(this)
            await { val state = control.readState(); state.active && state.level == 3 }
            profiles.update { it.copy(paused = true) }
            await { !control.readState().active }
            profiles.update { it.copy(paused = false, manualOverride = null, lastAppliedLevel = null) }
            await { control.readState().active }
            control.pause() // Same native change as switching Eye comfort shield off manually.
            await { profiles.profiles.first().isPaused }
            check(!control.readState().active)
            profiles.update { it.copy(paused = false, manualOverride = null, lastAppliedLevel = null) }
            await { control.readState().active }
            withContext(Dispatchers.Main) { moveTaskToBack(true) }
            runCatching { bridge.requireWorker().destroy() } // Only this app's child, never the Shizuku server.
            delay(300)
            profiles.update { fixedLevel(it, 7) }
            await { val state = control.readState(); state.active && state.level == 7 }
            check(profiles.profiles.first().originalSettings == original.toList())
        } finally {
            if (started) withContext(NonCancellable) {
                profiles.update { it.copy(enabled = false) }
                automation.reconcile()
                check(bridge.requireWorker().snapshot().contentEquals(original)) { "Original native settings did not restore" }
                check(profiles.profiles.first().originalSettings == null) { "Restore remains pending" }
                profiles.update { savedProfile }
            }
        }
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
