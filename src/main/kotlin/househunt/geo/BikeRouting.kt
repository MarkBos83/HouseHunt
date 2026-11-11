package househunt.geo

import househunt.model.BikeInfo
import househunt.model.Coordinates
import househunt.run.BikeConfiguration
import kotlin.math.roundToInt

/** Minutes are always derived from distance and our own speed; OSRM's duration is ignored. */
fun calculateCyclingMinutes(distanceMeters: Int, speedKilometersPerHour: Double): Double =
    distanceMeters * 60.0 / (speedKilometersPerHour * 1000.0)

fun buildBikeInfo(distanceMeters: Int, speedKilometersPerHour: Double): BikeInfo =
    BikeInfo(distanceMeters, calculateCyclingMinutes(distanceMeters, speedKilometersPerHour))

/** Shortest of several station routes. */
fun pickShortestRoute(routes: List<BikeInfo>): BikeInfo? = routes.minByOrNull { it.distanceMeters }

fun buildCoordinateKey(coordinates: Coordinates): String =
    "%.5f,%.5f".format(java.util.Locale.ROOT, coordinates.latitude, coordinates.longitude)

interface BikeRouter {
    /** Distances in metres to each destination, in the same order; null where no route was found. */
    fun findDistances(origin: Coordinates, destinations: List<Coordinates>): List<Int?>
}

class OsrmBikeRouter(
    private val configuration: BikeConfiguration,
    private val httpClient: JsonHttpClient,
) : BikeRouter {

    override fun findDistances(origin: Coordinates, destinations: List<Coordinates>): List<Int?> {
        val tableDistances = runCatching { fetchTableDistances(origin, destinations) }.getOrNull()
        if (tableDistances != null && tableDistances.size == destinations.size) return tableDistances
        return destinations.map { destination -> runCatching { fetchRouteDistance(origin, destination) }.getOrNull() }
    }

    private fun fetchTableDistances(origin: Coordinates, destinations: List<Coordinates>): List<Int?> {
        val allPoints = listOf(origin) + destinations
        val destinationIndexes = destinations.indices.joinToString(";") { (it + 1).toString() }
        val url = "${configuration.osrmBaseUrl}/table/v1/${configuration.osrmProfile}/${formatPoints(allPoints)}" +
            "?sources=0&destinations=$destinationIndexes&annotations=distance"
        return parseOsrmTableDistances(httpClient.getText(url)).map { it?.roundToInt() }
    }

    private fun fetchRouteDistance(origin: Coordinates, destination: Coordinates): Int? {
        val url = "${configuration.osrmBaseUrl}/route/v1/${configuration.osrmProfile}/" +
            "${formatPoints(listOf(origin, destination))}?overview=false"
        return parseOsrmRouteDistance(httpClient.getText(url))?.roundToInt()
    }

    private fun formatPoints(points: List<Coordinates>): String =
        points.joinToString(";") { "%.6f,%.6f".format(java.util.Locale.ROOT, it.longitude, it.latitude) }
}
