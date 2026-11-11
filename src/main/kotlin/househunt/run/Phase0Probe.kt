package househunt.run

import househunt.geo.JsonHttpClient
import househunt.geo.NominatimGeocoder
import househunt.geo.PdokGeocoder
import househunt.geo.encodeQueryValue
import househunt.geo.parseOsrmRouteDistance
import househunt.geo.parseOsrmTableDistances
import househunt.model.Coordinates
import househunt.model.RawListing
import househunt.source.ListingSource
import househunt.source.PagedListingSource
import househunt.source.PageFetcher
import househunt.source.createSources
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * Phase 0: throwaway checks of every source and map service.
 * Saves the real HTML as test fixtures and prints what the parsers made of it.
 * Run it from your own PC and from a GitHub Actions runner, then compare.
 */
fun runPhaseZeroProbe(configuration: AppConfiguration, environment: EnvironmentSettings) {
    val fixtureDirectory = Path.of(configuration.paths.fixtureDirectory).also { Files.createDirectories(it) }
    createFetchers(configuration).use { fetchers ->
        val sources = createSources(configuration, fetchers, onlyEnabled = false) { Instant.now().epochSecond }
        sources.forEach { source ->
            probeSource(source, "http", fetchers.http, configuration, fixtureDirectory)
            probeSource(source, "browser", fetchers.browser, configuration, fixtureDirectory)
        }
    }
    probeMapServices(configuration, environment)
}

private fun probeSource(
    source: ListingSource,
    mode: String,
    fetcher: PageFetcher,
    configuration: AppConfiguration,
    fixtureDirectory: Path,
) {
    val label = "${source.sourceName}/$mode"
    val searchUrl = configuration.searchUrls.getValue(source.sourceName)
    val paged = source as? PagedListingSource ?: return
    runCatching {
        val html = fetcher.fetchHtml(searchUrl)
        saveFixture(fixtureDirectory, "${source.sourceName.name.lowercase()}-$mode-search.html", html)
        val listings = paged.parseSearchPage(html, searchUrl, Instant.now().epochSecond)
        println("[$label] search page: ${html.length} chars, ${listings.size} listings parsed")
        listings.take(3).forEach { println("    $it") }
        listings.firstOrNull()?.let { probeDetail(paged, it, mode, fetcher, fixtureDirectory, label) }
        if (mode == "http") probeSecondPage(paged, listings, fetcher, fixtureDirectory)
    }.onFailure { println("[$label] FAILED: ${it.message}") }
}

private fun probeSecondPage(
    source: PagedListingSource,
    firstPageListings: List<RawListing>,
    fetcher: PageFetcher,
    fixtureDirectory: Path,
) {
    val label = "${source.sourceName}/http page 2"
    runCatching {
        val url = source.pageUrl(2)
        val html = fetcher.fetchHtml(url)
        saveFixture(fixtureDirectory, "${source.sourceName.name.lowercase()}-http-search-page2.html", html)
        val listings = source.parseSearchPage(html, url, Instant.now().epochSecond)
        val newOnes = listings.filter { listing -> firstPageListings.none { it.url == listing.url } }
        println("[$label] $url -> ${listings.size} listings, ${newOnes.size} not on page 1")
    }.onFailure { println("[$label] FAILED: ${it.message}") }
}

private fun probeDetail(
    source: PagedListingSource,
    listing: RawListing,
    mode: String,
    fetcher: PageFetcher,
    fixtureDirectory: Path,
    label: String,
) {
    val html = fetcher.fetchHtml(listing.url)
    saveFixture(fixtureDirectory, "${source.sourceName.name.lowercase()}-$mode-detail.html", html)
    println("[$label] detail page: ${html.length} chars -> ${source.parseDetailPage(html, listing)}")
}

private fun saveFixture(directory: Path, fileName: String, html: String) {
    Files.writeString(directory.resolve(fileName), html)
}

private fun probeMapServices(configuration: AppConfiguration, environment: EnvironmentSettings) {
    val httpClient = JsonHttpClient("househunt-personal-tracker (${environment.geoContactEmail})")
    val pdok = PdokGeocoder(httpClient)
    val nominatim = NominatimGeocoder(httpClient)
    val destinations = listOf(configuration.destinations.work) + configuration.destinations.stations
    val found = destinations.map { destination ->
        val fromPdok = runCatching { pdok.findCoordinates(destination.query) }.getOrNull()
        val fromNominatim = runCatching { nominatim.findCoordinates(destination.query) }.getOrNull()
        println("[geo] ${destination.name}: pdok=$fromPdok nominatim=$fromNominatim")
        listOfNotNull(fromPdok, fromNominatim).firstOrNull { isInSearchArea(it, configuration.searchArea) }
    }
    probeRouting(configuration, found.filterNotNull(), httpClient)
}

private fun isInSearchArea(point: Coordinates, area: SearchAreaConfiguration): Boolean =
    point.latitude in area.minimumLatitude..area.maximumLatitude &&
        point.longitude in area.minimumLongitude..area.maximumLongitude

private fun probeRouting(configuration: AppConfiguration, points: List<Coordinates>, httpClient: JsonHttpClient) {
    if (points.size < 2) return println("[osrm] not enough resolved points to test routing")
    val base = "${configuration.bike.osrmBaseUrl}"
    val profile = configuration.bike.osrmProfile
    val coordinateText = points.joinToString(";") { "${it.longitude},${it.latitude}" }
    val destinationIndexes = (1 until points.size).joinToString(";")
    runCatching {
        val table = httpClient.getText("$base/table/v1/$profile/$coordinateText?sources=0&destinations=$destinationIndexes&annotations=distance")
        println("[osrm] table distances: ${parseOsrmTableDistances(table)}")
    }.onFailure { println("[osrm] table FAILED: ${it.message} (use 4 route calls)") }
    runCatching {
        val route = httpClient.getText("$base/route/v1/$profile/${points[0].longitude},${points[0].latitude};${points[1].longitude},${points[1].latitude}?overview=false")
        println("[osrm] route distance: ${parseOsrmRouteDistance(route)}")
    }.onFailure { println("[osrm] route FAILED: ${it.message}") }
    println("[osrm] query example: ${encodeQueryValue("a b")}")
}
