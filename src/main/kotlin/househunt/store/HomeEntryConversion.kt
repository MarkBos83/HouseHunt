package househunt.store

import househunt.model.Coordinates
import househunt.model.ListingStatus
import househunt.model.RawListing
import househunt.model.SourceName
import househunt.normalize.formatAddress
import househunt.normalize.normalizePostcode
import househunt.normalize.parseAddressLine
import househunt.normalize.parseEnergyLabel
import househunt.source.extractCity

private val postcodeInTextPattern = Regex("""\b[1-9]\d{3}\s?[A-Za-z]{2}\b""")

// ---- listing to entry ----

fun toHomeEntry(listing: RawListing, today: String): HomeEntry = HomeEntry(
    source = listing.source.name.lowercase(),
    address = formatFullAddress(listing),
    priceEuros = listing.price,
    livingAreaSquareMeters = listing.areaSquareMeters,
    bedrooms = listing.bedrooms,
    energyLabel = listing.energyLabel?.display,
    status = listing.status?.let(::formatStatusText),
    url = listing.url,
    serviceCostsEuros = listing.serviceCostsPerMonth,
    latitude = listing.coordinates?.latitude,
    longitude = listing.coordinates?.longitude,
    firstSeen = today,
    lastSeen = today,
)

/** "Stationsstraat 12B, 3811 AB Amersfoort", the same shape the Claude task writes. */
fun formatFullAddress(listing: RawListing): String {
    val place = listOfNotNull(normalizePostcode(listing.postcode)?.let(::formatPostcode), listing.city)
        .joinToString(" ")
    return listOf(formatAddress(listing), place).filter { it.isNotBlank() }.joinToString(", ")
}

/** "3811AB" becomes "3811 AB". */
fun formatPostcode(normalizedPostcode: String): String =
    "${normalizedPostcode.take(4)} ${normalizedPostcode.drop(4)}"

fun formatStatusText(status: ListingStatus): String = when (status) {
    ListingStatus.AVAILABLE -> "available"
    ListingStatus.UNDER_OFFER -> "under offer"
    ListingStatus.SOLD_CONDITIONS_PENDING -> "sold subject to conditions"
    ListingStatus.SOLD -> "sold"
}

// ---- entry to listing ----

/** Entries from a source this program does not know are skipped. */
fun toRawListing(entry: HomeEntry): RawListing? {
    val source = parseSourceName(entry.source) ?: return null
    val addressLine = findAddressLine(entry.address)
    val parsedAddress = parseAddressLine(addressLine)
    return RawListing(
        source = source,
        url = entry.url,
        street = parsedAddress?.street,
        houseNumber = parsedAddress?.houseNumber,
        addition = parsedAddress?.addition.orEmpty(),
        postcode = normalizePostcode(entry.address),
        city = findCity(entry.address),
        price = entry.priceEuros,
        areaSquareMeters = entry.livingAreaSquareMeters,
        bedrooms = entry.bedrooms,
        energyLabel = parseEnergyLabel(entry.energyLabel),
        status = parseStatusText(entry.status),
        serviceCostsPerMonth = entry.serviceCostsEuros,
        coordinates = readCoordinates(entry),
    )
}

fun parseSourceName(text: String): SourceName? =
    SourceName.entries.firstOrNull { it.name.equals(text.trim(), ignoreCase = true) }

/** The part before the first comma, or before the postcode when there is no comma. */
private fun findAddressLine(address: String): String {
    if (',' in address) return address.substringBefore(',').trim()
    val postcode = postcodeInTextPattern.find(address) ?: return address.trim()
    return address.substring(0, postcode.range.first).trim()
}

private fun findCity(address: String): String? {
    extractCity(address)?.let { return it }
    return address.substringAfter(',', "").trim().ifEmpty { null }
}

/** Understands the English words the Claude task writes and the Dutch words the sites use. */
fun parseStatusText(text: String?): ListingStatus? {
    val lowered = text?.lowercase() ?: return null
    return when {
        "conditions" in lowered || "voorbehoud" in lowered -> ListingStatus.SOLD_CONDITIONS_PENDING
        "under offer" in lowered || "under option" in lowered -> ListingStatus.UNDER_OFFER
        "onder bod" in lowered || "onder optie" in lowered -> ListingStatus.UNDER_OFFER
        "sold" in lowered || "verkocht" in lowered -> ListingStatus.SOLD
        "available" in lowered || "beschikbaar" in lowered -> ListingStatus.AVAILABLE
        else -> null
    }
}

private fun readCoordinates(entry: HomeEntry): Coordinates? {
    val latitude = entry.latitude ?: return null
    val longitude = entry.longitude ?: return null
    return Coordinates(latitude, longitude)
}
