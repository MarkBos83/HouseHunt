package househunt.run

import househunt.model.PublishedData
import househunt.model.PublishedSourceStatus
import househunt.source.ListingSource
import househunt.store.Database

/** One scheduled run: collect from every source, update the database, and build the data for the site. */
class RunPipeline(
    private val configuration: AppConfiguration,
    private val database: Database,
    private val sources: List<ListingSource>,
    private val homeBuilder: PublishedHomeBuilder,
) {
    private val collector = SourceCollector(database, configuration.filters)

    fun execute(slot: RunSlot?, nowEpochSeconds: Long): PublishedData {
        val baselineRun = database.findLatestSuccessPerSource().isEmpty()
        val refreshAllDetails = slot?.hour == configuration.schedule.slotHours.minOrNull()
        sources.forEach { source ->
            val result = collector.collect(source, nowEpochSeconds, refreshAllDetails)
            database.recordSourceRun(nowEpochSeconds, slot, result)
        }
        return PublishedData(
            updatedAtEpochSeconds = nowEpochSeconds,
            newBadgeHours = configuration.lifecycle.newBadgeHours,
            sources = readSourceStatuses(),
            homes = homeBuilder.build(nowEpochSeconds, baselineRun),
        )
    }

    private fun readSourceStatuses(): List<PublishedSourceStatus> =
        database.findLatestRunPerSource().values
            .filter { it.source in configuration.enabledSources }
            .sortedBy { it.source.priority }
            .map { PublishedSourceStatus(it.source.name, it.success, it.listingCount, it.error) }
}
