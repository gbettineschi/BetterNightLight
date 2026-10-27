# Changelog

## 1.0.4-samsung.3 — 2026-10-27

First public Samsung release, incorporating the earlier Samsung preview builds. The APK is identical to the signed optimized build already installed and tested on the Galaxy S23.

- Control Samsung’s native Eye comfort shield through a typed Shizuku shell helper, without root or an overlay.
- Set Day, Sunset and Night warmth targets with separate 0–240 minute fades. The native Custom-mode control exposes eleven requested levels, 0–10.
- Use clock times or saved-location sunrise/sunset, with independent −240…+240 minute solar offsets and exact-minute entry.
- Add the app’s own Quick Settings tile to enable, pause and resume automation; long press opens the app.
- Respect native Samsung slider, toggle and mode changes. Resume at the next scheduled transition, after one hour from the latest change, or only on manual Resume.
- Show Samsung-specific support, live Shizuku and filter status in About.
- Save and restore the original Samsung configuration, retry incomplete restoration, and keep device-specific ownership out of portable backups.

Validation: 87 JVM tests passed, with debug/release builds and both lints. Physical S23 checks covered native control, a background fade, the actual tile and slider, manual overrides, real next-transition expiry and restoration. The one-hour boundary was checked with an injected clock and an accelerated phone-test timestamp, not a real one-hour wait.

Shizuku must remain running and be restarted after reboot. Full reboot recovery, overnight behavior and other Samsung/One UI versions remain unverified. See the [validation record](docs/samsung-validation.md).
