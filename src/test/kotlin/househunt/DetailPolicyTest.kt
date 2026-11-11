package househunt

import househunt.model.EnergyLabel
import househunt.model.StoredListing
import househunt.run.isWorthDetailFetch
import househunt.run.shouldFetchDetails
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DetailPolicyTest {
    private val filters = buildFilters()

    @Test
    fun `a home that already fails a filter is not worth a detail request`() {
        assertFalse(isWorthDetailFetch(buildListing(price = 450_000), filters))
        assertFalse(isWorthDetailFetch(buildListing(area = 70), filters))
        assertFalse(isWorthDetailFetch(buildListing(bedrooms = 1), filters))
        assertFalse(isWorthDetailFetch(buildListing(label = EnergyLabel.C), filters))
    }

    @Test
    fun `unknown values and passing homes are worth a detail request`() {
        assertTrue(isWorthDetailFetch(buildListing(), filters))
        assertTrue(isWorthDetailFetch(buildListing(bedrooms = null, label = null), filters))
    }

    @Test
    fun `details are fetched for new listings, changed price or status, and the daily refresh`() {
        val listing = buildListing()
        val stored = StoredListing(listing, lastSeenEpochSeconds = 1, lastDetailFetchEpochSeconds = 1)

        assertTrue(shouldFetchDetails(null, listing, refreshAllDetails = false))
        assertTrue(shouldFetchDetails(stored, listing.copy(price = 340_000), refreshAllDetails = false))
        assertTrue(shouldFetchDetails(stored, listing, refreshAllDetails = true))
        assertFalse(shouldFetchDetails(stored, listing, refreshAllDetails = false))
        assertTrue(shouldFetchDetails(stored.copy(lastDetailFetchEpochSeconds = null), listing, refreshAllDetails = false))
        assertEquals(false, shouldFetchDetails(stored, listing, false))
    }
}
