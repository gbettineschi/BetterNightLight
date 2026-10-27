# Changes from Better Night Light

Forked from [paulsnuff/BetterNightLight 1.0.4](https://github.com/paulsnuff/BetterNightLight/tree/v1.0.4) (`b636929`, 30 September 2026). The original app targets Android’s AOSP Night Light. Samsung’s Eye comfort shield uses a different native service; the one-time AOSP permission was insufficient on the tested S23.

The personal history adds:

- **Samsung control through Shizuku:** a typed shell helper reads/writes the native Custom-mode filter without root or an overlay. Eleven requested warmth levels are available; the app does not claim calibrated Kelvin or finer native steps.
- **Day / Sunset / Night scheduling:** independent targets and fades, saved-location solar times, independent sunrise/sunset offsets, and defined midnight/DST/collision behavior.
- **Safe ownership and recovery:** save the original Samsung configuration before takeover, restore it when automation stops, retry incomplete restoration, and keep device-only snapshots out of portable backups.
- **A dedicated Quick Settings tile:** enable, pause/resume and open the app, with status reflecting actual automation/helper state.
- **Samsung-aware About:** show native support, Shizuku connectivity and live filter state rather than the unrelated AOSP permission check.
- **Respect for native manual changes:** pause automation after Samsung slider/toggle/mode edits, then resume at the next scheduled transition, after one hour from the latest change, or only on explicit Resume.

These changes were developed in 20 non-merge commits after the fork point, with three rounds of documented S23 acceptance. [Validation and limits](samsung-validation.md) distinguish actual phone observations from JVM/clock-fixture checks. Overnight use, full reboot recovery and other Samsung/One UI versions remain unverified. My changes are personal, 100% vibe-coded and provided as is.

Upstream code and licence attribution are retained. To update this fork, fetch/review upstream changes, merge only the wanted changes, rerun checks and publish a new personal version; upstream updates are not merged automatically.
