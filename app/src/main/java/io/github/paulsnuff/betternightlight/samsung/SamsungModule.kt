package io.github.paulsnuff.betternightlight.samsung

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Singleton

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
    fun automation(store: SamsungProfileStore, control: SamsungControl, clock: Clock): SamsungAutomation =
        SamsungAutomation(store, control) { profile ->
            // Resolve the current system zone on each tick, including after a timezone change.
            resolveSamsungLevel(profile, ZonedDateTime.ofInstant(clock.instant(), ZoneId.systemDefault()))
        }
}
