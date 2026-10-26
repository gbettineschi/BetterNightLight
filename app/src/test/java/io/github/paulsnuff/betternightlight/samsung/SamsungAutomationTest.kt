package io.github.paulsnuff.betternightlight.samsung

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SamsungAutomationTest {
    private class Store(initial: SamsungProfile) : SamsungProfileStore {
        override val profiles = MutableStateFlow(initial)
        override suspend fun update(transform: (SamsungProfile) -> SamsungProfile): SamsungProfile = transform(profiles.value).also { profiles.value = it }
    }
    private class Control(initial: List<Int> = listOf(10, 0, 1, 0, 0)) : SamsungControl {
        var values = initial
        val history = mutableListOf<List<Int>>()
        var failApply = false
        var failRestore = false
        var onSnapshot: (suspend () -> Unit)? = null
        var onApply: (suspend () -> Unit)? = null
        var onRestore: (suspend () -> Unit)? = null
        override suspend fun readState() = SamsungNativeState(values[4] == 1, values[0], values[1] == 0 && values[2] == 1 && values[3] == 0)
        override suspend fun snapshot(): List<Int> { val saved = values.toList(); onSnapshot?.invoke(); return saved }
        override suspend fun applyLevel(level: Int) {
            values = SamsungSettings.writesForLevel(level).map { it.second }; history += values
            if (failApply) throw IllegalStateException("Helper stopped after a partial write")
            onApply?.invoke()
        }
        override suspend fun pause() { values = values.toMutableList().also { it[4] = 0 }; history += values }
        override suspend fun restore(original: List<Int>) {
            if (failRestore) throw IllegalStateException("Helper unavailable")
            values = SamsungSettings.restoreWrites(original).map { it.second }; history += values
            onRestore?.invoke()
        }
    }
    @Test fun capturesTheOriginalSettingsBeforeTakingControl() = runBlocking {
        val store = Store(SamsungProfile(enabled = true)); val control = Control()
        SamsungAutomation(store, control) { 6 }.reconcile()
        assertEquals(listOf(10, 0, 1, 0, 0), store.profiles.value.originalSettings)
        assertEquals(6, store.profiles.value.lastAppliedLevel)
        assertEquals(listOf(6, 0, 1, 0, 1), control.values)
    }
    @Test fun manualNativeOffBecomesAPersistentPause() = runBlocking {
        val original = listOf(10, 0, 1, 0, 0)
        val store = Store(SamsungProfile(enabled = true, originalSettings = original, lastAppliedLevel = 6))
        val control = Control(listOf(6, 0, 1, 0, 0))
        SamsungAutomation(store, control) { 8 }.reconcile()
        assertTrue(store.profiles.value.paused)
        assertEquals(0, control.values[4])
        assertEquals(original, store.profiles.value.originalSettings)
    }
    @Test fun persistentPauseIsAppliedByANewCoordinator() = runBlocking {
        val original = listOf(10, 0, 1, 0, 0)
        val store = Store(SamsungProfile(enabled = true, paused = true, originalSettings = original, lastAppliedLevel = 6))
        val control = Control(listOf(6, 0, 1, 0, 1))
        SamsungAutomation(store, control) { 8 }.reconcile()
        assertEquals(0, control.values[4]); assertTrue(store.profiles.value.paused)
        assertEquals(original, store.profiles.value.originalSettings)
    }
    @Test fun disablingRestoresTheExactAdaptiveConfiguration() = runBlocking {
        val original = listOf(4, 1, 0, 2, 1)
        val store = Store(SamsungProfile(enabled = false, originalSettings = original, lastAppliedLevel = 7))
        val control = Control(listOf(7, 0, 1, 0, 1))
        SamsungAutomation(store, control) { 9 }.reconcile()
        assertEquals(original, control.values); assertNull(store.profiles.value.originalSettings)
        assertFalse(store.profiles.value.enabled); assertNull(store.profiles.value.lastAppliedLevel)
    }
    @Test fun aPartialFailureRetainsEnoughInformationToRestore() = runBlocking {
        val store = Store(SamsungProfile(enabled = true)); val control = Control().apply { failApply = true }
        val automation = SamsungAutomation(store, control) { 6 }
        automation.reconcile()
        assertTrue(automation.status.value.error)
        assertEquals(listOf(10, 0, 1, 0, 0), store.profiles.value.originalSettings)
        store.update { it.copy(enabled = false) }; automation.reconcile()
        assertEquals(listOf(10, 0, 1, 0, 0), control.values)
        assertNull(store.profiles.value.originalSettings)
    }
    @Test fun failedRestoreRemainsPendingUntilTheHelperReturns() = runBlocking {
        val original = listOf(10, 0, 1, 0, 0)
        val store = Store(SamsungProfile(originalSettings = original)); val control = Control(listOf(6, 0, 1, 0, 1)).apply { failRestore = true }
        val automation = SamsungAutomation(store, control) { 6 }; automation.reconcile()
        assertTrue(automation.status.value.error); assertEquals(original, store.profiles.value.originalSettings)
        control.failRestore = false; automation.reconcile()
        assertEquals(original, control.values); assertNull(store.profiles.value.originalSettings)
    }
    @Test fun disablingDuringSnapshotCannotActivateAStaleSchedule() = runBlocking {
        val store = Store(SamsungProfile(enabled = true)); val control = Control()
        control.onSnapshot = { store.update { it.copy(enabled = false) } }
        SamsungAutomation(store, control) { 6 }.reconcile()
        assertFalse(store.profiles.value.enabled); assertNull(store.profiles.value.originalSettings)
        assertTrue(control.history.none { it[4] == 1 })
    }
    @Test fun disablingDuringAWriteRestoresBeforeReconcileReturns() = runBlocking {
        val store = Store(SamsungProfile(enabled = true)); val control = Control()
        control.onApply = { store.update { it.copy(enabled = false) } }
        SamsungAutomation(store, control) { 6 }.reconcile()
        assertFalse(store.profiles.value.enabled); assertNull(store.profiles.value.originalSettings)
        assertEquals(listOf(10, 0, 1, 0, 0), control.values)
    }
    @Test fun corruptSnapshotCannotBeDiscardedOrOverwritten() = runBlocking {
        val store = Store(SamsungProfile(enabled = true, originalSettings = listOf(9), lastAppliedLevel = 6))
        val control = Control(listOf(6, 0, 1, 0, 1)); val automation = SamsungAutomation(store, control) { 8 }
        automation.reconcile()
        assertTrue(automation.status.value.error); assertEquals(listOf(9), store.profiles.value.originalSettings)
        assertTrue(control.history.isEmpty())
    }
    @Test fun reenableAndPauseDuringRestoreRetainsTheLatestPause() = runBlocking {
        val original = listOf(10, 0, 1, 0, 0)
        val store = Store(SamsungProfile(originalSettings = original))
        val control = Control(listOf(6, 0, 1, 0, 1))
        control.onRestore = { store.update { it.copy(enabled = true, paused = true) } }
        SamsungAutomation(store, control) { 8 }.reconcile()
        assertTrue(store.profiles.value.enabled)
        assertTrue(store.profiles.value.paused)
        assertEquals(0, control.values[4])
        assertTrue(control.history.none { it[0] == 8 && it[4] == 1 })
    }
    @Test fun invalidScheduleCannotPreventExplicitPause() = runBlocking {
        val original = listOf(10, 0, 1, 0, 0)
        val store = Store(SamsungProfile(enabled = true, paused = true, useSunTimes = true, originalSettings = original, lastAppliedLevel = 6))
        val control = Control(listOf(6, 0, 1, 0, 1))
        SamsungAutomation(store, control) { error("Invalid schedule must not be evaluated") }.reconcile()
        assertEquals(0, control.values[4])
        assertTrue(store.profiles.value.paused)
        assertEquals(original, store.profiles.value.originalSettings)
    }
    @Test fun failedAutomaticOffCanRecoverAndRunTheNextEvening() = runBlocking {
        val original = listOf(10, 0, 1, 0, 0)
        val store = Store(SamsungProfile(enabled = true, originalSettings = original, lastAppliedLevel = 6))
        val control = Control(listOf(6, 0, 1, 0, 1)).apply { failApply = true }
        var target = 0
        val automation = SamsungAutomation(store, control) { target }
        automation.reconcile()
        assertTrue(automation.status.value.error)
        assertEquals(0, control.values[4]) // Native write succeeded; refresh failed.
        control.failApply = false
        val restarted = SamsungAutomation(store, control) { target }
        restarted.reconcile()
        assertFalse(store.profiles.value.paused)
        target = 8
        restarted.reconcile()
        assertFalse(store.profiles.value.paused)
        assertEquals(listOf(8, 0, 1, 0, 1), control.values)
    }
}
