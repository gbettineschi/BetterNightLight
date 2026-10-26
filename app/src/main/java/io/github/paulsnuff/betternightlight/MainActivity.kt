package io.github.paulsnuff.betternightlight

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.paulsnuff.betternightlight.shizuku.ShizukuPermissionManager
import io.github.paulsnuff.betternightlight.samsung.SamsungDevice
import io.github.paulsnuff.betternightlight.ui.AppRoot
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject
    lateinit var shizukuPermissionManager: ShizukuPermissionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Samsung uses the typed runtime helper; the AOSP permission grant is unnecessary there.
        if (!SamsungDevice.isSupported(this)) {
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    shizukuPermissionManager.tryGrantSilentlyInBackground()
                }
            }
        }

        enableEdgeToEdge()
        setContent {
            AppRoot()
        }
    }
}
