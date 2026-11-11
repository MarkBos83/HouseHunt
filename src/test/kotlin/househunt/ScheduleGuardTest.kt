package househunt

import househunt.run.RunSlot
import househunt.run.ScheduleConfiguration
import househunt.run.determineSlotToRun
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScheduleGuardTest {
    private val schedule = ScheduleConfiguration("Europe/Amsterdam", listOf(8, 15, 20), 2)

    private fun decide(utc: String, alreadyRun: Set<RunSlot> = emptySet()) =
        determineSlotToRun(Instant.parse(utc), schedule, alreadyRun)

    @Test
    fun `winter trigger at 07 UTC is the 08 slot and the 06 UTC trigger is skipped`() {
        assertEquals(RunSlot(LocalDate.of(2026, 1, 15), 8), decide("2026-01-15T07:00:00Z"))
        assertNull(decide("2026-01-15T06:00:00Z"))
    }

    @Test
    fun `summer trigger at 06 UTC is the 08 slot and the 07 UTC trigger finds the slot already run`() {
        val first = decide("2026-07-15T06:00:00Z")!!
        assertEquals(RunSlot(LocalDate.of(2026, 7, 15), 8), first)
        assertEquals(first, decide("2026-07-15T07:00:00Z"))
        assertNull(decide("2026-07-15T07:00:00Z", alreadyRun = setOf(first)))
    }

    @Test
    fun `all three slots work in summer and winter`() {
        listOf("2026-01-15T14:00:00Z" to 15, "2026-01-15T19:00:00Z" to 20, "2026-07-15T13:00:00Z" to 15, "2026-07-15T18:00:00Z" to 20)
            .forEach { (utc, hour) -> assertEquals(hour, decide(utc)?.hour, utc) }
    }

    @Test
    fun `a late start up to two hours after the slot still runs but three hours is too late`() {
        assertEquals(15, decide("2026-01-15T15:30:00Z")?.hour)
        assertNull(decide("2026-01-15T17:00:00Z"))
    }

    @Test
    fun `a double trigger does not run the same slot twice`() {
        val slot = RunSlot(LocalDate.of(2026, 1, 15), 8)
        assertNull(decide("2026-01-15T07:20:00Z", alreadyRun = setOf(slot)))
    }

    @Test
    fun `clock change days follow local time`() {
        // 2026-03-29 is the spring-forward day: 06:00 UTC is already 08:00 in Amsterdam.
        assertEquals(8, decide("2026-03-29T06:00:00Z")?.hour)
        assertNull(decide("2026-03-29T05:00:00Z"))
    }
}
