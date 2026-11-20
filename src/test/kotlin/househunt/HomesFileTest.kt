package househunt

import househunt.geo.buildGoogleMapsUrl
import househunt.merge.mergeListings
import househunt.model.EnergyLabel
import househunt.model.ListingStatus
import househunt.model.SourceName
import househunt.store.HomeEntry
import househunt.store.HomesFile
import househunt.store.mergeSourceEntries
import househunt.store.toHomeEntry
import househunt.store.toRawListing
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomesFileTest {

    private fun entry(url: String, source: String = "huislijn", firstSeen: String = "2026-11-01") = HomeEntry(
        source = source, address = "De Beurs 40, 3823 GA Amersfoort", url = url,
        firstSeen = firstSeen, lastSeen = firstSeen,
    )

    @Test
    fun `an entry written by the claude task becomes a listing`() {
        val claudeEntry = HomeEntry(
            source = "funda", address = "Weegsteen 6-B, 3823 GC Amersfoort", priceEuros = 345_000,
            livingAreaSquareMeters = 88, bedrooms = 3, energyLabel = "B", status = "under offer",
            url = "https://www.funda.nl/x", firstSeen = "2026-11-20", lastSeen = "2026-11-20",
        )
        val listing = toRawListing(claudeEntry)!!

        assertEquals(SourceName.FUNDA, listing.source)
        assertEquals("Weegsteen", listing.street)
        assertEquals(6, listing.houseNumber)
        assertEquals("B", listing.addition)
        assertEquals("3823GC", listing.postcode)
        assertEquals("Amersfoort", listing.city)
        assertEquals(EnergyLabel.B, listing.energyLabel)
        assertEquals(ListingStatus.UNDER_OFFER, listing.status)
    }

    @Test
    fun `an unknown energy label and sold subject to conditions are understood`() {
        val listing = toRawListing(entry("u").copy(source = "pararius", energyLabel = "unknown", status = "sold subject to conditions"))!!
        assertNull(listing.energyLabel)
        assertEquals(ListingStatus.SOLD_CONDITIONS_PENDING, listing.status)
    }

    @Test
    fun `a buurtje listing survives the trip through homes json`() {
        val original = buildListing(SourceName.BUURTJE, addition = "B", serviceCosts = 150)
        val back = toRawListing(toHomeEntry(original, "2026-11-20"))!!

        assertEquals(original.street, back.street)
        assertEquals(original.houseNumber, back.houseNumber)
        assertEquals("B", back.addition)
        assertEquals(original.postcode, back.postcode)
        assertEquals(150, back.serviceCostsPerMonth)
        assertEquals(EnergyLabel.A, back.energyLabel)
    }

    @Test
    fun `homes keep first seen, disappeared homes are flagged`() {
        val previous = listOf(entry("a"), entry("b"))
        val merged = mergeSourceEntries(previous, listOf(entry("a", firstSeen = "2026-11-20"), entry("c", firstSeen = "2026-11-20")), "2026-11-20")

        assertEquals("2026-11-01", merged.first { it.url == "a" }.firstSeen)
        assertEquals("2026-11-20", merged.first { it.url == "a" }.lastSeen)
        assertEquals("2026-11-20", merged.first { it.url == "c" }.firstSeen)
        assertTrue(merged.first { it.url == "b" }.noLongerListed)
    }

    @Test
    fun `entries of other sources are written back untouched`() {
        val file = Files.createTempFile("homes", ".json")
        Files.writeString(file, """[{"source":"funda","address":"De Beurs 40, 3823 GA Amersfoort","url":"f","first_seen":"2026-11-20","last_seen":"2026-11-20","extra":"kept"}]""")

        val entries = HomesFile(file).updateSources(mapOf(SourceName.HUISLIJN to listOf(entry("h"))), "2026-11-21")

        assertEquals(listOf("f", "h"), entries.map { it.url })
        assertTrue(Files.readString(file).contains("\"extra\": \"kept\""))
    }

    @Test
    fun `the google maps link searches for the full address`() {
        val home = mergeListings(listOf(buildListing(SourceName.HUISLIJN)))
        assertEquals("https://www.google.com/maps/search/?api=1&query=Stationsstraat+12%2C+3811+AB+Amersfoort", buildGoogleMapsUrl(home))
    }
}
