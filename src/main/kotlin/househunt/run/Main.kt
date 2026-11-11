package househunt.run

import househunt.geo.BikeInfoService
import househunt.geo.ChainedGeocoder
import househunt.geo.JsonHttpClient
import househunt.geo.NominatimGeocoder
import househunt.geo.OsrmBikeRouter
import househunt.geo.PdokGeocoder
import househunt.publish.publishSite
import househunt.source.BrowserPageFetcher
import househunt.source.HttpPageFetcher
import househunt.source.RequestPacer
import househunt.source.SourceFetchers
import househunt.source.createSources
import househunt.store.Database
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.system.exitProcess

private val configurationPath: Path = Path.of(System.getenv("HOUSEHUNT_CONFIG") ?: "config/config.json")

fun main(arguments: Array<String>) {
    when (arguments.firstOrNull()) {
        "guard" -> runGuardCommand()
        "run" -> runCollectionCommand(forced = "--force" in arguments)
        "probe" -> runPhaseZeroProbe(loadConfiguration(configurationPath), readEnvironmentSettings())
        else -> {
            System.err.println("Usage: guard | run [--force] | probe")
            exitProcess(2)
        }
    }
}

/** Tells the workflow whether this trigger falls in a slot that has not run yet. */
private fun runGuardCommand() {
    val configuration = loadConfiguration(configurationPath)
    val slot = Database(Path.of(configuration.paths.databaseFile)).use { database ->
        val today = Instant.now().atZone(java.time.ZoneId.of(configuration.schedule.timeZone)).toLocalDate()
        determineSlotToRun(Instant.now(), configuration.schedule, database.findSlotsRunOn(today))
    }
    reportGuardResult(slot)
}

private fun reportGuardResult(slot: RunSlot?) {
    val shouldRun = slot != null
    println(if (shouldRun) "Running slot ${slot?.label}" else "Skipping: no open slot right now")
    System.getenv("GITHUB_OUTPUT")?.let { outputFile ->
        Files.writeString(Path.of(outputFile), "run=$shouldRun\n", java.nio.file.StandardOpenOption.APPEND)
    }
}

private fun runCollectionCommand(forced: Boolean) {
    val configuration = loadConfiguration(configurationPath)
    val environment = readEnvironmentSettings()
    Database(Path.of(configuration.paths.databaseFile)).use { database ->
        val now = Instant.now()
        val slot = if (forced) null else findSlotOrExit(configuration, database, now)
        val data = createFetchers(configuration).use { fetchers ->
            buildPipeline(configuration, environment, database, fetchers).execute(slot, now.epochSecond)
        }
        publishSite(data, environment.sitePassword, Path.of(configuration.paths.frontendTemplate), Path.of(configuration.paths.outputFile))
        println("Published ${data.homes.size} homes; sources: ${data.sources.joinToString { "${it.name}=${if (it.ok) "ok" else "FAILED (${it.error})"}" }}")
    }
}

private fun findSlotOrExit(configuration: AppConfiguration, database: Database, now: Instant): RunSlot {
    val today = now.atZone(java.time.ZoneId.of(configuration.schedule.timeZone)).toLocalDate()
    val slot = determineSlotToRun(now, configuration.schedule, database.findSlotsRunOn(today))
    if (slot == null) {
        println("No open slot; nothing to do.")
        exitProcess(0)
    }
    return slot
}

fun createFetchers(configuration: AppConfiguration): SourceFetchers {
    val pacer = RequestPacer(configuration.requests)
    return SourceFetchers(
        http = HttpPageFetcher(configuration.requests, pacer),
        browserProvider = { BrowserPageFetcher(configuration.requests, pacer, configuration.schedule.timeZone) },
    )
}

fun buildPipeline(
    configuration: AppConfiguration,
    environment: EnvironmentSettings,
    database: Database,
    fetchers: SourceFetchers,
): RunPipeline {
    val sources = createSources(configuration, fetchers) { Instant.now().epochSecond }
    val bikeInfoService = createBikeInfoService(configuration, environment, database)
    return RunPipeline(configuration, database, sources, PublishedHomeBuilder(database, bikeInfoService, configuration))
}

fun createBikeInfoService(
    configuration: AppConfiguration,
    environment: EnvironmentSettings,
    database: Database,
): BikeInfoService {
    val userAgent = "househunt-personal-tracker (${environment.geoContactEmail})"
    val httpClient = JsonHttpClient(userAgent)
    val area = configuration.searchArea
    val geocoder = ChainedGeocoder(listOf(PdokGeocoder(httpClient), NominatimGeocoder(httpClient))) {
        it.latitude in area.minimumLatitude..area.maximumLatitude &&
            it.longitude in area.minimumLongitude..area.maximumLongitude
    }
    return BikeInfoService(database, geocoder, OsrmBikeRouter(configuration.bike, httpClient), configuration)
}
