package househunt.run

import househunt.model.PublishedData
import househunt.model.PublishedSourceStatus
import househunt.model.SourceName
import househunt.model.SourceRunResult
import househunt.source.ListingSource
import househunt.store.Database
import househunt.store.HomeEntry
import househunt.store.HomesFile
import househunt.store.parseSourceName
import househunt.store.toHomeEntry
import java.time.Instant
import java.time.ZoneId

/**
 * One run: collect Huislijn and Buurtje, write them into homes.json next to the Funda and Pararius
 * entries from the Claude task, then build the site data from everything in homes.json.
 */
class RunPipeline(
    private val configuration: AppConfiguration,
    private val database: Database,
    private val sources: List<ListingSource>,
    private val homesFile: HomesFile,
    private val homeBuilder: PublishedHomeBuilder,
) {
    private val collector = SourceCollector(database, configuration.filters)

    fun execute(slot: RunSlot?, nowEpochSeconds: Long): PublishedData {
        val baselineRun = database.findLatestSuccessPerSource().isEmpty()
        val results = collectAllSources(slot, nowEpochSeconds)
        val entries = writeHomesFile(results, nowEpochSeconds)
        return buildPublishedData(entries, nowEpochSeconds, baselineRun)
    }

    /** Rebuilds the site from homes.json as it is, without visiting any website (used after the Claude task pushed). */
    fun publishFromHomesFile(nowEpochSeconds: Long): PublishedData =
        buildPublishedData(homesFile.readEntries(), nowEpochSeconds, baselineRun = false)

    private fun buildPublishedData(entries: List<HomeEntry>, nowEpochSeconds: Long, baselineRun: Boolean): PublishedData =
        PublishedData(
            updatedAtEpochSeconds = nowEpochSeconds,
            newBadgeHours = configuration.lifecycle.newBadgeHours,
            sources = readSourceStatuses(entries),
            homes = homeBuilder.build(entries, nowEpochSeconds, baselineRun),
        )

    private fun collectAllSources(slot: RunSlot?, nowEpochSeconds: Long): List<SourceRunResult> {
        val refreshAllDetails = slot?.hour == configuration.schedule.slotHours.minOrNull()
        return sources.map { source ->
            collector.collect(source, nowEpochSeconds, refreshAllDetails)
                .also { database.recordSourceRun(nowEpochSeconds, slot, it) }
        }
    }

    /** A failed source keeps its previous entries untouched, so a broken scraper never makes homes disappear. */
    private fun writeHomesFile(results: List<SourceRunResult>, nowEpochSeconds: Long): List<HomeEntry> {
        val today = formatToday(nowEpochSeconds)
        val freshEntriesPerSource = results.filter { it.success }.associate { result ->
            result.source to findListingsSeenNow(result.source, nowEpochSeconds).map { toHomeEntry(it, today) }
        }
        return homesFile.updateSources(freshEntriesPerSource, today)
    }

    private fun findListingsSeenNow(source: SourceName, nowEpochSeconds: Long) =
        database.findAllListings()
            .filter { it.listing.source == source && it.lastSeenEpochSeconds >= nowEpochSeconds }
            .map { it.listing }

    private fun formatToday(nowEpochSeconds: Long): String =
        Instant.ofEpochSecond(nowEpochSeconds).atZone(ZoneId.of(configuration.schedule.timeZone)).toLocalDate().toString()

    private fun readSourceStatuses(entries: List<HomeEntry>): List<PublishedSourceStatus> =
        (readFileSourceStatuses(entries) + readScrapedSourceStatuses()).sortedBy { parseSourceName(it.name)?.priority }

    /** Huislijn and Buurtje: the result of their latest run. */
    private fun readScrapedSourceStatuses(): List<PublishedSourceStatus> =
        database.findLatestRunPerSource().values
            .filter { it.source in configuration.enabledSources }
            .map { PublishedSourceStatus(it.source.name, it.success, it.listingCount, it.error) }

    /** Funda and Pararius: how many homes the Claude task's entries in homes.json still list. */
    private fun readFileSourceStatuses(entries: List<HomeEntry>): List<PublishedSourceStatus> =
        SourceName.entries.filter { it !in configuration.enabledSources }.map { source ->
            val listedCount = entries.count { parseSourceName(it.source) == source && !it.noLongerListed }
            val error = if (listedCount == 0) "no listings in homes.json" else null
            PublishedSourceStatus(source.name, listedCount > 0, listedCount, error)
        }
}
