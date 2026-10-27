package io.github.paulsnuff.betternightlight.samsung

data class SamsungAboutPresentation(
    val supported: Boolean,
    val helperReady: Boolean,
    val helperMessage: String,
    val nativeActive: Boolean?,
    val warmth: Int?,
)

fun samsungAboutPresentation(supported: Boolean, connection: SamsungConnection, native: SamsungNativeState?): SamsungAboutPresentation =
    SamsungAboutPresentation(
        supported = supported,
        helperReady = connection == SamsungConnection.READY,
        helperMessage = when (connection) {
            SamsungConnection.READY -> "Connected"
            SamsungConnection.STOPPED -> "Start Shizuku"
            SamsungConnection.NEEDS_PERMISSION -> "Allow Shizuku access"
            SamsungConnection.CONNECTING -> "Connecting…"
            SamsungConnection.FAILED -> "Reconnect Shizuku"
        },
        nativeActive = native?.active,
        warmth = native?.takeIf { supported && it.active && it.level in 0..10 }?.level,
    )
