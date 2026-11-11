package househunt.normalize

import househunt.model.HomeKeys
import househunt.model.RawListing
import java.text.Normalizer

private val postcodePattern = Regex("""\b([1-9]\d{3})\s?([A-Za-z]{2})\b""")
private val whitespacePattern = Regex("""\s+""")
private val accentPattern = Regex("""\p{InCombiningDiacriticalMarks}+""")
private val nonAlphanumericPattern = Regex("""[^a-z0-9]""")

fun normalizePostcode(text: String?): String? {
    val match = postcodePattern.find(text ?: return null) ?: return null
    return match.groupValues[1] + match.groupValues[2].uppercase()
}

fun normalizeStreet(text: String?): String? {
    val withoutAccents = Normalizer.normalize(text ?: return null, Normalizer.Form.NFD)
        .replace(accentPattern, "")
    val cleaned = withoutAccents.lowercase().replace(whitespacePattern, " ").trim()
    return cleaned.ifEmpty { null }
}

/** "6-B", "6B" and "6 b" all give "b"; the number itself is kept separately. */
fun normalizeHouseNumberAddition(text: String?): String =
    (text ?: "").lowercase().replace(nonAlphanumericPattern, "")

data class ParsedAddress(val street: String, val houseNumber: Int, val addition: String)

private val addressLinePattern = Regex("""^(.*?\S)\s+(\d+)\s*[-\s]?\s*(.*)$""")

/** Splits "Stationsstraat 12-B" into street, number and addition. */
fun parseAddressLine(text: String): ParsedAddress? {
    val match = addressLinePattern.find(text.trim()) ?: return null
    return ParsedAddress(
        street = match.groupValues[1],
        houseNumber = match.groupValues[2].toInt(),
        addition = match.groupValues[3].trim(),
    )
}

fun buildPostcodeKey(listing: RawListing): String? {
    val postcode = normalizePostcode(listing.postcode) ?: return null
    val number = listing.houseNumber ?: return null
    return "pc:$postcode:$number:${normalizeHouseNumberAddition(listing.addition)}"
}

fun buildStreetKey(listing: RawListing): String? {
    val street = normalizeStreet(listing.street) ?: return null
    val number = listing.houseNumber ?: return null
    return "st:$street:$number:${normalizeHouseNumberAddition(listing.addition)}"
}

fun buildHomeKeys(listing: RawListing): HomeKeys =
    HomeKeys(postcodeKey = buildPostcodeKey(listing), streetKey = buildStreetKey(listing))

fun formatAddress(listing: RawListing): String {
    val street = listing.street ?: return listing.url
    val number = listing.houseNumber?.toString().orEmpty()
    val addition = listing.addition.trim()
    val additionPart = if (addition.isEmpty()) "" else if (addition.first().isDigit()) "-$addition" else addition
    return "$street $number$additionPart".trim()
}
