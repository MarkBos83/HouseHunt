package househunt.source

import househunt.model.Coordinates
import househunt.model.ListingStatus
import househunt.model.RawListing
import househunt.model.SourceName
import househunt.normalize.parseEnergyLabel
import househunt.normalize.parseStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import java.math.BigDecimal

private const val OBJECT_ELEMENT = "hl-search-object-display"
private const val OBJECT_ATTRIBUTE = ":object"
private val houseNumberPattern = Regex("""^(\d+)\s*[-\s]?\s*(.*)$""")
private val lenientJson = Json { ignoreUnknownKeys = true }

/**
 * Huislijn embeds every listing as a JSON attribute on its search page (verified in Phase 0),
 * including address, price, status, living area, bedrooms, energy label and coordinates.
 * Plain HTTP is enough and no detail page is needed.
 */
class HuislijnSource(
    searchUrl: String,
    maximumPages: Int,
    fetcher: PageFetcher,
    currentEpochSeconds: () -> Long,
) : PagedListingSource(SourceName.HUISLIJN, searchUrl, maximumPages, fetcher, fetcher, currentEpochSeconds) {

    override fun buildPageUrl(searchUrl: String, pageNumber: Int): String =
        addQueryParameter(searchUrl, "page", pageNumber)

    override fun parseSearchPage(html: String, pageUrl: String, fetchedAtEpochSeconds: Long): List<RawListing> =
        Jsoup.parse(html, pageUrl).select(OBJECT_ELEMENT).mapNotNull { element ->
            val json = element.attr(OBJECT_ATTRIBUTE).ifBlank { null } ?: return@mapNotNull null
            runCatching { parseObject(lenientJson.parseToJsonElement(json).jsonObject, pageUrl, fetchedAtEpochSeconds) }.getOrNull()
        }

    private fun parseObject(item: JsonObject, pageUrl: String, fetchedAtEpochSeconds: Long): RawListing? {
        val link = item.text("link") ?: return null
        val properties = item["properties"]?.jsonObject
        val price = item.text("price")?.let { BigDecimal(it).toInt() }?.takeIf { it > 0 }
        val (number, addition) = splitHouseNumber(item.text("housenumber"))
        return RawListing(
            source = SourceName.HUISLIJN,
            url = resolveUrl(pageUrl, link),
            street = item.text("street"),
            houseNumber = number,
            addition = addition,
            postcode = item.text("zipcode"),
            city = item.text("city"),
            price = price,
            priceOnRequest = price == null,
            areaSquareMeters = properties?.get("WoonOpp")?.jsonPrimitive?.intOrNull,
            bedrooms = properties?.get("AantalSlaapkamers")?.jsonPrimitive?.intOrNull,
            energyLabel = parseEnergyLabel(properties?.text("EPAAdvies")),
            status = parseStatus(item.text("status")) ?: ListingStatus.AVAILABLE,
            coordinates = readCoordinates(item["location"]?.jsonObject),
            fetchedAtEpochSeconds = fetchedAtEpochSeconds,
        )
    }

    /** "155 B" gives number 155 and addition "B". */
    private fun splitHouseNumber(text: String?): Pair<Int?, String> {
        val match = houseNumberPattern.find(text?.trim().orEmpty()) ?: return null to ""
        return match.groupValues[1].toInt() to match.groupValues[2].trim()
    }

    private fun readCoordinates(location: JsonObject?): Coordinates? {
        val latitude = location?.get("lat")?.jsonPrimitive?.doubleOrNull ?: return null
        val longitude = location["lon"]?.jsonPrimitive?.doubleOrNull ?: return null
        return Coordinates(latitude, longitude)
    }

    /** Everything is already on the search page, so no detail request is made. */
    override fun fetchDetails(listing: RawListing): RawListing = listing

    override fun parseDetailPage(html: String, listing: RawListing): RawListing =
        enrichFromPairs(listing, readKeyValuePairs(Jsoup.parse(html, listing.url)))
}

private fun JsonObject.text(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull
