package househunt

import househunt.merge.groupListingsByKeys
import househunt.merge.mergeListings
import househunt.model.EnergyLabel
import househunt.model.SourceName
import kotlin.test.Test
import kotlin.test.assertEquals

class MergeTest {

    @Test
    fun `each field comes from the highest priority source that has a value`() {
        val funda = buildListing(SourceName.FUNDA, price = 340_000, bedrooms = null, label = null)
        val pararius = buildListing(SourceName.PARARIUS, price = 345_000, bedrooms = 4, label = EnergyLabel.B)
        val buurtje = buildListing(SourceName.BUURTJE, price = 330_000, bedrooms = 2, label = EnergyLabel.C, serviceCosts = 150)

        val merged = mergeListings(listOf(buurtje, pararius, funda))

        assertEquals(340_000, merged.price)
        assertEquals(4, merged.bedrooms)
        assertEquals(EnergyLabel.B, merged.energyLabel)
        assertEquals(150, merged.serviceCostsPerMonth)
    }

    @Test
    fun `the address links to the highest priority source`() {
        val merged = mergeListings(listOf(buildListing(SourceName.HUISLIJN), buildListing(SourceName.PARARIUS)))
        assertEquals("https://example.test/pararius/1", merged.url)
    }

    @Test
    fun `listings with different spellings of the addition are grouped`() {
        val first = buildListing(SourceName.FUNDA, addition = "B")
        val second = buildListing(SourceName.PARARIUS, addition = "b")
        assertEquals(1, groupListingsByKeys(listOf(first, second)).size)
    }

    @Test
    fun `a listing without postcode is matched on the street key`() {
        val withPostcode = buildListing(SourceName.FUNDA)
        val withoutPostcode = buildListing(SourceName.BUURTJE, postcode = null, street = "stationsstraat")
        assertEquals(1, groupListingsByKeys(listOf(withPostcode, withoutPostcode)).size)
    }

    @Test
    fun `different house numbers stay separate`() {
        val groups = groupListingsByKeys(listOf(buildListing(houseNumber = 12), buildListing(SourceName.PARARIUS, houseNumber = 14)))
        assertEquals(2, groups.size)
    }
}
