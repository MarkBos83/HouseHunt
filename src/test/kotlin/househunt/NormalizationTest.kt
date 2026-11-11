package househunt

import househunt.model.EnergyLabel
import househunt.model.ListingStatus
import househunt.normalize.buildHomeKeys
import househunt.normalize.normalizeHouseNumberAddition
import househunt.normalize.normalizePostcode
import househunt.normalize.normalizeStreet
import househunt.normalize.parseAddressLine
import househunt.normalize.parseArea
import househunt.normalize.parseBedrooms
import househunt.normalize.parseEnergyLabel
import househunt.normalize.parsePrice
import househunt.normalize.parseServiceCosts
import househunt.normalize.parseStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NormalizationTest {

    @Test
    fun `house number additions in different spellings become the same value`() {
        listOf("6-B", "6B", "6 b").forEach { text ->
            val parsed = parseAddressLine("Stationsstraat $text")!!
            assertEquals(6, parsed.houseNumber, text)
            assertEquals("b", normalizeHouseNumberAddition(parsed.addition), text)
        }
    }

    @Test
    fun `postcode is uppercase without a space`() {
        assertEquals("3821AB", normalizePostcode("3821 ab Amersfoort"))
        assertNull(normalizePostcode("Amersfoort"))
    }

    @Test
    fun `street is lowercase without accents or extra spaces`() {
        assertEquals("cafe straat", normalizeStreet("  Café   Straat "))
    }

    @Test
    fun `both price types give the same amount and price on request gives none`() {
        assertEquals(350_000, parsePrice("€ 350.000 k.k."))
        assertEquals(350_000, parsePrice("€ 350.000 v.o.n."))
        assertNull(parsePrice("Prijs op aanvraag"))
    }

    @Test
    fun `area bedrooms and service costs are read as whole numbers`() {
        assertEquals(85, parseArea("85 m² woonoppervlak"))
        assertEquals(3, parseBedrooms("3 slaapkamers"))
        assertEquals(185, parseServiceCosts("Servicekosten € 185,50 per maand"))
        assertNull(parseServiceCosts("geen kosten"))
    }

    @Test
    fun `energy labels are normalised`() {
        assertEquals(EnergyLabel.A_PLUS_4, parseEnergyLabel("A++++"))
        assertEquals(EnergyLabel.A_PLUS_4, parseEnergyLabel("A4"))
        assertEquals(EnergyLabel.B, parseEnergyLabel(" b "))
        assertNull(parseEnergyLabel("onbekend"))
    }

    @Test
    fun `status words map to the right status`() {
        assertEquals(ListingStatus.AVAILABLE, parseStatus("Beschikbaar"))
        assertEquals(ListingStatus.UNDER_OFFER, parseStatus("Onder bod"))
        assertEquals(ListingStatus.UNDER_OFFER, parseStatus("Onder optie"))
        assertEquals(ListingStatus.SOLD_CONDITIONS_PENDING, parseStatus("Verkocht onder voorbehoud"))
        assertEquals(ListingStatus.SOLD, parseStatus("Verkocht"))
    }

    @Test
    fun `both matching keys are built, and the street key works without a postcode`() {
        val withPostcode = buildHomeKeys(buildListing(addition = "B"))
        assertEquals("pc:3811AB:12:b", withPostcode.postcodeKey)
        assertEquals("st:stationsstraat:12:b", withPostcode.streetKey)
        assertNull(buildHomeKeys(buildListing(postcode = null)).postcodeKey)
    }
}
