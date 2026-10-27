package io.github.paulsnuff.betternightlight.samsung

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShizukuSamsungControl @Inject constructor(
    private val bridge: SamsungBridge,
    private val device: SamsungDevice,
) : SamsungControl {
    override suspend fun readState(): SamsungNativeState = withContext(Dispatchers.IO) { device.read() }
    override suspend fun snapshot(): List<Int> = withWorker { it.snapshot().toList() }
    override suspend fun applyLevel(level: Int) = withWorker { it.applyLevel(level) }
    override suspend fun pause() = withWorker { it.pause() }
    override suspend fun restore(original: List<Int>) = withWorker { it.restore(original.toIntArray()) }

    private suspend fun <T> withWorker(action: (ISamsungWorker) -> T): T = withContext(Dispatchers.IO) {
        try {
            action(bridge.requireWorker())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w("BnlSamsungControl", "Samsung operation failed; retaining original snapshot", error)
            throw error
        }
    }
}
