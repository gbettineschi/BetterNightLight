# Better Night Light: Samsung fork

Approved scope, 26 October 2026. The user authorized implementation and required a no-root feasibility check first.

## Purpose

Replace Twilight on the user's Samsung Galaxy S23 with a simple open-source app that controls Samsung's native Eye comfort shield. The user wants three adjustable warmth targets—day, sunset, and night—and control over transition speed. A dedicated Quick Settings toggle is essential.

## Approach

The user selected a Better Night Light fork. Reuse its Kotlin/Compose interface, scheduling service, solar calculations, and existing Android backend. Add a Samsung backend and extend the schedule. This keeps a small utility rather than starting a new app.

The alternatives discussed were an Automate flow, which already demonstrates Samsung-specific control but adds another app and flow configuration, and retaining Twilight, which remains the working fallback. A fork best fits the requested native controls and simple interface, subject to testing Samsung compatibility.

## Existing capability and required changes

Better Night Light currently provides a base temperature, an optional later boost temperature, and an optional off period. Its gradual mode interpolates from base to boost; it does not expose the requested three targets with independent fade durations. It uses standard Android Night Light settings and has no dedicated Quick Settings tile. Samsung support therefore requires both a new control path and changes to capability detection and state reading.

## Proposed behavior

- Three warmth sliders: **Day**, **Sunset**, and **Night**. Zero means no warming. On Samsung, label these as strength rather than claiming calibrated Kelvin values.
- Three transition-duration controls: night to day, day to sunset, and sunset to night. Zero gives an immediate change; longer durations give slower linear changes.
- Proposed timing: reach Day at sunrise, Sunset at sunset, and Night at a user-selected night time. Retain manual times as an alternative to sunrise/sunset. These timing choices are proposals, not requirements already supplied by the user.
- Each fade ends at its target time; before the fade, hold the preceding target. Limit a fade to the available time between targets, so overlapping fades cannot create jumps. Handle midnight as part of one continuous schedule.
- Opening the app must not be necessary for scheduled updates. After reboot or a missed update, calculate the correct current strength rather than replaying old steps.
- A dedicated Quick Settings tile pauses and resumes filtering. Pause turns the filter off and suspends automatic writes until the user resumes. Resume applies the current scheduled strength. Reflect the actual pause state in the tile and respect a manual native-filter switch-off.
- Preserve a snapshot of the prior native settings before taking control. Provide a restore action when stopping the app's automation.

## Samsung control and first validation

The phone exposes Samsung's `blue_light_filter` and `blue_light_filter_opacity` settings, while standard Android Night Light reports unavailable. The published Automate flow also refreshes Samsung's BlueLightFilterService. This supports the proposed approach, but reading those keys does not prove that writing them produces a visible, reliable change on this phone.

Before building the complete feature, test a small reversible sequence on the physical S23: record existing settings, apply a few spaced strength changes through the Samsung path, verify the visible effect and required permissions, and restore the original state. Establish whether Samsung's own scheduling/adaptive mode must be suspended. Do not bypass a support warning and assume success. No root or overlay is planned.

## Scope and acceptance

Keep the existing restrained interface. The initial fork needs the three targets, duration controls, Samsung support, and tile; freehand curves, extra dimming, and per-app rules are outside this version.

Verify schedule calculations across midnight, differing fade lengths, equal targets, and missed updates. Verify visible gradual changes, tile pause/resume, manual-off behavior, screen-off operation, and reboot recovery on the S23. A successful build or emulator run alone is insufficient evidence that it replaces Twilight reliably.

## Sources inspected

- [Better Night Light](https://github.com/paulsnuff/BetterNightLight), especially `AutomationSchedule.kt`, `TimelineUtils.kt`, `NightLightReader.kt`, and `NightLightController.kt`.
- [Samsung Automate flow 50175](https://llamalab.com/automate/community/flows/50175), including its downloaded flow definition, inspected without running it.
- Read-only checks on the connected Galaxy S23 in this chat.

## Device validation and implementation decisions

On the actual SM-S911B, Android 17 / One UI 9, ordinary ADB shell UID 2000 successfully changed strength 3 -> 7 -> 3. Samsung mDNIe logs acknowledged these values, and the user confirmed the visible changes. The original settings were restored and checked. A temporary target-37 normal app was rejected from writing the private setting even after WRITE_SECURE_SETTINGS was granted. Its direct mDNIe calls returned false; its explicit Samsung service refresh succeeded. Use a running Shizuku shell user service for the five native settings and refresh. Root is not required. The temporary probe was removed.

Implement Samsung-specific profile, controller, foreground service, and home screen alongside the existing AOSP implementation. Reuse the existing solar calculator and shared DataStore. Samsung targets are integer levels 0-10; show this limitation honestly. Stop and restore returns the five changed native settings to their original values. The own tile pauses/resumes without discarding that original snapshot. Shizuku loss must produce a visible waiting state and must never be reported as a successful write.
