# BetterNightLight-Samsung

A personal fork of [paulsnuff/BetterNightLight](https://github.com/paulsnuff/BetterNightLight), made to schedule Samsung’s native **Eye comfort shield** on my Galaxy S23. I added a Shizuku backend, three-phase warmth schedules, solar offsets, a dedicated tile and controls that respect Samsung’s own slider and toggle.

**My changes are 100% vibe-coded, built for personal use, and provided as is.** No warranty or promised support. The original app is the work of its upstream authors; the [GPL v3 or later licence](LICENSE) is retained.

No root or screen overlay is needed. **Shizuku must stay running.** Tested on **Galaxy S23 (SM-S911B), Android 17 / One UI 9**; other Samsung versions are not yet verified.

**[Download the APK](https://github.com/gbettineschi/BetterNightLight-Samsung/releases/latest)** · [Obtainium](#updates--obtainium) · [Changes from upstream](docs/fork-changes.md) · [Validation](docs/samsung-validation.md)

## Features

- **Day, Sunset and Night** warmth targets, each with its own fade duration (0–240 minutes).
- Manual target times, or sunrise and sunset from a saved location; Night has a chosen clock time. Sunrise and sunset each have an independent −240…+240 minute offset (negative earlier, positive later), with quick ±5-minute buttons and exact signed-minute entry. Fades finish at the adjusted times; manual fallback times stay unchanged.
- Eleven native Custom-mode warmth levels, 0–10. These are not calibrated Kelvin values. A long fade advances through these discrete levels rather than using an overlay to interpolate finer colors.
- A dedicated **Night Light Quick Settings tile**: first tap enables the schedule; subsequent taps pause/resume. Long press opens the app. The app’s master switch stops automation and restores the previous Samsung configuration.
- **Native manual control:** changes from Samsung’s own slider or toggle are respected, with a choice of when automation resumes (below).
- **Samsung-aware About screen:** reports native-service support, the live Shizuku connection and current filter state.
- Original native settings saved before takeover; failed restoration remains pending for retry.
- A foreground service, wake/time-change reconciliation, and a periodic recovery worker. Errors and lost helper access are visible.

## Manual changes while automation is on

When you actually change Samsung’s warmth, on/off state or mode, automation pauses its own adjustments and keeps your native setting. Samsung’s controls remain free to use. Choose the behavior under **Home → Manual changes in Samsung**:

| Resume choice | Behavior |
| --- | --- |
| Until the next scheduled change (default) | Resume at the next phase’s fade start, or its target time for an instant/equal-level transition. |
| For one hour | Resume one hour after the latest observed native change. Further changes restart the hour. |
| Until I tap Resume | Keep manual control until you resume from the app or its tile. |

An individual step of a running fade does not count as the next scheduled change. Resume reapplies the schedule’s current warmth. Opening Samsung settings or tapping a value that remains unchanged does not pause automation.

The app’s own **Pause** button/tile switches the filter off until Resume. The master **Off** switch stops automation and restores the Samsung configuration saved before takeover.

## Setup

1. Download the `.apk` from [this fork’s Releases](https://github.com/gbettineschi/BetterNightLight-Samsung/releases/latest), allow installation from your browser/file manager if Android asks, and install it. The app is displayed as **Better Night Light Samsung**, with package `io.github.gbettineschi.betternightlight`. It can coexist with the upstream app; avoid running two filter schedulers together.
2. Install [Shizuku](https://github.com/RikkaApps/Shizuku), start it using wireless debugging or USB ADB, then allow this app to access it. **Keep Shizuku running. Restart it after every phone reboot.** A one-time `WRITE_SECURE_SETTINGS` grant alone cannot write Samsung’s private filter settings on the tested phone.
3. Open the app, choose the three targets and fade durations, then enable Automatic warmth. While enabled, this app takes over Samsung’s Custom schedule; turning it off restores the saved configuration.
4. Tap **Add Quick Settings tile**, or add **Night Light** from Quick Settings → Edit. Allow notifications and unrestricted background operation if Android asks.

When Shizuku is unavailable, automatic updates and this app’s tile cannot control the native filter. The phone’s own Eye comfort shield tile remains available to turn it off. Reopen this app and reconnect Shizuku to finish any pending restore.

Sunrise/sunset uses saved coordinates; fetching a location is an explicit foreground action. If solar times cannot be calculated, manual times are used. Keep the three manual times distinct. System time-zone changes are followed automatically. If two targets resolve to the same instant (for example during the spring clock change), Night takes priority over Sunset, then Day.

About reports the Samsung backend, live Shizuku connection and native filter state. The one-time AOSP secure-settings permission check does not apply to Samsung.

## Updates / Obtainium

Add `https://github.com/gbettineschi/BetterNightLight-Samsung` in [Obtainium](https://github.com/ImranR98/Obtainium), or [open the prepared import](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22io.github.gbettineschi.betternightlight%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fgbettineschi%2FBetterNightLight-Samsung%22%2C%22author%22%3A%22gbettineschi%22%2C%22name%22%3A%22Better%20Night%20Light%20Samsung%22%7D). Releases contain one signed APK: no APK filter, login or token is needed for this app. **Include prereleases is not required**; this fork uses normal releases. The version in the tag matches the APK’s version.

Install updates over the existing app to preserve its settings and Shizuku authorization. Do not uninstall or clear data to get around a signing mismatch. Manual downloads work too. The upstream project’s APKs do not contain the Samsung backend and use a different package ID. There is no in-app updater.

The first public release, **1.0.4-samsung.3**, uses the same APK and signing certificate as the version already checked on the S23. The certificate SHA-256 fingerprint is:

```text
B478B60B64740B48DA9CF33504214F3E0B1C2D4F4996F856D5946E3080F47F6F
```

## Compatibility and validation

The Samsung backend was tested on a physical **Galaxy S23 (SM-S911B), Android 17 / One UI 9**. The helper ran with ADB shell UID 2000, without root. A standard Android emulator does not contain Samsung’s native service, so it cannot validate this backend.

See [device validation](docs/samsung-validation.md) for exact checks and remaining limits. Short tests do not establish overnight reliability, and full phone reboot / global Shizuku-server restart are not part of the physical test record.

Other Samsung / One UI versions may use a different private service. Detection checks for the tested service and settings; it is not a compatibility guarantee. The existing AOSP Night Light screen remains available on non-Samsung devices, but its hardware path was not retested as part of this fork.

Schedule preferences are portable, but enabled/pause state and native restoration snapshots stay in Android’s no-backup directory. Reinstallation or device transfer starts automation off and captures the new device’s settings only when enabled. Any legacy pre-release snapshot in backed-up preferences is discarded, while target preferences are retained.

For bug reports, include your Samsung model, Android/One UI version, app version, Shizuku status and steps to reproduce. Please omit saved coordinates or other personal information. Use [this fork’s issue tracker](https://github.com/gbettineschi/BetterNightLight-Samsung/issues) for Samsung-specific problems.

## Build

Use JDK 25 and Android SDK 37 (SDK Manager package `platforms;android-37.0`, build-tools 36.0.0):

```sh
./gradlew testDebugUnitTest assembleDebug lintDebug
```

For a signed, optimized release build, supply `SIGNING_STORE_FILE`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` and `SIGNING_KEY_PASSWORD` through the environment, then run `./gradlew assembleRelease`. Keep the same private signing key for future updates; do not commit it. The debug-only physical test activity is excluded from release builds.

The first public APK was built and signed locally. After the one-time signing setup, reviewed `vX.Y.Z-samsung.N` tags build and publish future signed APKs automatically for Obtainium. The retained certificate is checked before publication; manual workflow runs only verify and do not publish.

Routine CI tests and builds without uploading installers. Failure reports expire after one day, and releases upload their APK directly to GitHub Releases without duplicate Actions installer artifacts. See [release and signing instructions](docs/releasing.md).

## Credits

Based on [paulsnuff/BetterNightLight](https://github.com/paulsnuff/BetterNightLight). Samsung support, three-phase scheduling, solar offsets, the dedicated tile and native manual-resume policies are maintained in this fork. See [LICENSE](LICENSE) for the GPL v3 terms.
