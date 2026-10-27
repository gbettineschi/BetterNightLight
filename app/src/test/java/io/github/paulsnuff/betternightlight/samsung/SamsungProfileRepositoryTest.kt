package io.github.paulsnuff.betternightlight.samsung

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class SamsungProfileRepositoryTest {
    private fun withStore(test: suspend (SamsungProfileRepository, androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>, androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>) -> Unit) = runBlocking {
        val folder = Files.createTempDirectory("bnl-samsung-test").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val data = PreferenceDataStoreFactory.create(scope = scope, produceFile = { folder.resolve("samsung.preferences_pb") })
        val runtime = PreferenceDataStoreFactory.create(scope = scope, produceFile = { folder.resolve("runtime.preferences_pb") })
        try { test(SamsungProfileRepository(data, runtime), data, runtime) }
        finally { scope.coroutineContext[Job]!!.cancelAndJoin(); folder.deleteRecursively() }
    }
    @Test fun profileAndRestoreSnapshotSurviveANewRepositoryInstance() = withStore { repo, data, runtime ->
        val expected = SamsungProfile(enabled = true, paused = true, day = SamsungPhase(2, 480, 30), night = SamsungPhase(9, 60, 120), useSunTimes = true, latitude = 46.5, longitude = 6.6, sunriseOffsetMinutes = -25, sunsetOffsetMinutes = 40, originalSettings = listOf(4, 1, 0, 2, 1), lastAppliedLevel = 7)
        repo.update { expected }
        assertEquals(expected, SamsungProfileRepository(data, runtime).profiles.first())
    }
    @Test fun oldPreferencesDefaultToUnshiftedSunEvents() = withStore { repo, _, _ ->
        val profile = repo.profiles.first()
        assertEquals(0, profile.sunriseOffsetMinutes); assertEquals(0, profile.sunsetOffsetMinutes)
    }
    @Test fun nativeOverrideAndChoiceSurviveRestartWithoutEnteringBackup() = withStore { repo, data, runtime ->
        val override = SamsungManualOverride(SamsungManualResumePolicy.ONE_HOUR, java.time.Instant.parse("2026-10-27T10:00:00Z"), java.time.Instant.parse("2026-10-27T11:00:00Z"), SamsungNativeState(true, 7, true))
        repo.update { it.copy(manualResumePolicy = SamsungManualResumePolicy.ONE_HOUR, manualOverride = override) }
        val restarted = SamsungProfileRepository(data, runtime).profiles.first()
        assertEquals(SamsungManualResumePolicy.ONE_HOUR, restarted.manualResumePolicy)
        assertEquals(override, restarted.manualOverride)
        assertTrue(restarted.isPaused)
        assertFalse(data.data.first().asMap().keys.any { it.name == "samsung_manual_override" })
    }
    @Test fun transferredPolicyCannotImportNativeOverrideOwnership() = withStore { _, data, runtime ->
        data.edit {
            it[stringPreferencesKey("samsung_manual_resume_policy")] = "ONE_HOUR"
            it[stringPreferencesKey("samsung_manual_override")] = "old-device-owned-state"
        }
        val imported = SamsungProfileRepository(data, runtime).profiles.first()
        assertEquals(SamsungManualResumePolicy.ONE_HOUR, imported.manualResumePolicy)
        assertNull(imported.manualOverride)
        assertFalse(imported.isPaused)
    }
    @Test fun concurrentEditsKeepBothFieldsInsteadOfWritingStaleCopies() = withStore { repo, _, _ ->
        coroutineScope {
            repeat(20) {
                launch(Dispatchers.Default) { repo.update { it.copy(day = it.day.copy(fadeMinutes = it.day.fadeMinutes + 1)) } }
                launch(Dispatchers.Default) { repo.update { it.copy(night = it.night.copy(fadeMinutes = it.night.fadeMinutes + 1)) } }
            }
        }
        val profile = repo.profiles.first()
        assertEquals(80, profile.day.fadeMinutes); assertEquals(110, profile.night.fadeMinutes)
    }
    @Test fun aMalformedStoredSnapshotIsKeptForTheControllerToReject() = withStore { repo, data, runtime ->
        repo.profiles.first()
        runtime.edit { it[stringPreferencesKey("samsung_original_settings")] = "broken,0,1,0,0" }
        val snapshot = repo.profiles.first().originalSettings
        assertNotNull(snapshot)
        assertThrows(IllegalArgumentException::class.java) { SamsungSettings.restoreWrites(snapshot!!) }
    }
    @Test fun backupPreferencesNeverContainDeviceOwnershipOrRestoreSnapshot() = withStore { repo, data, runtime ->
        repo.update { it.copy(enabled = true, paused = true, originalSettings = listOf(4, 1, 0, 2, 1), lastAppliedLevel = 6) }
        val names = data.data.first().asMap().keys.map { it.name }
        assertFalse(names.any { it in listOf("samsung_enabled", "samsung_paused", "samsung_original_settings", "samsung_last_level") })
    }
    @Test fun restoringLegacyBackupImportsTargetsButNeverNativeOwnership() = withStore { _, data, runtime ->
        data.edit {
            it[androidx.datastore.preferences.core.booleanPreferencesKey("samsung_enabled")] = true
            it[androidx.datastore.preferences.core.booleanPreferencesKey("samsung_paused")] = true
            it[stringPreferencesKey("samsung_original_settings")] = "4,1,0,2,1"
            it[androidx.datastore.preferences.core.intPreferencesKey("samsung_last_level")] = 7
            it[androidx.datastore.preferences.core.intPreferencesKey("samsung_day_fade")] = 42
            it[androidx.datastore.preferences.core.intPreferencesKey("samsung_sunrise_offset")] = -15
            it[androidx.datastore.preferences.core.intPreferencesKey("samsung_sunset_offset")] = 35
        }
        val restored = SamsungProfileRepository(data, runtime).profiles.first()
        assertEquals(42, restored.day.fadeMinutes)
        assertEquals(-15, restored.sunriseOffsetMinutes); assertEquals(35, restored.sunsetOffsetMinutes)
        assertFalse(restored.enabled); assertFalse(restored.paused)
        assertNull(restored.originalSettings); assertNull(restored.lastAppliedLevel)
    }
}
