package io.github.paulsnuff.betternightlight.samsung

import java.time.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SamsungManualAutomationTest {
    private val original = listOf(10, 0, 1, 0, 0)
    private val start = Instant.parse("2026-10-27T10:00:00Z")
    private class Time(var at: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = at
        fun advance(seconds: Long) { at = at.plusSeconds(seconds) }
    }
    private class Store(initial: SamsungProfile) : SamsungProfileStore {
        override val profiles = MutableStateFlow(initial)
        override suspend fun update(transform: (SamsungProfile) -> SamsungProfile) = transform(profiles.value).also { profiles.value = it }
    }
    private class Native(var state: SamsungNativeState) : SamsungControl {
        var writes = 0
        var failApply = false
        var onRead: (suspend () -> Unit)? = null
        override suspend fun readState(): SamsungNativeState {
            val captured = state; val action = onRead; onRead = null; action?.invoke(); return captured
        }
        override suspend fun snapshot() = listOf(state.level, if (state.customMode) 0 else 1, 1, 0, if (state.active) 1 else 0)
        override suspend fun applyLevel(level: Int) {
            state = SamsungNativeState(level > 0, level, true); writes++
            if (failApply) error("Refresh failed after native settings were written")
        }
        override suspend fun pause() { state = state.copy(active = false); writes++ }
        override suspend fun restore(original: List<Int>) {
            state = SamsungNativeState(original[4] == 1, original[0], original[1] == 0 && original[2] == 1 && original[3] == 0); writes++
        }
    }
    private fun profile(policy: SamsungManualResumePolicy) = SamsungProfile(enabled = true, manualResumePolicy = policy, originalSettings = original, lastAppliedLevel = 3)
    private fun runner(store: Store, native: Native, clock: Clock, next: () -> Instant? = { start.plusSeconds(7200) }) =
        SamsungAutomation(store, native, clock, { _, _ -> next() }) { 3 }

    @Test fun manualSliderIsHeldWithoutTurningTheFilterOff() = runBlocking {
        val store = Store(profile(SamsungManualResumePolicy.MANUAL)); val native = Native(SamsungNativeState(true, 7, true))
        runner(store, native, Time(start)).reconcile()
        assertEquals(SamsungNativeState(true, 7, true), native.state); assertEquals(0, native.writes)
        assertNotNull(store.profiles.value.manualOverride); assertNull(store.profiles.value.manualOverride!!.resumeAt)
        assertEquals(original, store.profiles.value.originalSettings)
    }
    @Test fun manualOffAndSubsequentOnRemainUnderNativeControl() = runBlocking {
        val store = Store(profile(SamsungManualResumePolicy.MANUAL)); val native = Native(SamsungNativeState(false, 3, true))
        val time = Time(start); val automation = runner(store, native, time)
        automation.reconcile(); assertNotNull(store.profiles.value.manualOverride)
        native.state = SamsungNativeState(true, 8, true); time.advance(10); automation.reconcile()
        assertEquals(SamsungNativeState(true, 8, true), native.state); assertEquals(0, native.writes)
    }
    @Test fun manualOnWhenTheScheduledLevelIsZeroIsAlsoHeld() = runBlocking {
        val store = Store(profile(SamsungManualResumePolicy.MANUAL).copy(lastAppliedLevel = 0))
        val native = Native(SamsungNativeState(true, 5, true))
        SamsungAutomation(store, native, Time(start)) { 0 }.reconcile()
        assertTrue(native.state.active); assertEquals(5, native.state.level); assertNotNull(store.profiles.value.manualOverride)
    }
    @Test fun oneHourExpirySurvivesANewCoordinatorAndResumesCurrentSchedule() = runBlocking {
        val store = Store(profile(SamsungManualResumePolicy.ONE_HOUR)); val native = Native(SamsungNativeState(true, 7, true)); val time = Time(start)
        runner(store, native, time).reconcile()
        assertNotNull(store.profiles.value.manualOverride)
        assertEquals(Instant.parse("2026-10-27T11:00:00Z"), store.profiles.value.manualOverride!!.resumeAt)
        time.advance(3599); runner(store, native, time).reconcile(); assertEquals(7, native.state.level)
        time.advance(1); runner(store, native, time).reconcile()
        assertEquals(3, native.state.level); assertNull(store.profiles.value.manualOverride)
    }
    @Test fun aFurtherNativeEditRestartsTheHour() = runBlocking {
        val store = Store(profile(SamsungManualResumePolicy.ONE_HOUR)); val native = Native(SamsungNativeState(true, 7, true)); val time = Time(start)
        val automation = runner(store, native, time); automation.reconcile()
        time.advance(1800); native.state = native.state.copy(level = 8); automation.reconcile()
        assertNotNull(store.profiles.value.manualOverride)
        assertEquals(Instant.parse("2026-10-27T11:30:00Z"), store.profiles.value.manualOverride!!.resumeAt)
        time.advance(1801); automation.reconcile(); assertEquals(8, native.state.level)
        time.advance(1799); automation.reconcile(); assertEquals(3, native.state.level)
    }
    @Test fun aNativeEditAtExpiryWinsAndStartsANewHour() = runBlocking {
        val store = Store(profile(SamsungManualResumePolicy.ONE_HOUR)); val native = Native(SamsungNativeState(true, 7, true)); val time = Time(start)
        val automation = runner(store, native, time); automation.reconcile(); time.advance(3600)
        native.state = native.state.copy(level = 8); automation.reconcile()
        assertEquals(8, native.state.level); assertNotNull(store.profiles.value.manualOverride)
        assertEquals(Instant.parse("2026-10-27T12:00:00Z"), store.profiles.value.manualOverride!!.resumeAt)
    }
    @Test fun nextTransitionResumesAtItsDeadlineNotTheNextTick() = runBlocking {
        val store = Store(profile(SamsungManualResumePolicy.NEXT_CHANGE)); val native = Native(SamsungNativeState(true, 7, true)); val time = Time(start)
        val automation = runner(store, native, time); automation.reconcile(); time.advance(5); automation.reconcile()
        assertEquals(7, native.state.level)
        time.advance(7194); automation.reconcile(); assertEquals(7, native.state.level)
        time.advance(1); automation.reconcile(); assertEquals(3, native.state.level); assertNull(store.profiles.value.manualOverride)
    }
    @Test fun scheduleEditsRecalculateNextTransitionWithoutDiscardingTheHold() = runBlocking {
        val store = Store(profile(SamsungManualResumePolicy.NEXT_CHANGE)); val native = Native(SamsungNativeState(true, 7, true)); val time = Time(start)
        var next = start.plusSeconds(7200)
        val automation = runner(store, native, time) { next }; automation.reconcile()
        next = start.plusSeconds(10800); store.update { it.copy(night = it.night.copy(minute = 60)) }
        time.advance(7201); automation.reconcile()
        assertEquals(7, native.state.level); assertNotNull(store.profiles.value.manualOverride)
        assertEquals(Instant.parse("2026-10-27T13:00:00Z"), store.profiles.value.manualOverride!!.resumeAt)
    }
    @Test fun selectingANewPolicyWhileHeldAppliesItFromSelectionTime() = runBlocking {
        val store = Store(profile(SamsungManualResumePolicy.MANUAL)); val native = Native(SamsungNativeState(true, 7, true)); val time = Time(start)
        val automation = runner(store, native, time); automation.reconcile(); time.advance(900)
        store.update { it.copy(manualResumePolicy = SamsungManualResumePolicy.ONE_HOUR) }; automation.reconcile()
        assertNotNull(store.profiles.value.manualOverride)
        assertEquals(Instant.parse("2026-10-27T11:15:00Z"), store.profiles.value.manualOverride!!.resumeAt)
        assertEquals(7, native.state.level)
    }
    @Test fun disablingDuringManualControlRestoresAndClearsOwnedState() = runBlocking {
        val store = Store(profile(SamsungManualResumePolicy.MANUAL)); val native = Native(SamsungNativeState(true, 7, true)); val time = Time(start)
        val automation = runner(store, native, time); automation.reconcile(); store.update { it.copy(enabled = false) }; automation.reconcile()
        assertEquals(SamsungNativeState(false, 10, true), native.state)
        assertNull(store.profiles.value.manualOverride); assertNull(store.profiles.value.originalSettings)
    }
    @Test fun ownScheduledWritesAndPartialFailuresNeverStartAManualOverride() = runBlocking {
        val store = Store(profile(SamsungManualResumePolicy.ONE_HOUR)); val native = Native(SamsungNativeState(true, 3, true)); val time = Time(start)
        native.failApply = true
        val automation = SamsungAutomation(store, native, time) { 0 }; automation.reconcile()
        assertTrue(automation.status.value.error); assertNull(store.profiles.value.manualOverride)
        native.failApply = false; automation.reconcile()
        assertNull(store.profiles.value.manualOverride); assertFalse(native.state.active)
    }
    @Test fun invalidScheduleDoesNotUndoAnExternalManualSetting() = runBlocking {
        val store = Store(profile(SamsungManualResumePolicy.MANUAL).copy(useSunTimes = true)); val native = Native(SamsungNativeState(true, 7, true))
        runner(store, native, Time(start)).reconcile()
        assertNotNull(store.profiles.value.manualOverride); assertEquals(7, native.state.level); assertEquals(0, native.writes)
    }
    @Test fun explicitPauseDuringASlowReadDoesNotResurrectAnOldOverride() = runBlocking {
        val override = SamsungManualOverride(SamsungManualResumePolicy.ONE_HOUR, start, start.plusSeconds(3600), SamsungNativeState(true, 7, true))
        val store = Store(profile(SamsungManualResumePolicy.ONE_HOUR).copy(manualOverride = override)); val native = Native(override.native)
        native.onRead = { store.update { it.copy(paused = true, manualOverride = null) } }
        runner(store, native, Time(start.plusSeconds(60))).reconcile()
        assertTrue(store.profiles.value.paused); assertNull(store.profiles.value.manualOverride); assertFalse(native.state.active)
    }
}
