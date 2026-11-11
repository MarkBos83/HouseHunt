package househunt.source

import househunt.model.Coordinates
import househunt.model.ListingStatus
import househunt.model.RawListing
import househunt.model.SourceName
import househunt.normalize.ParsedAddress
import househunt.normalize.isPriceOnRequest
import househunt.normalize.normalizePostcode
import househunt.normalize.parseAddressLine
import househunt.normalize.parseArea
import househunt.normalize.parseBedrooms
import househunt.normalize.parseEnergyLabel
import househunt.normalize.parsePrice
import househunt.normalize.parseServiceCosts
import househunt.normalize.parseStatus
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI

private val propertyTypePrefixes = listOf(
    "appartement", "huis", "woning", "woonhuis", "villa", "bungalow", "maisonnette", "benedenwoning",
    "bovenwoning", "tussenwoning", "hoekwoning", "eengezinswoning", "penthouse", "portiekwoning",
    "galerijflat", "studio", "grachtenpand", "herenhuis", "landhuis", "woonboerderij", "twee-onder-een-kapwoning",
)
private val cityAfterPostcodePattern = Regex("""\d{4}\s?[A-Za-z]{2}\s+([\p{L}][\p{L}\s'’-]*)""")
private val latitudePattern = Regex(""""latitude"\s*:\s*"?(-?\d{2}\.\d+)""")
private val longitudePattern = Regex(""""longitude"\s*:\s*"?(-?\d{1,2}\.\d+)""")

// ---- addresses ----

fun stripPropertyTypePrefix(text: String): String {
    val lowered = text.trim().lowercase()
    val prefix = propertyTypePrefixes.firstOrNull { lowered.startsWith("$it ") } ?: return text.trim()
    return text.trim().substring(prefix.length).trim()
}

fun capitalizeWords(text: String): String =
    text.split(' ').joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }

/** Some sites put spaces or line breaks around a link, which URI would reject. */
fun resolveUrl(pageUrl: String, href: String): String = URI.create(pageUrl).resolve(href.trim()).toString()

fun addQueryParameter(url: String, name: String, value: Int): String {
    val separator = if ('?' in url) '&' else '?'
    return "$url$separator$name=$value"
}

fun extractCity(text: String): String? =
    cityAfterPostcodePattern.find(text)?.groupValues?.get(1)?.trim()?.split('(', ',')?.first()?.trim()

/** Picks the first usable address from a heading, the link text or the URL slug. */
fun extractAddress(headingText: String?, linkText: String?, url: String): ParsedAddress? {
    val fromText = listOfNotNull(headingText, linkText)
        .firstNotNullOfOrNull { parseAddressLine(stripPropertyTypePrefix(it)) }
    return fromText ?: parseAddressFromUrl(url)
}

private fun parseAddressFromUrl(url: String): ParsedAddress? {
    val segments = URI.create(url).path.split('/').filter { it.isNotBlank() }.reversed()
    return segments.firstNotNullOfOrNull { segment ->
        parseAddressLine(stripPropertyTypePrefix(segment.replace('-', ' ')))
            ?.let { it.copy(street = capitalizeWords(it.street)) }
    }
}

// ---- search cards ----

/**
 * Finds result cards starting from detail links: the smallest surrounding element that shows a price
 * and contains only one distinct detail link.
 */
fun findCardsByLink(document: Document, hrefPattern: Regex, pageUrl: String): List<Pair<Element, String>> {
    val cards = LinkedHashMap<String, Element>()
    for (anchor in document.select("a[href]")) {
        val url = resolveUrl(pageUrl, anchor.attr("href")).substringBefore('#').substringBefore('?')
        if (!hrefPattern.containsMatchIn(url) || url.trimEnd('/') == pageUrl.substringBefore('?').trimEnd('/')) continue
        val card = findSmallestCardAround(anchor, hrefPattern, pageUrl) ?: continue
        cards.putIfAbsent(url, card)
    }
    return cards.map { (url, card) -> card to url }
}

private fun findSmallestCardAround(anchor: Element, hrefPattern: Regex, pageUrl: String): Element? {
    var current: Element? = anchor
    var depth = 0
    while (current != null && depth < 6) {
        if (current.text().contains("€")) {
            return current.takeIf { countDistinctLinks(it, hrefPattern, pageUrl) == 1 }
        }
        current = current.parent()
        depth++
    }
    return null
}

private fun countDistinctLinks(card: Element, hrefPattern: Regex, pageUrl: String): Int =
    card.select("a[href]")
        .map { resolveUrl(pageUrl, it.attr("href")).substringBefore('#').substringBefore('?') }
        .filter { hrefPattern.containsMatchIn(it) && it.trimEnd('/') != pageUrl.substringBefore('?').trimEnd('/') }
        .toSet()
        .size

fun buildListingFromCard(
    source: SourceName,
    url: String,
    card: Element,
    address: ParsedAddress?,
    fetchedAtEpochSeconds: Long,
): RawListing {
    val text = card.text()
    return RawListing(
        source = source,
        url = url,
        street = address?.street,
        houseNumber = address?.houseNumber,
        addition = address?.addition.orEmpty(),
        postcode = normalizePostcode(text),
        city = extractCity(text),
        price = parsePrice(text),
        priceOnRequest = isPriceOnRequest(text),
        areaSquareMeters = parseArea(text),
        bedrooms = parseBedrooms(text),
        energyLabel = parseEnergyLabel(findEnergyLabelText(text)),
        status = parseStatus(text) ?: ListingStatus.AVAILABLE,
        fetchedAtEpochSeconds = fetchedAtEpochSeconds,
    )
}

private val energyLabelInTextPattern = Regex("""energielabel\s*:?\s*(A\d|[A-G]\+*)""", RegexOption.IGNORE_CASE)

fun findEnergyLabelText(text: String): String? = energyLabelInTextPattern.find(text)?.groupValues?.get(1)

// ---- detail pages ----

/** Reads "term: value" pairs from definition lists and two-column tables. */
fun readKeyValuePairs(document: Document): Map<String, String> {
    val pairs = LinkedHashMap<String, String>()
    for (term in document.select("dt")) {
        val value = term.nextElementSibling()?.takeIf { it.tagName() == "dd" } ?: continue
        pairs.putIfAbsent(term.text().trim().lowercase().trimEnd(':'), value.text().trim())
    }
    for (row in document.select("tr")) {
        val cells = row.select("th, td")
        if (cells.size >= 2) pairs.putIfAbsent(cells[0].text().trim().lowercase().trimEnd(':'), cells[1].text().trim())
    }
    return pairs
}

private val firstNumberPattern = Regex("""\d+""")

private fun parseFirstNumber(text: String): Int? = firstNumberPattern.find(text)?.value?.toIntOrNull()

/** Finds the first pair whose term contains one of the given words, so small wording changes still match. */
private fun Map<String, String>.valueWhereTermContains(vararg words: String): String? =
    entries.firstOrNull { (term, _) -> words.any { term.contains(it) } }?.value

/** Fills only fields the search page left empty; details never overwrite what is known. */
fun enrichFromPairs(listing: RawListing, pairs: Map<String, String>): RawListing {
    val serviceText = pairs.valueWhereTermContains("servicekosten", "vve")
    return listing.copy(
        bedrooms = listing.bedrooms ?: pairs.valueWhereTermContains("slaapkamer")?.let(::parseFirstNumber),
        energyLabel = listing.energyLabel ?: parseEnergyLabel(pairs.valueWhereTermContains("energielabel")),
        areaSquareMeters = listing.areaSquareMeters ?: pairs.valueWhereTermContains("woonoppervlakte")?.let(::parseArea),
        serviceCostsPerMonth = listing.serviceCostsPerMonth ?: serviceText?.let { parseServiceCosts("servicekosten $it") },
        status = parseStatus(pairs["status"]) ?: listing.status,
    )
}

/** Many sites embed latitude/longitude in JSON-LD or page state; only Dutch coordinates are accepted. */
fun extractEmbeddedCoordinates(html: String): Coordinates? {
    val latitude = latitudePattern.find(html)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
    val longitude = longitudePattern.find(html)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
    val inNetherlands = latitude in 50.5..53.7 && longitude in 3.0..7.3
    return if (inNetherlands) Coordinates(latitude, longitude) else null
}
