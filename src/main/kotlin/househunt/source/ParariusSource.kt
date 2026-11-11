package househunt.source

import househunt.model.ListingStatus
import househunt.model.RawListing
import househunt.model.SourceName
import househunt.normalize.isPriceOnRequest
import househunt.normalize.normalizePostcode
import househunt.normalize.parseArea
import househunt.normalize.parseEnergyLabel
import househunt.normalize.parsePrice
import househunt.normalize.parseStatus
import househunt.normalize.parseWholeNumber
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

private const val CARD_SELECTOR = "section.listing-search-item"
private const val TITLE_LINK_SELECTOR = "a.listing-search-item__link--title"
private const val SUBTITLE_SELECTOR = ".listing-search-item__sub-title"
private const val PRICE_SELECTOR = ".listing-search-item__price"
private const val SURFACE_SELECTOR = ".illustrated-features__item--surface-area"
private const val LABEL_SELECTOR = ".listing-label"
private const val BEDROOMS_SELECTOR = ".listing-features__description--number_of_bedrooms"
private const val ENERGY_LABEL_SELECTOR = ".listing-features__description--energy_label"

/** Pararius needs the headless browser; bedrooms and energy label only exist on the detail page. */
class ParariusSource(
    searchUrl: String,
    maximumPages: Int,
    browserFetcher: PageFetcher,
    currentEpochSeconds: () -> Long,
) : PagedListingSource(SourceName.PARARIUS, searchUrl, maximumPages, browserFetcher, browserFetcher, currentEpochSeconds) {

    override fun buildPageUrl(searchUrl: String, pageNumber: Int): String =
        "${searchUrl.trimEnd('/')}/page-$pageNumber"

    override fun parseSearchPage(html: String, pageUrl: String, fetchedAtEpochSeconds: Long): List<RawListing> {
        val document = Jsoup.parse(html, pageUrl)
        return document.select(CARD_SELECTOR).mapNotNull { parseCard(it, fetchedAtEpochSeconds) }
    }

    private fun parseCard(card: Element, fetchedAtEpochSeconds: Long): RawListing? {
        val link = card.selectFirst(TITLE_LINK_SELECTOR) ?: return null
        val url = link.absUrl("href").substringBefore('?').ifEmpty { return null }
        val address = extractAddress(null, link.text(), url)
        val subtitle = card.selectFirst(SUBTITLE_SELECTOR)?.text().orEmpty()
        val priceText = card.selectFirst(PRICE_SELECTOR)?.text().orEmpty()
        return RawListing(
            source = SourceName.PARARIUS,
            url = url,
            street = address?.street,
            houseNumber = address?.houseNumber,
            addition = address?.addition.orEmpty(),
            postcode = normalizePostcode(subtitle),
            city = extractCity(subtitle),
            price = parsePrice(priceText),
            priceOnRequest = isPriceOnRequest(priceText),
            areaSquareMeters = card.selectFirst(SURFACE_SELECTOR)?.text()?.let(::parseArea),
            status = readStatus(card),
            fetchedAtEpochSeconds = fetchedAtEpochSeconds,
        )
    }

    private fun readStatus(card: Element): ListingStatus {
        val labelTexts = card.select(LABEL_SELECTOR).joinToString(" ") { it.text() }
        return parseStatus(labelTexts) ?: ListingStatus.AVAILABLE
    }

    override fun parseDetailPage(html: String, listing: RawListing): RawListing {
        val document = Jsoup.parse(html, listing.url)
        val pairs = readKeyValuePairs(document)
        val bedrooms = document.selectFirst(BEDROOMS_SELECTOR)?.text()?.let(::parseWholeNumber)
        val energyLabel = parseEnergyLabel(document.selectFirst(ENERGY_LABEL_SELECTOR)?.text())
        val withPageValues = listing.copy(
            bedrooms = listing.bedrooms ?: bedrooms,
            energyLabel = listing.energyLabel ?: energyLabel,
        )
        val enriched = enrichFromPairs(withPageValues, pairs)
        return enriched.copy(coordinates = enriched.coordinates ?: extractEmbeddedCoordinates(html))
    }
}
