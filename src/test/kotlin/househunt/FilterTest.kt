package househunt

import househunt.filter.passesBasicFilters
import househunt.filter.passesBikeFilter
import househunt.geo.buildBikeInfo
import househunt.geo.calculateCyclingMinutes
import househunt.model.EnergyLabel
import househunt.model.ListingStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FilterTest {
    private val filters = buildFilters()

    @Test
    fun `a home that meets every rule passes`() {
        assertTrue(passesBasicFilters(buildHome(), filters))
    }

    @Test
    fun `price above the limit or on request is hidden`() {
        assertTrue(passesBasicFilters(buildHome(price = 400_000), filters))
        assertFalse(passesBasicFilters(buildHome(price = 400_001), filters))
        assertFalse(passesBasicFilters(buildHome(price = null), filters))
    }

    @Test
    fun `area below 75 or unknown is hidden`() {
        assertTrue(passesBasicFilters(buildHome(area = 75), filters))
        assertFalse(passesBasicFilters(buildHome(area = 74), filters))
        assertFalse(passesBasicFilters(buildHome(area = null), filters))
    }

    @Test
    fun `unknown bedrooms and label are shown, known bad ones are hidden`() {
        assertTrue(passesBasicFilters(buildHome(bedrooms = null), filters))
        assertFalse(passesBasicFilters(buildHome(bedrooms = 1), filters))
        assertTrue(passesBasicFilters(buildHome(label = null), filters))
        assertTrue(passesBasicFilters(buildHome(label = EnergyLabel.B), filters))
        assertFalse(passesBasicFilters(buildHome(label = EnergyLabel.C), filters))
    }

    @Test
    fun `only sold homes are hidden by status`() {
        assertFalse(passesBasicFilters(buildHome(status = ListingStatus.SOLD), filters))
        assertTrue(passesBasicFilters(buildHome(status = ListingStatus.SOLD_CONDITIONS_PENDING), filters))
        assertTrue(passesBasicFilters(buildHome(status = ListingStatus.UNDER_OFFER), filters))
    }

    @Test
    fun `other places are hidden`() {
        assertFalse(passesBasicFilters(buildHome(city = "Utrecht"), filters))
    }

    @Test
    fun `bike time of exactly 13 minutes passes and 13 minutes 1 second fails`() {
        // At 12 km/h, 2,600 m is exactly 13:00 and 2,604 m is a little over.
        assertEquals(13.0, calculateCyclingMinutes(2600, 12.0))
        assertTrue(passesBikeFilter(buildBikeInfo(2600, 12.0), filters))
        assertFalse(passesBikeFilter(buildBikeInfo(2604, 12.0), filters))
    }

    @Test
    fun `at 16 km per hour the limit is 3466 metres`() {
        assertTrue(passesBikeFilter(buildBikeInfo(3466, 16.0), filters))
        assertFalse(passesBikeFilter(buildBikeInfo(3467, 16.0), filters))
    }

    @Test
    fun `a missing route never hides a home`() {
        assertTrue(passesBikeFilter(null, filters))
    }
}
