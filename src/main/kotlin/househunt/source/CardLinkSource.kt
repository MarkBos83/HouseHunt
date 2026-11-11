package househunt.source

import househunt.model.RawListing
import househunt.model.SourceName
import org.jsoup.Jsoup

/**
 * Funda, Huislijn and Buurtje: result cards are found from their detail links, so only the link
 * pattern and the page parameter differ per site. Both are checked in Phase 0 against saved HTML.
 */
class CardLinkSource(
    sourceName: SourceName,
    searchUrl: String,
    private val detailLinkPattern: Regex,
    private val pageParameterName: String,
    maximumPages: Int,
    searchFetcher: PageFetcher,
    detailFetcher: PageFetcher,
    currentEpochSeconds: () -> Long,
) : PagedListingSource(sourceName, searchUrl, maximumPages, searchFetcher, detailFetcher, currentEpochSeconds) {

    override fun buildPageUrl(searchUrl: String, pageNumber: Int): String {
        return addQueryParameter(searchUrl, pageParameterName, pageNumber)
    }

    override fun parseSearchPage(html: String, pageUrl: String, fetchedAtEpochSeconds: Long): List<RawListing> {
        val document = Jsoup.parse(html, pageUrl)
        return findCardsByLink(document, detailLinkPattern, pageUrl).map { (card, url) ->
            val heading = card.selectFirst("h1, h2, h3, h4")?.text()
            val address = extractAddress(heading, null, url)
            buildListingFromCard(sourceName, url, card, address, fetchedAtEpochSeconds)
        }
    }

    override fun parseDetailPage(html: String, listing: RawListing): RawListing {
        val pairs = readKeyValuePairs(Jsoup.parse(html, listing.url))
        val enriched = enrichFromPairs(listing, pairs)
        return enriched.copy(coordinates = enriched.coordinates ?: extractEmbeddedCoordinates(html))
    }
}
