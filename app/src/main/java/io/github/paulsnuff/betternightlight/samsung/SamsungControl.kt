package io.github.paulsnuff.betternightlight.samsung

import kotlinx.coroutines.flow.Flow

data class SamsungNativeState(val active: Boolean, val level: Int, val customMode: Boolean)

interface SamsungProfileStore {
    val profiles: Flow<SamsungProfile>
    suspend fun update(transform: (SamsungProfile) -> SamsungProfile): SamsungProfile
}

interface SamsungControl {
    suspend fun readState(): SamsungNativeState
    suspend fun snapshot(): List<Int>
    suspend fun applyLevel(level: Int)
    suspend fun pause()
    suspend fun restore(original: List<Int>)
}
