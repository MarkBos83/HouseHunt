package househunt.store

import househunt.model.EnergyLabel
import househunt.model.SourceName
import househunt.normalize.parseEnergyLabel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

private const val UNKNOWN_ENERGY_LABEL = "unknown"
private val resultsJson = Json { ignoreUnknownKeys = true }

/** One home as the Claude scheduled task found it on a Funda or Pararius search page. */
@Serializable
data class ClaudeResultHome(
    val address: String,
    @SerialName("price_eur") val priceEuros: Int? = null,
    @SerialName("living_area_m2") val livingAreaSquareMeters: Int? = null,
    val bedrooms: Int? = null,
    @SerialName("energy_label") val energyLabel: String? = null,
    val status: String? = null,
    val url: String,
)

/** The task's results file; a source that failed is null. */
@Serializable
data class ClaudeResults(
    val funda: List<ClaudeResultHome>? = null,
    val pararius: List<ClaudeResultHome>? = null,
)

fun readClaudeResults(path: Path): ClaudeResults =
    resultsJson.decodeFromString(ClaudeResults.serializer(), Files.readString(path))

/**
 * Merges the task's results into homes.json with the same rules as Huislijn and Buurtje.
 * Returns a one-line summary for the log.
 */
fun importClaudeResults(results: ClaudeResults, homesFile: HomesFile, today: String): String {
    val successfulSources = findSuccessfulSources(results)
    if (successfulSources.isEmpty()) return "Both sources failed; homes.json is left unchanged."
    val freshEntries = successfulSources.mapValues { (source, homes) -> toHomeEntries(source, homes, today) }
    val entries = homesFile.updateSources(freshEntries, today)
    return summarizeSources(entries, successfulSources.keys, today)
}

fun findSuccessfulSources(results: ClaudeResults): Map<SourceName, List<ClaudeResultHome>> = buildMap {
    results.funda?.let { put(SourceName.FUNDA, it) }
    results.pararius?.let { put(SourceName.PARARIUS, it) }
}

/** Keeps label B or better; a home whose label is not shown is kept as "unknown". */
fun isEnergyLabelKept(text: String?): Boolean {
    val label = parseEnergyLabel(text) ?: return true
    return label.isAtLeast(EnergyLabel.B)
}

fun toHomeEntries(source: SourceName, homes: List<ClaudeResultHome>, today: String): List<HomeEntry> =
    homes.filter { isEnergyLabelKept(it.energyLabel) }
        .distinctBy { it.url }
        .map { toHomeEntry(source, it, today) }

private fun toHomeEntry(source: SourceName, home: ClaudeResultHome, today: String) = HomeEntry(
    source = source.name.lowercase(),
    address = home.address,
    priceEuros = home.priceEuros,
    livingAreaSquareMeters = home.livingAreaSquareMeters,
    bedrooms = home.bedrooms,
    energyLabel = home.energyLabel ?: UNKNOWN_ENERGY_LABEL,
    status = home.status,
    url = home.url,
    firstSeen = today,
    lastSeen = today,
)

private fun summarizeSources(entries: List<HomeEntry>, sources: Set<SourceName>, today: String): String =
    sources.joinToString("; ") { source ->
        val listed = entries.filter { parseSourceName(it.source) == source && !it.noLongerListed }
        "${source.name.lowercase()}: ${listed.size} listed, ${listed.count { it.firstSeen == today }} first seen today"
    }
