package io.github.paulsnuff.betternightlight.samsung

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku
import javax.inject.Inject
import javax.inject.Singleton

enum class SamsungConnection { STOPPED, NEEDS_PERMISSION, CONNECTING, READY, FAILED }

@Singleton
class SamsungBridge @Inject constructor(
    @ApplicationContext context: Context,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(SamsungConnection.STOPPED)
    val state = _state.asStateFlow()
    private val mutex = Mutex()
    @Volatile private var worker: ISamsungWorker? = null
    private val args = Shizuku.UserServiceArgs(ComponentName(context.packageName, SamsungWorker::class.java.name))
        .tag("samsung-eye-comfort").processNameSuffix("samsung").daemon(false).version(1)

    init {
        Shizuku.addBinderReceivedListenerSticky {
            refresh()
            if (_state.value == SamsungConnection.CONNECTING) scope.launch { runCatching { requireWorker() } }
        }
        Shizuku.addBinderDeadListener { worker = null; refresh() }
        Shizuku.addRequestPermissionResultListener { code, _ ->
            if (code == REQUEST_CODE) {
                refresh()
                if (_state.value == SamsungConnection.CONNECTING) scope.launch { runCatching { requireWorker() } }
            }
        }
        refresh()
    }

    fun refresh() {
        _state.value = when {
            !runCatching { Shizuku.pingBinder() }.getOrDefault(false) -> SamsungConnection.STOPPED
            !runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false) -> SamsungConnection.NEEDS_PERMISSION
            worker?.asBinder()?.isBinderAlive == true -> SamsungConnection.READY
            else -> SamsungConnection.CONNECTING
        }
    }

    fun requestAuthorization() {
        refresh()
        when (_state.value) {
            SamsungConnection.NEEDS_PERMISSION -> Shizuku.requestPermission(REQUEST_CODE)
            SamsungConnection.CONNECTING, SamsungConnection.READY, SamsungConnection.FAILED -> scope.launch { runCatching { requireWorker() } }
            SamsungConnection.STOPPED -> Unit
        }
    }

    suspend fun requireWorker(): ISamsungWorker = withContext(Dispatchers.IO) {
        mutex.withLock {
            refresh()
            check(_state.value != SamsungConnection.STOPPED) { "Start Shizuku to control Eye comfort shield." }
            check(_state.value != SamsungConnection.NEEDS_PERMISSION) { "Allow Better Night Light in Shizuku." }
            worker?.takeIf { it.asBinder().isBinderAlive }?.let { return@withLock it }
            val pending = CompletableDeferred<ISamsungWorker>()
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    if (binder == null || !binder.isBinderAlive) {
                        pending.completeExceptionally(IllegalStateException("Samsung helper is unavailable."))
                        return
                    }
                    try {
                        val connected = ISamsungWorker.Stub.asInterface(binder)
                        worker = connected
                        binder.linkToDeath({
                            if (worker?.asBinder() == binder) { worker = null; refresh() }
                        }, 0)
                        refresh()
                        pending.complete(connected)
                    } catch (error: Exception) {
                        worker = null
                        refresh()
                        pending.completeExceptionally(error)
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    worker = null
                    refresh()
                    pending.completeExceptionally(IllegalStateException("Samsung helper disconnected."))
                }
            }
            try {
                Shizuku.bindUserService(args, connection)
                withTimeout(12_000) { pending.await() }
            } catch (error: Throwable) {
                runCatching { Shizuku.unbindUserService(args, connection, false) }
                worker = null
                refresh()
                if (_state.value == SamsungConnection.CONNECTING) _state.value = SamsungConnection.FAILED
                throw error
            }
        }
    }

    private companion object { const val REQUEST_CODE = 8792 }
}
