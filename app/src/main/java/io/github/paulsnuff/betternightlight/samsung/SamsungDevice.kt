package io.github.paulsnuff.betternightlight.samsung

import android.content.ComponentName
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SamsungDevice @Inject constructor(@ApplicationContext private val context: Context) {
    fun read(): SamsungNativeState {
        val resolver = context.contentResolver
        return SamsungNativeState(
            active = Settings.System.getInt(resolver, "blue_light_filter") == 1,
            level = Settings.System.getInt(resolver, "blue_light_filter_opacity"),
            customMode = Settings.System.getInt(resolver, "blue_light_filter_adaptive_mode") == 0 &&
                Settings.System.getInt(resolver, "blue_light_filter_scheduled") == 1 &&
                Settings.System.getInt(resolver, "blue_light_filter_type") == 0,
        )
    }

    fun observe(): Flow<SamsungNativeState> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) { runCatching { trySend(read()) } }
        }
        val resolver = context.contentResolver
        SamsungSettings.keys.forEach { resolver.registerContentObserver(Settings.System.getUriFor(it), false, observer) }
        runCatching { trySend(read()) }
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.distinctUntilChanged()

    companion object {
        fun isSupported(context: Context): Boolean = Build.MANUFACTURER.equals("samsung", ignoreCase = true) && runCatching {
            @Suppress("DEPRECATION")
            val service = context.packageManager.getServiceInfo(ComponentName("com.samsung.android.bluelightfilter", "com.samsung.android.bluelightfilter.BlueLightFilterService"), 0)
            service.enabled && service.exported && service.permission == null &&
                Settings.System.getString(context.contentResolver, "blue_light_filter_opacity") != null
        }.getOrDefault(false)
    }
}
