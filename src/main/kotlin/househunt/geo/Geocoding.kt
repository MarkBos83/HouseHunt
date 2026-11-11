package househunt.geo

import househunt.model.Coordinates
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

interface Geocoder {
    val serviceName: String
    fun findCoordinates(query: String): Coordinates?
}

/** Shared plain-HTTP helper for the free public map services. */
class JsonHttpClient(private val userAgent: String) {
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    fun getText(url: String): String {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("User-Agent", userAgent)
            .header("Accept", "application/json")
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() in 200..299) { "HTTP ${response.statusCode()} for $url" }
        return response.body()
    }
}

fun encodeQueryValue(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

class PdokGeocoder(private val httpClient: JsonHttpClient) : Geocoder {
    override val serviceName = "pdok"

    override fun findCoordinates(query: String): Coordinates? {
        val url = "https://api.pdok.nl/bzk/locatieserver/search/v3_1/free" +
            "?rows=1&fq=type:adres&q=${encodeQueryValue(query)}"
        return parsePdokResponse(httpClient.getText(url))
    }
}

class NominatimGeocoder(
    private val httpClient: JsonHttpClient,
    private val pauseBetweenRequests: () -> Unit = { Thread.sleep(1100) },
) : Geocoder {
    override val serviceName = "nominatim"

    override fun findCoordinates(query: String): Coordinates? {
        pauseBetweenRequests()
        val url = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1&countrycodes=nl" +
            "&q=${encodeQueryValue(query)}"
        return parseNominatimResponse(httpClient.getText(url))
    }
}

/**
 * Tries each geocoder in order and returns the first plausible answer, with the service that gave it.
 * An answer outside the search area counts as a miss, so the next service gets its turn.
 */
class ChainedGeocoder(
    private val geocoders: List<Geocoder>,
    private val isPlausible: (Coordinates) -> Boolean = { true },
) {
    fun findCoordinates(query: String): Pair<Coordinates, String>? {
        for (geocoder in geocoders) {
            val coordinates = runCatching { geocoder.findCoordinates(query) }.getOrNull()
            if (coordinates != null && isPlausible(coordinates)) return coordinates to geocoder.serviceName
        }
        return null
    }
}
