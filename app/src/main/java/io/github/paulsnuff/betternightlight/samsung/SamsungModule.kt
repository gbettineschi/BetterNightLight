package io.github.paulsnuff.betternightlight.samsung

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Singleton
import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SamsungRuntime

@Module
@InstallIn(SingletonComponent::class)
abstract class SamsungBindings {
    @Binds abstract fun store(repository: SamsungProfileRepository): SamsungProfileStore
    @Binds abstract fun control(controller: ShizukuSamsungControl): SamsungControl
}

@Module
@InstallIn(SingletonComponent::class)
object SamsungModule {
    @Provides
    @Singleton
    @SamsungRuntime
    fun runtimeStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(produceFile = {
            // Android excludes this directory from both cloud backup and device transfer.
            context.noBackupFilesDir.resolve("samsung_runtime.preferences_pb")
        })

    @Provides
    @Singleton
    fun automation(store: SamsungProfileStore, control: SamsungControl, clock: Clock): SamsungAutomation =
        SamsungAutomation(store, control, clock = clock) { profile ->
            // Resolve the current system zone on each tick, including after a timezone change.
            resolveSamsungLevel(profile, ZonedDateTime.ofInstant(clock.instant(), ZoneId.systemDefault()))
        }
}
