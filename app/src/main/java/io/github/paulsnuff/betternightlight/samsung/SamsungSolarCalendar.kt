package io.github.paulsnuff.betternightlight.samsung

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

data class SamsungSolarCalendar(val date: LocalDate, val zone: ZoneId)

fun samsungSolarCalendar(
    changes: Flow<Unit>,
    now: () -> Instant = Instant::now,
    zone: () -> ZoneId = ZoneId::systemDefault,
): Flow<SamsungSolarCalendar> = changes.onStart { emit(Unit) }.map {
    val currentZone = zone()
    SamsungSolarCalendar(now().atZone(currentZone).toLocalDate(), currentZone)
}.distinctUntilChanged()
