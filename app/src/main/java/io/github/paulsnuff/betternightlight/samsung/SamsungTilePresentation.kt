package io.github.paulsnuff.betternightlight.samsung

data class SamsungTilePresentation(val active: Boolean, val subtitle: String, val available: Boolean = true)

fun samsungTilePresentation(profile: SamsungProfile, connection: SamsungConnection, status: SamsungAutomationStatus, supported: Boolean): SamsungTilePresentation = when {
    !supported -> SamsungTilePresentation(false, "Samsung required", false)
    connection != SamsungConnection.READY -> SamsungTilePresentation(false, "Connect Shizuku")
    status.error -> SamsungTilePresentation(false, "Check app")
    !profile.enabled -> SamsungTilePresentation(false, "Off")
    profile.manualOverride != null -> SamsungTilePresentation(false, "Manual control")
    profile.paused -> SamsungTilePresentation(false, "Paused")
    else -> SamsungTilePresentation(true, "Automatic")
}
