package househunt.geo

import househunt.model.MergedHome
import househunt.store.formatPostcode
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val GOOGLE_MAPS_SEARCH_URL = "https://www.google.com/maps/search/?api=1&query="

/** A Google Maps search for the full address, e.g. "De Beurs 40, 3823 GA Amersfoort". */
fun buildGoogleMapsUrl(home: MergedHome): String {
    val place = listOfNotNull(home.postcode?.let(::formatPostcode), home.city).joinToString(" ")
    val query = listOf(home.address, place).filter { it.isNotBlank() }.joinToString(", ")
    return GOOGLE_MAPS_SEARCH_URL + URLEncoder.encode(query, StandardCharsets.UTF_8)
}
