package io.github.paulsnuff.betternightlight.samsung

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import io.github.paulsnuff.betternightlight.MainActivity
import io.github.paulsnuff.betternightlight.R
import io.github.paulsnuff.betternightlight.domain.AutomationWorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SamsungAutomationService : Service() {
    @Inject lateinit var automation: SamsungAutomation
    @Inject lateinit var profiles: SamsungProfileRepository
    @Inject lateinit var device: SamsungDevice
    @Inject lateinit var watchdog: AutomationWorkScheduler
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lastNotification: String? = null
    private val wakeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { scope.launch { tick() } }
    }

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Automatic screen warmth", NotificationManager.IMPORTANCE_LOW),
        )
        val notification = notification("Starting automatic warmth…")
        if (Build.VERSION.SDK_INT >= 34) startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(ID, notification)
        ContextCompat.registerReceiver(this, wakeReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        watchdog.schedulePeriodicWork()
        scope.launch { profiles.profiles.collect { tick() } }
        scope.launch {
            device.observe().conflate().catch { error -> Log.w("BnlSamsungService", "Native observer unavailable; periodic checks remain active", error) }
                .collect { tick() }
        }
        scope.launch {
            while (true) { tick(); delay(5_000) }
        }
    }

    private suspend fun tick() {
        val message = try {
            automation.reconcile()
            val profile = profiles.profiles.first()
            if (!profile.enabled && profile.originalSettings == null) {
                watchdog.cancelPeriodicWork()
                stopSelf()
                return
            }
            automation.status.value.message
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w("BnlSamsungService", "Saved settings could not be read; retrying", error)
            "Could not read saved settings. Automatic updates are waiting."
        }
        if (message != lastNotification) {
            lastNotification = message
            getSystemService(NotificationManager::class.java).notify(ID, notification(message))
        }
    }

    private fun notification(message: String): Notification = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("Better Night Light Samsung")
        .setContentText(message)
        .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
        .setOngoing(true).setSilent(true).setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE).build()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch { tick() }
        return START_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { unregisterReceiver(wakeReceiver); scope.cancel(); super.onDestroy() }

    companion object {
        private const val CHANNEL = "samsung_circadian"
        private const val ID = 2
        fun start(context: Context) { ContextCompat.startForegroundService(context, Intent(context, SamsungAutomationService::class.java)) }
    }
}
