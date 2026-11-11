package househunt.source

import househunt.model.RawListing
import househunt.model.SourceName

interface ListingSource {
    val sourceName: SourceName

    /** Reads every search results page, following pagination. */
    fun fetchSearchListings(): List<RawListing>

    /** Opens the detail page and fills what the search page did not show. */
    fun fetchDetails(listing: RawListing): RawListing
}

/**
 * Shared flow: fetch pages until one adds nothing new, then parse.
 * A site only has to say how to build page URLs and how to parse a page.
 */
abstract class PagedListingSource(
    override val sourceName: SourceName,
    private val searchUrl: String,
    private val maximumPages: Int,
    private val searchFetcher: PageFetcher,
    private val detailFetcher: PageFetcher,
    private val currentEpochSeconds: () -> Long,
) : ListingSource {

    protected abstract fun buildPageUrl(searchUrl: String, pageNumber: Int): String
    abstract fun parseSearchPage(html: String, pageUrl: String, fetchedAtEpochSeconds: Long): List<RawListing>
    abstract fun parseDetailPage(html: String, listing: RawListing): RawListing

    /** The URL of a search results page; page 1 is the configured search URL itself. */
    fun pageUrl(pageNumber: Int): String = if (pageNumber == 1) searchUrl else buildPageUrl(searchUrl, pageNumber)

    override fun fetchSearchListings(): List<RawListing> {
        val found = LinkedHashMap<String, RawListing>()
        for (pageNumber in 1..maximumPages) {
            val pageUrl = pageUrl(pageNumber)
            val html = try {
                searchFetcher.fetchHtml(pageUrl)
            } catch (exception: HttpStatusException) {
                if (pageNumber > 1 && exception.statusCode == 404) break else throw exception
            }
            val listings = parseSearchPage(html, pageUrl, currentEpochSeconds())
            val newListings = listings.filter { it.url !in found }
            if (newListings.isEmpty()) break
            newListings.forEach { found[it.url] = it }
        }
        return found.values.toList()
    }

    override fun fetchDetails(listing: RawListing): RawListing =
        parseDetailPage(detailFetcher.fetchHtml(listing.url), listing)
}
