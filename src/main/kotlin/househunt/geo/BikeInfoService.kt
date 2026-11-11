package househunt.geo

import househunt.model.BikeInfo
import househunt.model.Coordinates
import househunt.model.HomeBikeInfo
import househunt.model.MergedHome
import househunt.normalize.normalizePostcode
import househunt.run.AppConfiguration
import househunt.run.DestinationConfiguration
import househunt.store.Database

private data class NamedPoint(val name: String, val coordinates: Coordinates)

/** Finds home coordinates and bike distances; every answer is cached so normal runs make almost no requests. */
class BikeInfoService(
    private val database: Database,
    private val geocoder: ChainedGeocoder,
    private val router: BikeRouter,
    private val configuration: AppConfiguration,
) {
    private val workPoint: NamedPoint? by lazy { resolveDestination(configuration.destinations.work) }
    private val stationPoints: List<NamedPoint> by lazy {
        configuration.destinations.stations.mapNotNull { resolveDestination(it) }
    }

    fun findBikeInfo(home: MergedHome): HomeBikeInfo {
        val origin = findHomeCoordinates(home) ?: return HomeBikeInfo(null, null)
        val destinations = listOfNotNull(workPoint) + stationPoints
        val distances = findDistancesWithCache(origin, destinations)
        val speed = configuration.bike.speedKilometersPerHour
        val work = workPoint?.let { distances[it.name] }?.let { buildBikeInfo(it, speed) }
        val stationRoutes = stationPoints.mapNotNull { distances[it.name] }.map { buildBikeInfo(it, speed) }
        return HomeBikeInfo(work, pickShortestRoute(stationRoutes))
    }

    private fun findHomeCoordinates(home: MergedHome): Coordinates? {
        home.coordinates?.let { return it }
        val addressKey = home.keys.postcodeKey ?: home.keys.streetKey ?: return null
        database.findGeocode(addressKey)?.let { return it }
        val (coordinates, service) = geocoder.findCoordinates(buildGeocodeQuery(home)) ?: return null
        database.saveGeocode(addressKey, coordinates, service)
        return coordinates
    }

    private fun buildGeocodeQuery(home: MergedHome): String {
        val postcode = home.listings.firstNotNullOfOrNull { normalizePostcode(it.postcode) }.orEmpty()
        return "${home.address} $postcode ${configuration.filters.place}".replace("  ", " ").trim()
    }

    private fun resolveDestination(destination: DestinationConfiguration): NamedPoint? {
        val cacheKey = "destination:${destination.name}"
        database.findGeocode(cacheKey)?.let { return NamedPoint(destination.name, it) }
        val (coordinates, service) = geocoder.findCoordinates(destination.query) ?: return null
        database.saveGeocode(cacheKey, coordinates, service)
        return NamedPoint(destination.name, coordinates)
    }

    private fun findDistancesWithCache(origin: Coordinates, destinations: List<NamedPoint>): Map<String, Int> {
        val originKey = buildCoordinateKey(origin)
        val cached = destinations.mapNotNull { point ->
            database.findRouteDistance(originKey, point.name)?.let { point.name to it }
        }.toMap()
        val missing = destinations.filter { it.name !in cached }
        if (missing.isEmpty()) return cached
        val fetched = fetchMissingDistances(origin, originKey, missing)
        return cached + fetched
    }

    private fun fetchMissingDistances(origin: Coordinates, originKey: String, missing: List<NamedPoint>): Map<String, Int> {
        val distances = router.findDistances(origin, missing.map { it.coordinates })
        return missing.zip(distances).mapNotNull { (point, distance) ->
            distance?.let {
                database.saveRouteDistance(originKey, point.name, it)
                point.name to it
            }
        }.toMap()
    }
}

fun formatBikeInfoForLog(info: BikeInfo?): String =
    info?.let { "${it.distanceMeters} m / %.1f min".format(java.util.Locale.ROOT, it.minutes) } ?: "-"
