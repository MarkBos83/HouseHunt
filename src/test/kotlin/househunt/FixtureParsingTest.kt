package househunt

import househunt.model.SourceName
import househunt.source.BuurtjeSource
import househunt.source.HuislijnSource
import househunt.source.PageFetcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Parser tests against the real pages saved by the Phase 0 probe (src/test/resources/fixtures). */
class FixtureParsingTest {
    private val unusedFetcher = object : PageFetcher {
        override fun fetchHtml(url: String): String = error("not used in parser tests")
    }

    private fun fixture(name: String): String =
        javaClass.getResource("/fixtures/$name")!!.readText()

    @Test
    fun `huislijn search page gives complete listings`() {
        val source = HuislijnSource("https://www.huislijn.nl/x", 1, unusedFetcher) { 0 }
        val listings = source.parseSearchPage(fixture("huislijn-http-search.html"), "https://www.huislijn.nl/x", 7)

        assertTrue(listings.isNotEmpty())
        assertTrue(listings.all { it.url.startsWith("https://www.huislijn.nl/") })
        assertTrue(listings.all { it.street != null && it.houseNumber != null && it.postcode != null })
        assertTrue(listings.all { (it.price ?: 0) > 0 && it.areaSquareMeters != null && it.coordinates != null })
    }

    @Test
    fun `huislijn house number with an addition is split`() {
        val html = """<hl-search-object-display :object='{"link":"/koopwoning/x/1/kapelweg-155-b-amersfoort","street":"Kapelweg","housenumber":"155 B","zipcode":"3812AB","city":"Amersfoort","price":"300000","status":"beschikbaar"}'></hl-search-object-display>"""
        val source = HuislijnSource("https://www.huislijn.nl/x", 1, unusedFetcher) { 0 }
        val kapelweg = source.parseSearchPage(html, "https://www.huislijn.nl/x", 7).single()

        assertEquals(155, kapelweg.houseNumber)
        assertEquals("B", kapelweg.addition)
    }

    @Test
    fun `buurtje search page gives every card`() {
        val source = BuurtjeSource("https://www.buurtje.nl/koopwoningen/amersfoort/", 1, unusedFetcher) { 0 }
        val listings = source.parseSearchPage(fixture("buurtje-http-search.html"), "https://buurtje.nl/koopwoningen/amersfoort/?prijs_max=400000", 7)

        assertTrue(listings.isNotEmpty())
        assertTrue(listings.all { it.url.startsWith("https://buurtje.nl/") })
        assertTrue(listings.all { it.street != null && it.houseNumber != null && it.postcode != null })
        assertTrue(listings.all { (it.price ?: 0) in 1..400_000 })
    }

    @Test
    fun `buurtje detail page gives bedrooms, status and energy label`() {
        val source = BuurtjeSource("https://www.buurtje.nl/koopwoningen/amersfoort/", 1, unusedFetcher) { 0 }
        val search = buildListing(SourceName.BUURTJE, bedrooms = null, label = null, status = null)
        val detailed = source.parseDetailPage(fixture("buurtje-http-detail.html"), search)

        assertNotNull(detailed.bedrooms)
        assertNotNull(detailed.energyLabel)
        assertNotNull(detailed.status)
    }
}
