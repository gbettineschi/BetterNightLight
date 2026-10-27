# Samsung Manual Control Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans inline; one fresh final reviewer. Steps use checkboxes.

**Goal:** Preserve native manual changes and resume automation by the user's selected policy.
**Architecture:** The existing coordinator detects differences from successful writes; a device-only override holds native state until its policy expires. Shared target events compute next transition.
**Tech Stack:** Existing Kotlin/Compose, DataStore, Shizuku, JUnit; no dependencies.
**Spec:** ../specs/2026-10-27-samsung-manual-control-design.md

## Global Constraints

- Three policies: NEXT_CHANGE (default), ONE_HOUR, MANUAL.
- Native adjustments remain free during an override; explicit app Pause remains filter-off.
- Portable preference only; override and original snapshot never backed up/transferred.
- Preserve native ownership, failure recovery, existing offsets and user profile.

## Review Focus

- Own writes and partially failed automatic-off must not masquerade as manual native changes.
- Midnight/DST, clipped fades, offsets and phase collisions must yield the next transition start, not the next fade step.
- Native edits at expiry or during override renew control predictably; process restart preserves deadlines.
- Profile disable/pause/resume and policy/schedule changes during slow reads/writes must not lose original settings or re-enable stale state.
- Backup imports discard override ownership; tile/Home/About must agree about paused vs automatic control.

### Task 1: Transition events and persisted manual-control contracts

**Files:** SamsungManualControl.kt, SamsungSchedule.kt, SamsungTargets.kt, SamsungProfile.kt, SamsungProfileRepository.kt and tests.
**Interfaces:** SamsungManualResumePolicy enum; SamsungManualOverride(policy, changedAt:Instant, resumeAt:Instant?, native:SamsungNativeState). Profile owns manualResumePolicy, manualOverride and derived isPaused. nextScheduledTransition(now, targets):Instant? and nextSamsungTransition(profile, now, sunTimes):Instant?.
- [ ] Write failing transition tests: next fade start, mid-fade skips its steps/end, instant/equal-level anchor, clipped overlap, midnight/DST/solar offsets.
- [ ] Add real DataStore roundtrip/portable preference/no-ownership import tests and native-difference tests; observe RED, implement and run full suite GREEN. Commit.

### Task 2: Native-change preservation and timed resume

**Files:** SamsungAutomation.kt, SamsungModule.kt, SamsungAutomationService.kt, coordinator tests/debug fixture.
**Interfaces:** Inject Clock and next-transition resolver into existing coordinator; existing trailing targetLevel lambda remains compatible. Native observer requests fresh reconciliation.
- [ ] Tests first: slider/off/on preserve native state, new edits renew hour, restart before/after expiry, next-transition expiry, policy change, native change at expiry, invalid schedule, automatic writes, disable/restore and concurrent pause/resume guards.
- [ ] Implement manual hold before normal scheduling validation, atomic guarded expiry/resume, original-snapshot preservation and no-backup runtime state. Full suite GREEN; commit.

### Task 3: Choices, status and verified delivery

**Files:** SamsungHomeScreen/ViewModel, SamsungTileAction/Presentation, SamsungAboutScreen, build version, README, debug probe and validation docs.
- [ ] Add three radio choices and manual-control/resume-time status; Resume button/tile clears overrides. Test tile status/actions before implementation.
- [ ] Full JVM suite, debug/release builds and lint; fresh final read-only review of this request delta, one material fix pass with RED→GREEN regressions.
- [ ] Physical S23 checks for native toggle and slider freedom, radio persistence, Resume, aged-timestamp timer recovery if feasible, and exact restoration of the owner's profile/settings. Install final signed APK, update existing draft PR, record limits honestly.
