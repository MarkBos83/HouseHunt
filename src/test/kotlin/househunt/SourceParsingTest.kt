package househunt

import househunt.geo.parseNominatimResponse
import househunt.geo.parseOsrmRouteDistance
import househunt.geo.parseOsrmTableDistances
import househunt.geo.parsePdokResponse
import househunt.model.EnergyLabel
import househunt.model.ListingStatus
import househunt.model.SourceName
import househunt.source.CardLinkSource
import househunt.source.PageFetcher
import househunt.source.ParariusSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * These use small hand-written HTML snippets that mimic the structure assumed in the plan.
 * Once Phase 0 has saved real pages in src/test/resources/fixtures, add tests that load those files.
 */
class SourceParsingTest {
    private val unusedFetcher = object : PageFetcher {
        override fun fetchHtml(url: String): String = error("not used in parser tests")
    }

    private val pararius = ParariusSource("https://www.pararius.nl/koopwoningen/amersfoort", 1, unusedFetcher) { 0 }

    private val card = CardLinkSource(
        SourceName.FUNDA, "https://www.funda.nl/zoeken/koop", Regex("/woning/"),
        "page", 1, unusedFetcher, unusedFetcher,
    ) { 0 }

    @Test
    fun `pararius search card is parsed`() {
        val html = """
            <section class="listing-search-item">
              <a class="listing-search-item__link--title" href="/appartement-te-koop/amersfoort/abc/stationsstraat-12-b">Appartement Stationsstraat 12-B</a>
              <div class="listing-search-item__sub-title">3811 AB Amersfoort (Binnenstad)</div>
              <div class="listing-search-item__price">€ 325.000 k.k.</div>
              <ul><li class="illustrated-features__item illustrated-features__item--surface-area">82 m²</li></ul>
              <span class="listing-label">Onder bod</span>
            </section>"""
        val listing = pararius.parseSearchPage(html, "https://www.pararius.nl/koopwoningen/amersfoort", 5).single()

        assertEquals("Stationsstraat", listing.street)
        assertEquals(12, listing.houseNumber)
        assertEquals("B", listing.addition)
        assertEquals("3811AB", listing.postcode)
        assertEquals("Amersfoort", listing.city)
        assertEquals(325_000, listing.price)
        assertEquals(82, listing.areaSquareMeters)
        assertEquals(ListingStatus.UNDER_OFFER, listing.status)
    }

    @Test
    fun `pararius detail page adds bedrooms and energy label`() {
        val search = buildListing(SourceName.PARARIUS, bedrooms = null, label = null)
        val html = """
            <dd class="listing-features__description listing-features__description--number_of_bedrooms">3</dd>
            <dd class="listing-features__description listing-features__description--energy_label">A</dd>"""
        val detailed = pararius.parseDetailPage(html, search)

        assertEquals(3, detailed.bedrooms)
        assertEquals(EnergyLabel.A, detailed.energyLabel)
    }

    @Test
    fun `card sources find a card from its detail link and read the visible values`() {
        val html = """
            <ul>
              <li><a href="/woning/ringweg-5"><h3>Ringweg 5</h3></a>
                  <p>3812 CD Amersfoort</p><p>€ 299.000 v.o.n.</p><p>95 m² · 3 slaapkamers</p></li>
              <li><a href="/woning/dorpsstraat-9"><h3>Dorpsstraat 9</h3></a><p>Prijs op aanvraag €</p></li>
            </ul>"""
        val listings = card.parseSearchPage(html, "https://www.funda.nl/zoeken/koop", 5)
        val first = listings.first { it.houseNumber == 5 }

        assertEquals(2, listings.size)
        assertEquals("Ringweg", first.street)
        assertEquals("3812CD", first.postcode)
        assertEquals(299_000, first.price)
        assertEquals(95, first.areaSquareMeters)
        assertEquals(3, first.bedrooms)
        assertNull(listings.first { it.houseNumber == 9 }.price)
    }

    @Test
    fun `card detail page reads status and service costs from key value pairs`() {
        val html = """
            <dl><dt>Status</dt><dd>Verkocht onder voorbehoud</dd><dt>VvE-bijdrage</dt><dd>€ 175 per maand</dd></dl>"""
        val detailed = card.parseDetailPage(html, buildListing(SourceName.FUNDA, serviceCosts = null))

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
