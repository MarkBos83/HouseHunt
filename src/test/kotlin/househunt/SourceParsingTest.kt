package househunt

import househunt.geo.parseNominatimResponse
import househunt.geo.parseOsrmRouteDistance
import househunt.geo.parseOsrmTableDistances
import househunt.geo.parsePdokResponse
import househunt.model.ListingStatus
import househunt.model.SourceName
import househunt.source.BuurtjeSource
import househunt.source.PageFetcher
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * These use small hand-written HTML snippets that mimic the structure assumed in the plan.
 * Once Phase 0 has saved real pages in src/test/resources/fixtures, add tests that load those files.
 */
class SourceParsingTest {
    private val unusedFetcher = object : PageFetcher {
        override fun fetchHtml(url: String): String = error("not used in parser tests")
    }

    private val buurtje = BuurtjeSource("https://buurtje.nl/koopwoningen/amersfoort/", 1, unusedFetcher) { 0 }

    @Test
    fun `detail page reads status and service costs from key value pairs`() {
        val html = """
            <dl><dt>Status</dt><dd>Verkocht onder voorbehoud</dd><dt>VvE-bijdrage</dt><dd>€ 175 per maand</dd></dl>"""
        val detailed = buurtje.parseDetailPage(html, buildListing(SourceName.BUURTJE, serviceCosts = null))

        assertEquals(ListingStatus.SOLD_CONDITIONS_PENDING, detailed.status)
        assertEquals(175, detailed.serviceCostsPerMonth)
    }

    @Test
    fun `map service answers are parsed`() {
        val pdok = """{"response":{"docs":[{"centroide_ll":"POINT(5.38 52.15)"}]}}"""
        assertEquals(52.15, parsePdokResponse(pdok)!!.latitude)
        assertEquals(5.38, parsePdokResponse(pdok)!!.longitude)
        assertEquals(52.2, parseNominatimResponse("""[{"lat":"52.2","lon":"5.4"}]""")!!.latitude)
        assertEquals(listOf(1200.0, null, 800.0), parseOsrmTableDistances("""{"distances":[[1200.0,null,800.0]]}"""))
        assertEquals(1500.4, parseOsrmRouteDistance("""{"routes":[{"distance":1500.4}]}"""))
    }
}
