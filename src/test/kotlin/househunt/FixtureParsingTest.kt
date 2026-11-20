package househunt

import househunt.model.EnergyLabel
import househunt.model.ListingStatus
import househunt.model.SourceName
import househunt.source.BuurtjeSource
import househunt.source.HuislijnSource
import househunt.source.PageFetcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Parser tests against the real pages saved by the Phase 0 probe (src/test/resources/fixtures). */
class FixtureParsingTest {
    private val unusedFetcher = object : PageFetcher {
        override fun fetchHtml(url: String): String = error("not used in parser tests")
    }

    private fun fixture(name: String): String =
        javaClass.getResource("/fixtures/$name")!!.readText()

    @Test
    fun `huislijn search page gives all embedded listings with details`() {
        val source = HuislijnSource("https://www.huislijn.nl/koopwoning/nederland/utrecht/amersfoort", 1, unusedFetcher) { 0 }
        val listings = source.parseSearchPage(fixture("huislijn-http-search.html"), "https://www.huislijn.nl/koopwoning/nederland/utrecht/amersfoort", 7)

        assertEquals(13, listings.size)
        val first = listings.first()
        assertEquals("Meerval", first.street)
        assertEquals(26, first.houseNumber)
        assertEquals("3824JN", first.postcode)
        assertEquals(430_000, first.price)
        assertEquals(110, first.areaSquareMeters)
        assertEquals(3, first.bedrooms)
        assertEquals(EnergyLabel.A, first.energyLabel)
        assertEquals(ListingStatus.AVAILABLE, first.status)
        assertEquals("https://www.huislijn.nl/koopwoning/nederland/utrecht/4449539/meerval-26-amersfoort", first.url)
        assertEquals(52.20399565, first.coordinates!!.latitude)
    }

    @Test
    fun `huislijn house number with an addition is split`() {
        val source = HuislijnSource("https://www.huislijn.nl/x", 1, unusedFetcher) { 0 }
        val listings = source.parseSearchPage(fixture("huislijn-http-search.html"), "https://www.huislijn.nl/x", 7)
        val kapelweg = listings.first { it.street == "Kapelweg" }

        assertEquals(155, kapelweg.houseNumber)
        assertEquals("B", kapelweg.addition)
    }

    @Test
    fun `buurtje search page gives every card`() {
        val source = BuurtjeSource("https://www.buurtje.nl/koopwoningen/amersfoort/", 1, unusedFetcher) { 0 }
        val listings = source.parseSearchPage(fixture("buurtje-http-search.html"), "https://buurtje.nl/koopwoningen/amersfoort/?prijs_max=400000", 7)

        assertEquals(36, listings.size)
        val first = listings.first()
        assertEquals("Gerrit van Stellingwerfstraat", first.street)
        assertEquals(34, first.houseNumber)
        assertEquals("3812SL", first.postcode)
        assertEquals("Amersfoort", first.city)
        assertEquals(400_000, first.price)
        assertEquals(106, first.areaSquareMeters)
        assertEquals(EnergyLabel.C, first.energyLabel)
        assertEquals("https://buurtje.nl/tussenwoning/gerrit-van-stellingwerfstraat-34-amersfoort/", first.url)
        assertEquals(52.1567, first.coordinates!!.latitude)
        assertTrue(listings.all { (it.price ?: 0) <= 400_000 })
    }

    @Test
    fun `buurtje detail page gives bedrooms, status and energy label`() {
        val source = BuurtjeSource("https://www.buurtje.nl/koopwoningen/amersfoort/", 1, unusedFetcher) { 0 }
        val search = buildListing(SourceName.BUURTJE, bedrooms = null, label = null, status = null)
        val detailed = source.parseDetailPage(fixture("buurtje-http-detail.html"), search)

        assertEquals(4, detailed.bedrooms)
        assertEquals(EnergyLabel.C, detailed.energyLabel)
        assertEquals(ListingStatus.AVAILABLE, detailed.status)
    }
}
