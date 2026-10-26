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
    private fun withStore(test: suspend (SamsungProfileRepository, androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>) -> Unit) = runBlocking {
        val folder = Files.createTempDirectory("bnl-samsung-test").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val data = PreferenceDataStoreFactory.create(scope = scope, produceFile = { folder.resolve("samsung.preferences_pb") })
        try { test(SamsungProfileRepository(data), data) }
        finally { scope.coroutineContext[Job]!!.cancelAndJoin(); folder.deleteRecursively() }
    }
    @Test fun profileAndRestoreSnapshotSurviveANewRepositoryInstance() = withStore { repo, data ->
        val expected = SamsungProfile(enabled = true, paused = true, day = SamsungPhase(2, 480, 30), night = SamsungPhase(9, 60, 120), useSunTimes = true, latitude = 46.5, longitude = 6.6, originalSettings = listOf(4, 1, 0, 2, 1), lastAppliedLevel = 7)
        repo.update { expected }
        assertEquals(expected, SamsungProfileRepository(data).profiles.first())
    }
    @Test fun concurrentEditsKeepBothFieldsInsteadOfWritingStaleCopies() = withStore { repo, _ ->
        coroutineScope {
            repeat(20) {
                launch(Dispatchers.Default) { repo.update { it.copy(day = it.day.copy(fadeMinutes = it.day.fadeMinutes + 1)) } }
                launch(Dispatchers.Default) { repo.update { it.copy(night = it.night.copy(fadeMinutes = it.night.fadeMinutes + 1)) } }
            }
        }
        val profile = repo.profiles.first()
        assertEquals(80, profile.day.fadeMinutes); assertEquals(110, profile.night.fadeMinutes)
    }
    @Test fun aMalformedStoredSnapshotIsKeptForTheControllerToReject() = withStore { repo, data ->
        data.edit { it[stringPreferencesKey("samsung_original_settings")] = "broken,0,1,0,0" }
        val snapshot = repo.profiles.first().originalSettings
        assertNotNull(snapshot)
        assertThrows(IllegalArgumentException::class.java) { SamsungSettings.restoreWrites(snapshot!!) }
    }
}
