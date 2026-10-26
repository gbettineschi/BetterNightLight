package io.github.paulsnuff.betternightlight.samsung

import androidx.activity.ComponentActivity
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import rikka.shizuku.Shizuku
import javax.inject.Inject

/** Debug-only physical-device check. No launcher entry and excluded from release APKs. */
@AndroidEntryPoint
class SamsungProbeActivity : ComponentActivity() {
    @Inject lateinit var bridge: SamsungBridge
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = TextView(this).apply { setPadding(24, 24, 24, 24); setText("Samsung backend check: allow Shizuku when prompted.") }
        setContentView(text)
        scope.launch {
            try {
                bridge.requestAuthorization()
                withTimeout(90_000) { bridge.state.first { it == SamsungConnection.READY } }
                val result = withContext(Dispatchers.IO) {
                    val worker = bridge.requireWorker()
                    val before = worker.snapshot()
                    worker.restore(before)
                    check(worker.snapshot().contentEquals(before)) { "Restoration mismatch" }
                    "PASS: root-free helper uid=${Shizuku.getUid()}, same-value restore verified, values=${before.toList()}"
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
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
