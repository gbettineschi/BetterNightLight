# Samsung Three-Phase Night Light Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add root-free Samsung Eye comfort shield control, three warmth targets with separate fade durations, and a dedicated pause/resume tile to the authorized Better Night Light fork.

**Architecture:** Keep the AOSP implementation intact. A Samsung-specific profile and pure schedule calculator drive a typed Shizuku user service. A foreground coordinator owns persistence, manual-off handling, restoration, and visible failure status; a Compose screen and tile share its state.

**Tech Stack:** Existing Kotlin/Compose, Hilt, DataStore, Shizuku 13.1.5, commons-suncalc, JUnit 4, Android SDK 37 / JDK 25.

**Spec:** ../specs/2026-10-26-samsung-three-phase-design.md

## Global Constraints

- Native Samsung Eye comfort shield, no overlay; no root required.
- Three warmth sliders: Day, Sunset, and Night; Samsung levels 0-10, not calibrated Kelvin.
- Separate fade durations; targets reached at sunrise, sunset, and chosen night time, with manual times available.
- Pause persists until resumed; manual native switch-off is respected.
- Preserve and restore the original native settings; report lost helper access honestly.
- A build or emulator run does not establish Samsung hardware behavior.

## Review Focus

- Midnight and daylight-saving transitions must not reset a fade.
- Overlapping fades must remain bounded by their surrounding target times.
- Concurrent UI/tile/service edits must not re-enable a disabled schedule or lose its snapshot.
- Helper death and partial native writes must preserve restoration information.
- Native manual-off must remain paused across process restart.

### Task 1: Pure schedule and native-setting contracts

**Files:** New `samsung/SamsungProfile.kt`, `SamsungSchedule.kt`, `SamsungSettings.kt`; matching tests under `app/src/test/java/io/github/paulsnuff/betternightlight/samsung/`. Add JUnit test dependency.

**Interfaces:** `SamsungProfile` contains enabled/paused, three integer levels, manual target minutes, three fade durations, optional solar coordinates, original-setting snapshot and last-applied level. `TimedTarget(at: Instant, level: Int, fadeMinutes: Int)` feeds `interpolateLevel(now: Instant, targets: List<TimedTarget>): Int`. `SamsungSettings` fixes the five setting keys and builds bounded takeover/restore values.

- [ ] Write tests: noon hold=0, 17:30 halfway into 0->6 evening fade=3, target time=6, 00:30 halfway into 2->8 night fade ending 01:00=5; zero duration holds until anchor; overlapping fade is shortened; equal levels stay constant; out-of-range levels/duplicate target times rejected; DST uses actual instants.
- [ ] Run tests and confirm missing implementation failures; implement and rerun.
- [ ] Test five native keys and exact restoration of an adaptive original configuration; reject malformed snapshots before any write.
- [ ] Commit the tested schedule and contracts.

### Task 2: Typed Samsung Shizuku bridge

**Files:** New AIDL `samsung/ISamsungWorker.aidl`; `SamsungWorker.kt`, `SamsungBridge.kt`, `SamsungDevice.kt`.

**Interfaces:** Worker `snapshot(): int[]`, `applyLevel(int level)`, `pause()`, `restore(in int[] values)`, `destroy()`. Bridge exposes `StateFlow` connection state, explicit authorization, and a bounded suspend worker connection. Device detects Samsung plus the real exported service and reads the native active/strength state.

- [ ] Use the proven settings-first/service-refresh path; write strength before activation, and check every command's exit/output with timeouts.
- [ ] Keep command construction limited to the five fixed keys, bounded integers, and fixed service component. Preserve native snapshot on every failure.
- [ ] Handle permission rejection, binder loss, reconnection, and request timeout without blocking the UI.
- [ ] Build and verify a real app connection through the phone's already-running Shizuku; confirm UID and same-value write/refresh.
- [ ] Commit the backend.

### Task 3: Persistence and automation lifecycle

**Files:** New `SamsungProfileRepository.kt`, `SamsungAutomation.kt`, `SamsungAutomationService.kt`; extend boot receiver and manifest.

**Interfaces:** Repository provides atomic `update((SamsungProfile)->SamsungProfile)` and a profile Flow. Automation `reconcile()` serializes native operations, uses the current persisted profile under its lock, and exposes current/error status. Service owns ticks and screen-on reconciliation.

- [ ] Exercise pause/manual-off decisions with literal inputs before implementation; persist last-applied level for restart detection.
- [ ] Save the original snapshot before the first write. Disable restores it before clearing; helper failure keeps a pending restore and visible status.
- [ ] Apply only changed levels; reconcile on screen wake, time/timezone change and profile update, with periodic foreground ticks.
- [ ] Resume after boot/update with clear waiting status until Shizuku is restarted; do not obtain root or bypass a denied authorization.
- [ ] Verify disable/restore and helper-loss behavior on the S23, and commit.

### Task 4: Home screen and Quick Settings tile

**Files:** New `SamsungHomeScreen.kt`, `SamsungHomeViewModel.kt`, `SamsungTileService.kt`; route Samsung devices in `HomeScreen.kt`; manifest tile declaration and distinct fork application ID.

- [ ] Add simple Day/Sunset/Night sliders, fade minutes, time pickers, solar/manual selection, coordinates and existing device-location provider access.
- [ ] Show actual helper/automation/native state, an explicit Shizuku connection action, pause/resume and stop/restore controls.
- [ ] Tile uses the same atomic profile and reconciliation path; unavailable helper cannot look like successful control. Long press opens the app.
- [ ] Validate visible controls, target edits and tile state on the phone; verify AOSP builds retain their existing route. Commit.

### Task 5: Final verification and delivery

- [ ] Run the complete JVM suite, debug build, and lint; record failures rather than hiding them.
- [ ] Install under a distinct application ID; test actual three-stage settings, short fade, background operation, manual-off, tile pause/resume and exact restore. Keep reboot validation distinct if a real reboot is not performed.
- [ ] Obtain a fresh whole-branch review using the executing-plans review procedure, fix material findings with regression tests, and rerun required checks.
- [ ] Update README with setup, Shizuku restart requirement, eleven-level limitation, restoration behavior and verified/unverified scope.
- [ ] Commit and push the feature branch to the explicitly requested fork, copy a signed APK into outputs, and provide the fork/branch and APK links.
