# Samsung validation record — 26–27 October 2026

## Physical device evidence

Galaxy S23 SM-S911B, Android 17 / One UI 9, verified boot enabled. No root or bootloader modification.

Passed:

- Shell UID 2000 wrote Samsung’s Custom-mode strength and refreshed its native service. Samsung mDNIe logs showed indices 3 and 7; the owner confirmed seeing the warmth change and return.
- An ordinary modern app could not write Samsung’s private System keys even with `WRITE_SECURE_SETTINGS`. The fork’s typed Shizuku worker ran as UID 2000 and passed snapshot / same-value restore / readback.
- Automation applied levels in foreground and background; pause/resume worked; native manual-off became a persisted pause.
- Restarting only this app’s Shizuku child and changing the target reconnected and applied level 7. The global Shizuku server was kept running.
- Disabling automation restored the exact original five controlled values. All eight separately recorded native settings, including untouched night-dim and schedule times, matched the original afterward.
- The Samsung Home layout, warmth sliders and transition/time controls were visible on the phone. Editing fade duration persisted the change.

## Reconnected S23 acceptance — 27 October 2026

Passed on the actual phone:

- Installed the final optimized, signed `1.0.4-samsung.1` APK. Its fresh helper ran as shell UID 2000; its package is not debuggable. The existing Shizuku authorization was retained.
- Fade-duration edit 60 → 45 → 60 and target-time edit 07:00 → 07:15 → 07:00 both saved and stayed on Home. Day warmth was changed to 3 for testing, then restored to 0.
- A one-minute background fade advanced monotonically through **2, 3, 4, 5, 6, 7, 8**, reached 8 at its target time, then restored the original configuration. The test coordinator used the debug variant; Samsung's native mDNIe display log independently confirmed every requested index. See the [timestamped trace](samsung-fade-trace-2026-10-27.csv) and [native display log](samsung-native-fade-log-2026-10-27.txt).
- The owner was not watching the fade. The recorded display-index progression proves operation, while subjective visual smoothness remains for normal use.
- Added the app's own **Night Light** tile to Quick Settings. Actual screen taps enabled native warmth 3, paused the filter, and resumed warmth 3. Long press opened the app.
- Tapping Samsung's own Eye comfort shield tile off caused the app to persist Paused and update its own tile. The app tile then resumed control.
- Turning the app's master switch off restored all eight recorded original native settings. Reopening Quick Settings refreshed the tile to Off, verifying the fix for stale external changes.

## Remaining reliability limits

Full phone reboot, global Shizuku-server restart, overnight operation, aggressive battery management and other One UI versions remain untested. A stock emulator does not contain Samsung's service. Physical short tests do not establish these longer-term results.

## Automated checks

The JVM suite covers actual schedule interpolation (including midnight, overlapping fades and daylight-saving instants), atomic DataStore persistence, coordinator takeover/restore and concurrent edits, manual-off persistence, malformed snapshots, solar fallback and truthful tile presentation. All 45 JVM tests pass. Both debug and optimized signed release builds and debug/release lint are required for delivery; final results are appended below.

A new regression test reproduced a stop/re-enable/pause race during native restoration; the coordinator now preserves the latest requested pause. The test failed before the fix and passed afterward.

## State after initial acceptance

The installed release is left with automation off and original Samsung filter settings restored. Day / Sunset / Night defaults remain 0 / 5 / 10, manual target times 07:00 / 18:00 / 23:00, and fade durations 60 / 60 / 90 minutes. The app's tile remains added to the expanded Quick Settings grid. The temporary USB screen-awake setting is restored to its original `0`. The Shizuku server remains running; no phone reboot or global Shizuku stop was performed.

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
- Physical checks were deferred during disconnection, then completed on reconnection as recorded above. Subjective fade smoothness was not judged by the owner.
- Full reboot, global Shizuku restart, overnight battery management and other Samsung versions remain untested; no disruptive phone actions were taken to extend this evidence.
- The upstream AOSP internals remain as provided. Compilation is checked; AOSP hardware acceptance is outside this Samsung task.

## Final build result

`testDebugUnitTest assembleDebug lintDebug assembleRelease lintRelease` succeeded after the review fixes (45 tests, zero failures/errors/skips). The optimized release APK is signed with the same certificate as the installed development APK, contains the Samsung tile, excludes the debug-only probe, and is not debuggable. It was subsequently installed and physically checked on reconnection as recorded above. Existing upstream warnings about dependency versions, translations, an unused expression and native-library stripping remain; lint reports no errors.

## About and solar-offset update — 1.0.4-samsung.2

Signed update installed on the same S23 with its existing authorization and settings preserved. About now checks the Samsung service, live Shizuku connection and native filter state; AOSP-only support/secure-permission warnings are absent. Numeric warmth is shown only for the verified fixed Custom configuration; other active Samsung modes display “Managed by Samsung.” Source links identify the fork and credit the original project.

Sunrise and sunset have independent −240…+240 minute offsets, default 0. Their ±5-minute buttons and exact signed-minute dialogs were physically checked (including −17 and +20), then restored to 0. The owner's original solar/manual mode and automation activation were preserved. The temporary screen-awake setting was restored. No coordinates or other schedule targets were changed.

All **61 JVM tests** pass, along with debug/release builds and both lints. Coverage includes zero/default migration, real DataStore/portable persistence, earlier/later targets, offset fade completion, midnight crossing, DST elapsed minutes, null-event manual fallback and cross-date phase collision priority. A fresh review found two important issues; adaptive warmth reporting and live preview-calendar refresh were corrected with three observed failing-then-passing tests. There were no critical findings or deferred minors. An initial missing English/Polish credit translation failed lint; both translations were added and lint passed without suppression.

The preview uses the same solar shift as scheduling and refreshes on date/time/timezone/minute events while visible. The calendar flow's midnight and timezone changes are unit-tested without a pause/resume cycle; the phone's actual clock/timezone were not changed for testing. Earlier reboot/overnight/other-device limits remain.

## Native manual control and automatic resume — 1.0.4-samsung.3

The user requested the three manual-change choices shown in their reference. Native Samsung activation, warmth and mode changes now start a separate manual override; the app leaves subsequent native changes alone. Choices are next scheduled transition (default), one hour from the latest observed native change, or manual Resume. The next transition means its clipped fade start, with instant/equal-level phases using the anchor; individual steps and the current fade's end do not cancel the override. Resume reapplies the schedule's current value. Explicit own-app/tile Pause keeps its prior filter-off behavior.

Automated evidence: **87 JVM tests**, debug/release builds and both lints passed. Tests include native slider/off/on changes, scheduled zero, renewed hour, changes at expiry, coordinator restart, policy and schedule edits, guarded slow reads, original restoration, partially failed automatic-off, portable policy versus device-only ownership, and next-transition boundaries across offsets/midnight/DST/overlapping fades. A fresh read-only review found no critical, important or minor issue requiring changes.

Physical S23 evidence:

- A debug-only fixture held native warmth 7, switched native off and back on at 5 without automation undoing it.
- ONE_HOUR expiry and return to scheduled native level 3 were exercised by aging only the app's test timestamp by 3596 seconds. This is **not a real one-hour wait**; the full 3600-second boundary is covered by the injected-clock JVM tests.
- NEXT_CHANGE held native level 6 until an actual scheduled instant at 10:49 Europe/Zurich, then resumed native level 8. See the [timestamped trace](samsung-manual-control-trace-2026-10-27.txt).
- The final optimized signed release was installed. All three radio choices were exercised. The actual Samsung Quick Settings tile switched off and back on without being overridden; its long-press opened Samsung settings. The actual Samsung colour-temperature slider was moved to level 8 and remained there through subsequent reconciliations.
- Choosing one hour showed a resume time. Resume in the app and in its own tile both returned to the current scheduled value. The owner's original profile, native eight-key baseline, schedule activation and screen-awake preference were restored. Default NEXT_CHANGE is selected.

A mere tap that leaves every observed native value unchanged is not a detectable change; tracking the gesture itself would require additional UI integration/permissions, which were not introduced. Full reboot, an actual unattended one-hour wait, overnight operation and other-device/One UI acceptance remain unverified. No system clock or global Shizuku-server stop was used.
