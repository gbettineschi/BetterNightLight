# Samsung About and solar offsets

The user explicitly requested correcting About's old support crosses to check the fork's Samsung path, and simple signed sunrise/sunset minute offsets. This is a small extension of the already authorized Samsung fork; continue the established inline workflow.

- About uses actual Samsung service detection, live Shizuku connection and observed native filter state. Remove irrelevant AOSP support/secure-setting-grant checks from the Samsung route; keep them on the AOSP route. Never force an unavailable backend/helper to look healthy. Link source to this fork and retain upstream credit.
- Two independent signed minute settings, default 0, bounded −240…+240 (implementation choice consistent with the existing four-hour fade cap). Negative means earlier, positive later. Apply them to real solar instants, with previews matching execution. Manual fallback times remain unshifted. Persist and back up offsets as portable preferences; existing installations default to 0.
- Use two simple value buttons and signed numeric dialogs in the existing solar Timing section. Avoid adding dependencies or a second scheduling system.
- Preserve existing native settings, enabled/pause state, Shizuku access and tile. Deliver version 1.0.4-samsung.2, versionCode 1000005, same signing certificate. Verify the actual phone when available; restore any test changes.

Approach: branch About to a Samsung presentation using the existing live Samsung view-model state; add offsets to the current profile/repository/resolver. Modifying the AOSP reader to pretend Samsung is AOSP would misrepresent Kelvin/permissions, and globally shifting manual times would change existing schedules; neither fits this request. These routine choices are the implementer's, not additional user-approved requirements.
