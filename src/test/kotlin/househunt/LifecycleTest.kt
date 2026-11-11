package househunt

import househunt.lifecycle.isHomePresent
import househunt.lifecycle.isNewHome
import househunt.lifecycle.isWithinGoneRetention
import househunt.lifecycle.updateGoneSince
import househunt.model.SourceName
import househunt.model.StoredListing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LifecycleTest {
    private val hour = 3600L
    private val day = 24 * hour

    private fun stored(source: SourceName, lastSeen: Long) =
        StoredListing(buildListing(source), lastSeen, null)

    @Test
    fun `a home is new for 48 hours unless it came from the baseline run`() {
        assertTrue(isNewHome(0, false, 47 * hour, 48))
        assertFalse(isNewHome(0, false, 48 * hour, 48))
        assertFalse(isNewHome(0, true, hour, 48))
    }

    @Test
    fun `a home missing from a successful source is gone`() {
        val listing = stored(SourceName.FUNDA, lastSeen = 100)
        assertFalse(isHomePresent(listOf(listing), mapOf(SourceName.FUNDA to 200)))
    }

    @Test
    fun `a failed source never makes homes disappear`() {
        // Funda last succeeded at 100 and saw the listing then; later runs failed, so nothing changed.
        val listing = stored(SourceName.FUNDA, lastSeen = 100)
        assertTrue(isHomePresent(listOf(listing), mapOf(SourceName.FUNDA to 100)))
    }

    @Test
    fun `a home still listed by one source is present`() {
        val gone = stored(SourceName.FUNDA, lastSeen = 100)
        val still = stored(SourceName.BUURTJE, lastSeen = 200)
        val latest = mapOf(SourceName.FUNDA to 200L, SourceName.BUURTJE to 200L)
        assertTrue(isHomePresent(listOf(gone, still), latest))
    }

    @Test
    fun `gone time is set once and cleared when the home reappears`() {
        assertEquals(500, updateGoneSince(null, present = false, nowEpochSeconds = 500))
        assertEquals(500, updateGoneSince(500, present = false, nowEpochSeconds = 900))
        assertNull(updateGoneSince(500, present = true, nowEpochSeconds = 900))
    }

    @Test
    fun `gone homes are shown for 7 days and then removed`() {
        assertTrue(isWithinGoneRetention(null, 10 * day, 7))
        assertTrue(isWithinGoneRetention(0, 7 * day, 7))
        assertFalse(isWithinGoneRetention(0, 7 * day + 1, 7))
    }
}
