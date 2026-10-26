# Better Night Light Samsung

A personal Samsung-compatible fork of [Better Night Light by paulsnuff](https://github.com/paulsnuff/BetterNightLight), licensed under [GPL v3 or later](LICENSE). It controls Samsung’s native **Eye comfort shield** through Shizuku, without root or a screen overlay.

## Samsung features

- **Day, Sunset and Night** warmth targets, each with its own fade duration (0–240 minutes).
- Manual target times, or sunrise and sunset from a saved location; Night has a chosen clock time.
- Eleven native Custom-mode warmth levels, 0–10. These are not calibrated Kelvin values. A long fade advances through these discrete levels rather than using an overlay to interpolate finer colors.
- A dedicated **Night Light Quick Settings tile**: first tap enables the schedule; subsequent taps pause/resume. Long press opens the app. The app’s master switch stops automation and restores the previous Samsung configuration.
- Persistent pause, including when you switch Eye comfort shield off yourself.
- Original native settings saved before takeover; failed restoration remains pending for retry.
- A foreground service, wake/time-change reconciliation, and a periodic recovery worker. Errors and lost helper access are visible.

## Setup

1. Install this fork’s APK (`io.github.gbettineschi.betternightlight`, displayed as **Better Night Light Samsung**).
2. Install [Shizuku](https://github.com/RikkaApps/Shizuku), start it using wireless debugging or USB ADB, then allow this app to access it. **Keep Shizuku running. Restart it after every phone reboot.** A one-time `WRITE_SECURE_SETTINGS` grant alone cannot write Samsung’s private filter settings on the tested phone.
3. Open the app, choose the three targets and fade durations, then enable Automatic warmth. While enabled, this app takes over Samsung’s Custom schedule; turning it off restores the saved configuration.
4. Tap **Add Quick Settings tile**, or add **Night Light** from Quick Settings → Edit. Allow notifications and unrestricted background operation if Android asks.

When Shizuku is unavailable, automatic updates and this app’s tile cannot control the native filter. The phone’s own Eye comfort shield tile remains available to turn it off. Reopen this app and reconnect Shizuku to finish any pending restore.

Sunrise/sunset uses saved coordinates; fetching a location is an explicit foreground action. If solar times cannot be calculated, manual times are used. Keep the three manual times distinct. System time-zone changes are followed automatically. If two targets resolve to the same instant (for example during the spring clock change), Night takes priority over Sunset, then Day.

## Compatibility and validation

The Samsung backend was tested on a physical **Galaxy S23 (SM-S911B), Android 17 / One UI 9**. The helper ran with ADB shell UID 2000, without root. A standard Android emulator does not contain Samsung’s native service, so it cannot validate this backend.

See [device validation](docs/samsung-validation.md) for exact checks and remaining limits. Short tests do not establish overnight reliability, and full phone reboot / global Shizuku-server restart are not part of the physical test record.

Other Samsung / One UI versions may use a different private service. Detection checks for the tested service and settings; it is not a compatibility guarantee. The existing AOSP Night Light screen remains available on non-Samsung devices, but its hardware path was not retested as part of this fork.

Schedule preferences are portable, but enabled/pause state and native restoration snapshots stay in Android’s no-backup directory. Reinstallation or device transfer starts automation off and captures the new device’s settings only when enabled. Any legacy pre-release snapshot in backed-up preferences is discarded, while target preferences are retained.

## Build

Use JDK 25 and Android SDK 37 (build-tools 36.0.0):

```sh
./gradlew testDebugUnitTest assembleDebug lintDebug
```

For a signed, optimized release build, supply `SIGNING_STORE_FILE`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` and `SIGNING_KEY_PASSWORD` through the environment, then run `./gradlew assembleRelease`. Keep the same private signing key for future updates; do not commit it. The debug-only physical test activity is excluded from release builds.

There is no automated release/update feed for this personal Samsung branch yet. The upstream project’s APKs do not contain this Samsung backend.
