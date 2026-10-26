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

The JVM suite covers actual schedule interpolation (including midnight, overlapping fades and daylight-saving instants), atomic DataStore persistence, coordinator takeover/restore and concurrent edits, manual-off persistence, malformed snapshots, solar fallback and truthful tile presentation. All 45 JVM tests pass. Both debug and optimized signed release builds and debug/release lint are required for delivery; final results are appended below.

A new regression test reproduced a stop/re-enable/pause race during native restoration; the coordinator now preserves the latest requested pause. The test failed before the fix and passed afterward.

## Cleanup to finish on reconnection

Automation was off and original native settings matched before disconnection. A temporary USB screen-awake setting remains enabled: `stay_on_while_plugged_in` was originally `0` and must be restored to `0` when the phone reconnects. No reboot or global Shizuku-server stop was performed.

## Independent source review and fixes

A fresh read-only reviewer found five important issues; all were reproduced with failing regressions, then fixed in one pass:

1. Invalid scheduling settings blocked native Pause. Pause now runs independently of validity; a tile click may also pause an invalid running schedule.
2. Active-tile metadata caused stale status without explicit update requests. The tile now uses Android’s ordinary panel-listening lifecycle; its real manifest is checked by a regression.
3. Spring DST could collapse distinct wall-clock times into one instant. Resolved collisions now use Night > Sunset > Day priority; tests cover the full resolver on the clock-change day and neighboring dates.
4. A failed automatic-off write looked like manual-off on retry. Write intent is now persisted before native operations; a regression restarts the coordinator after partial failure and verifies the next evening still runs.
5. Backup included device-specific native snapshots and ownership. The canonical local profile now lives in Android’s no-backup directory, with only portable target preferences mirrored to the backed-up store. Tests verify backup contents, safe legacy import, atomic edits and local snapshot persistence.

The reviewer reported no critical issues or deferred minors. No second review was commissioned; the fixes are covered by failing-then-passing regressions and the complete suite.

## Decisions and scope

- A distinct application ID protects any upstream installation. Its existing preferences are not automatically imported.
- Physical tile / fade / dialog-focus / optimized-APK checks remain pending at the owner’s request to finish the build after disconnection. The cost is unverified final hardware behavior, not a claim of completed acceptance.
- Full reboot, global Shizuku restart, overnight battery management and other Samsung versions remain untested; no disruptive phone actions were taken to extend this evidence.
- The upstream AOSP internals remain as provided. Compilation is checked; AOSP hardware acceptance is outside this Samsung task.
