package househunt.run

import househunt.model.SourceName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

@Serializable
data class FilterConfiguration(
    val place: String,
    val maximumPrice: Int,
    val minimumAreaSquareMeters: Int,
    val minimumBedrooms: Int,
    val maximumBikeMinutes: Double,
)

@Serializable
data class BikeConfiguration(
    val speedKilometersPerHour: Double,
    val osrmBaseUrl: String,
    val osrmProfile: String,
)

@Serializable
data class DestinationConfiguration(val name: String, val query: String)

@Serializable
data class DestinationsConfiguration(
    val work: DestinationConfiguration,
    val stations: List<DestinationConfiguration>,
)

@Serializable
data class RequestConfiguration(
    val minimumDelayMillis: Long,
    val maximumDelayMillis: Long,
    val maximumSearchPages: Int,
    val userAgent: String,
)

@Serializable
data class LifecycleConfiguration(val newBadgeHours: Int, val goneRetentionDays: Int)

@Serializable
data class ScheduleConfiguration(
    val timeZone: String,
    val slotHours: List<Int>,
    val windowHours: Int,
)

@Serializable
data class PathConfiguration(
    val databaseFile: String,
    val frontendTemplate: String,
    val outputFile: String,
    val fixtureDirectory: String,
)

/** Results outside this box are treated as wrong matches (PDOK once put a station near Rotterdam). */
@Serializable
data class SearchAreaConfiguration(
    val minimumLatitude: Double,
    val maximumLatitude: Double,
    val minimumLongitude: Double,
    val maximumLongitude: Double,
)

@Serializable
data class AppConfiguration(
    val searchUrls: Map<SourceName, String>,
    val enabledSources: Set<SourceName>,
    val filters: FilterConfiguration,
    val searchArea: SearchAreaConfiguration,
    val bike: BikeConfiguration,
    val destinations: DestinationsConfiguration,
    val requests: RequestConfiguration,
    val lifecycle: LifecycleConfiguration,
    val schedule: ScheduleConfiguration,
    val paths: PathConfiguration,
) {
    init {
        searchUrls.forEach { (source, url) ->
            require(url.startsWith("https://") || url.startsWith("http://")) {
                "Search URL for $source must start with https:// but is: $url"
            }
        }
    }
}

private val configurationJson = Json { ignoreUnknownKeys = true }

fun loadConfiguration(path: Path): AppConfiguration =
    configurationJson.decodeFromString(AppConfiguration.serializer(), Files.readString(path))

/** Secrets and contact details come from the environment, never from files. */
data class EnvironmentSettings(val sitePassword: String, val geoContactEmail: String)

fun readEnvironmentSettings(): EnvironmentSettings = EnvironmentSettings(
    sitePassword = requiredEnvironmentVariable("SITE_PASSWORD"),
    geoContactEmail = requiredEnvironmentVariable("GEO_CONTACT_EMAIL"),
)

private fun requiredEnvironmentVariable(name: String): String =
    System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: error("Environment variable $name is not set")
