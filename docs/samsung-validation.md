# Samsung validation record — 26 October 2026

## Physical device evidence

Galaxy S23 SM-S911B, Android 17 / One UI 9, verified boot enabled. No root or bootloader modification.

Passed:

- Shell UID 2000 wrote Samsung’s Custom-mode strength and refreshed its native service. Samsung mDNIe logs showed indices 3 and 7; the owner confirmed seeing the warmth change and return.
- An ordinary modern app could not write Samsung’s private System keys even with `WRITE_SECURE_SETTINGS`. The fork’s typed Shizuku worker ran as UID 2000 and passed snapshot / same-value restore / readback.
- Automation applied levels in foreground and background; pause/resume worked; native manual-off became a persisted pause.
- Restarting only this app’s Shizuku child and changing the target reconnected and applied level 7. The global Shizuku server was kept running.
- Disabling automation restored the exact original five controlled values. All eight separately recorded native settings, including untouched night-dim and schedule times, matched the original afterward.
- The Samsung Home layout, warmth sliders and transition/time controls were visible on the phone. Editing fade duration persisted the change.

## Pending physical checks

The phone was disconnected, and the owner requested completing the build before reconnecting later.

- Dedicated Quick Settings tile: actual add / enable / pause / resume / long-press behavior.
- A one-minute scheduled fade through several intermediate native levels, using the debug-only fixture and a timestamped trace.
- The fix preventing offscreen pager pages from stealing focus after a dialog closes. The prior build saved fade edits but unexpectedly selected Settings; the final fix has compiled, but has not been retested on hardware.
- Optimized signed APK installation and helper connection. Earlier physical checks used the debug variant of this same backend.
- Full phone reboot, global Shizuku restart, overnight operation, aggressive battery management and other One UI versions.

A stock emulator does not contain Samsung’s service, so an emulator would not prove these hardware checks.

## Automated checks

The JVM suite covers actual schedule interpolation (including midnight, overlapping fades and daylight-saving instants), atomic DataStore persistence, coordinator takeover/restore and concurrent edits, manual-off persistence, malformed snapshots, solar fallback and truthful tile presentation. Build/lint results are recorded with delivery.

A new regression test reproduced a stop/re-enable/pause race during native restoration; the coordinator now preserves the latest requested pause. The test failed before the fix and passed afterward.

## Cleanup to finish on reconnection

Automation was off and original native settings matched before disconnection. A temporary USB screen-awake setting remains enabled: `stay_on_while_plugged_in` was originally `0` and must be restored to `0` when the phone reconnects. No reboot or global Shizuku-server stop was performed.
