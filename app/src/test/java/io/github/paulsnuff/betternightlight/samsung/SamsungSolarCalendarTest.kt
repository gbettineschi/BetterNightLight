package io.github.paulsnuff.betternightlight.samsung

import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class SamsungSolarCalendarTest {
    @Test fun midnightRefreshesThePreviewWithoutAResumeCycle() = runBlocking {
        var instant = Instant.parse("2026-10-27T22:59:59Z")
        val changes = MutableSharedFlow<Unit>()
        val values = mutableListOf<SamsungSolarCalendar>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            samsungSolarCalendar(changes, { instant }, { ZoneId.of("Europe/Zurich") }).take(2).toList(values)
        }
        instant = Instant.parse("2026-10-27T23:00:01Z")
        changes.emit(Unit)
        job.join()
        assertEquals(listOf(LocalDate.of(2026, 10, 27), LocalDate.of(2026, 10, 28)), values.map { it.date })
    }
    @Test fun aTimezoneEventResolvesDateAndZoneAgainWhileStillVisible() = runBlocking {
        var zone = ZoneId.of("Europe/Zurich")
        val changes = MutableSharedFlow<Unit>()
        val values = mutableListOf<SamsungSolarCalendar>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            samsungSolarCalendar(changes, { Instant.parse("2026-10-27T23:30:00Z") }, { zone }).take(2).toList(values)
        }
        zone = ZoneOffset.UTC
        changes.emit(Unit)
        job.join()
        assertEquals(listOf(LocalDate.of(2026, 10, 28), LocalDate.of(2026, 10, 27)), values.map { it.date })
        assertEquals(ZoneOffset.UTC, values.last().zone)
    }
}
