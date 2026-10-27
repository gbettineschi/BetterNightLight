# Samsung About and Solar Offsets Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans inline, with one fresh final review. Steps use checkbox syntax.

**Goal:** Correct Samsung About diagnostics and add simple sunrise/sunset minute offsets.
**Architecture:** Samsung About consumes the existing native/bridge state; portable profile fields feed the existing solar resolver and UI.
**Tech Stack:** Existing Kotlin, Compose, DataStore, JUnit; no dependencies.
**Spec:** ../specs/2026-10-27-samsung-about-offsets-design.md

## Global Constraints

- Signed offsets −240…+240 minutes, default 0; manual fallback unchanged.
- Preserve device-local ownership and existing profile fields; no permission or root changes.
- About must reflect real Samsung/helper/native state and preserve AOSP behavior.

## Review Focus

- Offsets crossing midnight or DST use solar instants and preserve deterministic phase collision priority.
- Missing solar events use unshifted manual anchors.
- About never conflates inactive filter with unsupported backend or grants green helper status when disconnected.
- Legacy and transferred profiles retain/default offsets without restoring device ownership.
- Time previews and actual resolved anchors must agree after offset or timezone changes.

### Task 1: Samsung About diagnostics

**Files:** AboutScreen.kt; new samsung/SamsungAboutPresentation.kt and tests.
**Interface:** samsungAboutPresentation(supported, connection, native) gives supported/connected/native/warmth display state; Samsung route uses existing SamsungHomeViewModel, AOSP route retains AboutViewModel.
- [x] Reproduce old AOSP-only checks; add failing tests for supported+connected, disconnected helper, inactive vs unsupported and invalid native strength.
- [x] Implement genuine Samsung presentation and route, fork link/upstream credit; run testDebugUnitTest and commit.

### Task 2: Solar offsets and delivery

**Files:** SamsungProfile.kt, SamsungProfileRepository.kt, SamsungTargets.kt, SamsungHomeViewModel.kt, SamsungHomeScreen.kt; matching tests; app/build.gradle.kts; README.
**Interface:** sunriseOffsetMinutes and sunsetOffsetMinutes default 0; samsungSolarTimes applies offsets only to non-null solar instants. Resolver and previews consume it.
- [x] Add profile fields/contracts and failing tests for signed offsets/fade timing, midnight/DST collisions, missing-event fallback, bounds and DataStore/portable persistence/defaults.
- [x] Implement persistence, shared solar transformation, preview and signed dialogs; run full suite, debug/release builds and lint. Commit.
- [x] Fresh final source review over this request's branch delta; one material-fix pass with regression tests.
- [x] Install the signed update, check About and offset round trips on S23; restore test settings, copy APK and update existing draft PR. Record any unavailable hardware checks explicitly.
