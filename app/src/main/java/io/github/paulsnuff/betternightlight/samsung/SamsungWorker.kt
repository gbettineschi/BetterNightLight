package io.github.paulsnuff.betternightlight.samsung

import android.content.Context
import android.os.Process
import android.util.Log
import androidx.annotation.Keep
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/** Runs as Shizuku's shell user. Exposes only the five filter settings, never arbitrary commands. */
@Keep
class SamsungWorker : ISamsungWorker.Stub {
    @Keep constructor() : super() { Log.i(TAG, "Samsung worker uid=${Process.myUid()}") }
    @Keep @Suppress("UNUSED_PARAMETER") constructor(context: Context?) : this()

    override fun snapshot(): IntArray {
        val values = SamsungSettings.keys.map { key ->
            command(listOf("/system/bin/settings", "--user", "current", "get", "system", key)).trim().toIntOrNull()
                ?: throw IllegalStateException("Cannot read original Samsung setting: $key")
        }
        SamsungSettings.restoreWrites(values)
        return values.toIntArray()
    }

    override fun applyLevel(level: Int) {
        write(SamsungSettings.writesForLevel(level))
        refresh(level > 0)
    }

    override fun pause() {
        write(listOf("blue_light_filter" to 0))
        refresh(false)
    }

    override fun restore(values: IntArray) {
        val writes = SamsungSettings.restoreWrites(values.toList())
        write(writes)
        refresh(values.last() == 1)
    }

    private fun write(values: List<Pair<String, Int>>) {
        values.forEach { (key, value) ->
            command(listOf("/system/bin/settings", "--user", "current", "put", "system", key, value.toString()))
        }
    }

    private fun refresh(active: Boolean) {
        command(listOf(
            "/system/bin/am", "start-service", "--user", "current", "-n",
            "com.samsung.android.bluelightfilter/.BlueLightFilterService", "--ei",
            "BLUE_LIGHT_FILTER_SERVICE_TYPE", if (active) "21" else "22",
        ))
        Log.i(TAG, "Samsung native refresh active=$active")
    }

    private fun command(args: List<String>): String {
        val process = ProcessBuilder(args).redirectErrorStream(true).start()
        try {
            check(process.waitFor(5, TimeUnit.SECONDS)) { "Samsung command timed out" }
            val output = process.inputStream.bufferedReader().use { it.readText() }
            check(process.exitValue() == 0 && !output.contains("Error:", true) &&
                !output.contains("Permission Denial", true) && !output.contains("Exception", true)) {
                "Samsung command rejected: ${output.take(500)}"
            }
            return output
        } finally {
            process.destroy()
        }
    }

    override fun destroy() { exitProcess(0) }

    private companion object { const val TAG = "BnlSamsungWorker" }
}
