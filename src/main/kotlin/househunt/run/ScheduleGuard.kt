package househunt.run

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class RunSlot(val date: LocalDate, val hour: Int) {
    val label: String get() = "%02d:00".format(hour)
}

/**
 * Returns the slot to run now, or null when the trigger should be skipped.
 * A trigger is valid from the slot hour until [ScheduleConfiguration.windowHours] later,
 * and only when that slot has not run today yet.
 */
fun determineSlotToRun(
    now: Instant,
    schedule: ScheduleConfiguration,
    alreadyRunSlots: Set<RunSlot>,
): RunSlot? {
    val localNow = now.atZone(ZoneId.of(schedule.timeZone))
    val localHour = localNow.hour
    val matchingHour = schedule.slotHours.firstOrNull { slotHour ->
        localHour >= slotHour && localHour < slotHour + schedule.windowHours
    } ?: return null
    val slot = RunSlot(localNow.toLocalDate(), matchingHour)
    return if (slot in alreadyRunSlots) null else slot
}
