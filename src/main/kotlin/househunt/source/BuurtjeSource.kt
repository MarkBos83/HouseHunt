package househunt.source

import househunt.model.Coordinates
import househunt.model.ListingStatus
import househunt.model.RawListing
import househunt.model.SourceName
import househunt.normalize.normalizePostcode
import househunt.normalize.parseAddressLine
import househunt.normalize.parseArea
import househunt.normalize.parseEnergyLabel
import househunt.normalize.parsePrice
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

private const val CARD_SELECTOR = "div.pc"
private const val LINK_SELECTOR = "a.pc-body"
private const val ADDRESS_SELECTOR = ".pc-adres"
private const val PLACE_SELECTOR = ".pc-plaats"
private const val ENERGY_SELECTOR = ".pc-energy-text"
private const val FIRST_META_SELECTOR = ".pc-meta-item"
private const val PRICE_SELECTOR = ".pc-prijs"
private const val FEATURE_ROW_SELECTOR = ".ws-feature-row"
private const val FEATURE_LABEL_SELECTOR = ".ws-feature-label"
private const val FEATURE_VALUE_SELECTOR = ".ws-feature-value"
private const val LOCATE_SELECTOR = "[data-lat][data-lng]"

/**
 * Buurtje: plain HTTP; the search page shows address, postcode, price, living area (the first
 * meta item; the second is the plot), energy label and coordinates. Status and service costs
 * come from the detail page's "Kenmerken" section.
 */
class BuurtjeSource(
    searchUrl: String,
    maximumPages: Int,
    fetcher: PageFetcher,
    currentEpochSeconds: () -> Long,
) : PagedListingSource(SourceName.BUURTJE, searchUrl, maximumPages, fetcher, fetcher, currentEpochSeconds) {

    override fun buildPageUrl(searchUrl: String, pageNumber: Int): String =
        addQueryParameter(searchUrl, "page", pageNumber)

    override fun parseSearchPage(html: String, pageUrl: String, fetchedAtEpochSeconds: Long): List<RawListing> =
        Jsoup.parse(html, pageUrl).select(CARD_SELECTOR).mapNotNull { parseCard(it, pageUrl, fetchedAtEpochSeconds) }

    private fun parseCard(card: Element, pageUrl: String, fetchedAtEpochSeconds: Long): RawListing? {
        val href = card.selectFirst(LINK_SELECTOR)?.attr("href")?.ifBlank { null } ?: return null
        val address = card.selectFirst(ADDRESS_SELECTOR)?.text()?.let(::parseAddressLine)
        val place = card.selectFirst(PLACE_SELECTOR)?.text().orEmpty()
        return RawListing(
            source = SourceName.BUURTJE,
            url = resolveUrl(pageUrl, href).substringBefore('?'),
            street = address?.street,
            houseNumber = address?.houseNumber,
            addition = address?.addition.orEmpty(),
            postcode = normalizePostcode(place),
            city = extractCity(place),
            price = card.selectFirst(PRICE_SELECTOR)?.text()?.let(::parsePrice),
            areaSquareMeters = card.selectFirst(FIRST_META_SELECTOR)?.text()?.let(::parseArea),
            energyLabel = parseEnergyLabel(card.selectFirst(ENERGY_SELECTOR)?.text()),
            status = ListingStatus.AVAILABLE,
            coordinates = readCoordinates(card),
            fetchedAtEpochSeconds = fetchedAtEpochSeconds,
        )
    }

    private fun readCoordinates(card: Element): Coordinates? {
        val locate = card.selectFirst(LOCATE_SELECTOR) ?: return null
        val latitude = locate.attr("data-lat").toDoubleOrNull() ?: return null
        val longitude = locate.attr("data-lng").toDoubleOrNull() ?: return null
        return Coordinates(latitude, longitude)
    }

    override fun parseDetailPage(html: String, listing: RawListing): RawListing {
        val document = Jsoup.parse(html, listing.url)
        return enrichFromPairs(listing, readKeyValuePairs(document) + readFeatureRows(document))
    }

    /** The "Kenmerken" section: one row per feature with a label and a value. */
    private fun readFeatureRows(document: Document): Map<String, String> =
        document.select(FEATURE_ROW_SELECTOR).mapNotNull { row ->
            val label = row.selectFirst(FEATURE_LABEL_SELECTOR)?.ownText()?.trim()?.lowercase()?.ifEmpty { null }
            val value = row.selectFirst(FEATURE_VALUE_SELECTOR)?.text()?.trim()?.ifEmpty { null }
            if (label == null || value == null) null else label to value
        }.toMap()
}
