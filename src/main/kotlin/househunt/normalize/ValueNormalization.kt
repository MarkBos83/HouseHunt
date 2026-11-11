package househunt.normalize

import househunt.model.EnergyLabel
import househunt.model.ListingStatus

private val euroAmountPattern = Regex("""€\s*([\d.\s]+)""")
private val areaPattern = Regex("""(\d[\d.]*)\s*m(?:²|2)""")
private val bedroomsPattern = Regex("""(\d+)\s*(?:slaapkamers?|slaapk\.?)""", RegexOption.IGNORE_CASE)
private val serviceCostsPattern = Regex(
    """(?:servicekosten|vve-bijdrage|bijdrage vve|service costs)[^€\d]{0,40}€?\s*([\d.,]+)""",
    RegexOption.IGNORE_CASE,
)
private val plusLabelPattern = Regex("""^([A-G])(\++)?$""")
private val numberedLabelPattern = Regex("""^A([1-5])$""")

fun parseWholeNumber(text: String): Int? {
    val digits = text.filter { it.isDigit() }
    return digits.toIntOrNull()
}

fun isPriceOnRequest(text: String): Boolean = text.contains("prijs op aanvraag", ignoreCase = true)

/** "€ 350.000 k.k." and "€ 350.000 v.o.n." both give 350000. */
fun parsePrice(text: String): Int? {
    if (isPriceOnRequest(text)) return null
    val match = euroAmountPattern.find(text) ?: return null
    return parseWholeNumber(match.groupValues[1])
}

fun parseArea(text: String): Int? {
    val match = areaPattern.find(text) ?: return null
    return parseWholeNumber(match.groupValues[1])
}

fun parseBedrooms(text: String): Int? {
    val match = bedroomsPattern.find(text) ?: return null
    return match.groupValues[1].toIntOrNull()
}

/** Whole euros per month; a decimal part after a comma is dropped. */
fun parseServiceCosts(text: String): Int? {
    val match = serviceCostsPattern.find(text) ?: return null
    val amount = match.groupValues[1].substringBefore(',')
    return parseWholeNumber(amount)
}

fun parseEnergyLabel(text: String?): EnergyLabel? {
    val cleaned = text?.trim()?.uppercase()?.replace(" ", "") ?: return null
    numberedLabelPattern.matchEntire(cleaned)?.let { return labelForPlusCount(it.groupValues[1].toInt()) }
    val match = plusLabelPattern.matchEntire(cleaned) ?: return null
    return if (match.groupValues[1] == "A") labelForPlusCount(match.groupValues[2].length) else letterLabel(match.groupValues[1])
}

private fun labelForPlusCount(plusCount: Int): EnergyLabel? = when (plusCount) {
    0 -> EnergyLabel.A
    1 -> EnergyLabel.A_PLUS_1
    2 -> EnergyLabel.A_PLUS_2
    3 -> EnergyLabel.A_PLUS_3
    4 -> EnergyLabel.A_PLUS_4
    5 -> EnergyLabel.A_PLUS_5
    else -> null
}

private fun letterLabel(letter: String): EnergyLabel? = EnergyLabel.entries.firstOrNull { it.display == letter }

/** Checks the most specific Dutch phrase first. */
fun parseStatus(text: String?): ListingStatus? {
    val lowered = text?.lowercase() ?: return null
    return when {
        "verkocht onder voorbehoud" in lowered -> ListingStatus.SOLD_CONDITIONS_PENDING
        "onder bod" in lowered || "onder optie" in lowered -> ListingStatus.UNDER_OFFER
        "verkocht" in lowered -> ListingStatus.SOLD
        "beschikbaar" in lowered -> ListingStatus.AVAILABLE
        else -> null
    }
}
