package househunt.run

import househunt.model.RawListing
import househunt.model.SourceRunResult
import househunt.source.ListingSource
import househunt.store.Database

private const val PROGRESS_EVERY = 10

class SourceCollector(
    private val database: Database,
    private val filters: FilterConfiguration,
    private val report: (String) -> Unit = ::println,
) {

    /** Never throws: one broken site is recorded as failed and the others carry on. */
    fun collect(
        source: ListingSource,
        nowEpochSeconds: Long,
        refreshAllDetails: Boolean,
    ): SourceRunResult = try {
        report("[${source.sourceName}] reading search pages...")
        val searchListings = source.fetchSearchListings()
        report("[${source.sourceName}] found ${searchListings.size} listings")
        if (searchListings.isEmpty()) {
            SourceRunResult(source.sourceName, false, 0, "returned 0 listings")
        } else {
            saveAll(source, searchListings, nowEpochSeconds, refreshAllDetails)
            SourceRunResult(source.sourceName, true, searchListings.size)
        }
    } catch (exception: Exception) {
        report("[${source.sourceName}] FAILED: ${exception.message}")
        SourceRunResult(source.sourceName, false, 0, exception.message ?: exception.javaClass.simpleName)
    }

    private fun saveAll(
        source: ListingSource,
        searchListings: List<RawListing>,
        nowEpochSeconds: Long,
        refreshAllDetails: Boolean,
    ) {
        searchListings.forEachIndexed { index, listing ->
            saveWithDetails(source, listing, nowEpochSeconds, refreshAllDetails)
            val done = index + 1
            if (done % PROGRESS_EVERY == 0 || done == searchListings.size) {
                report("[${source.sourceName}] processed $done/${searchListings.size}")
            }
        }
    }

    private fun saveWithDetails(
        source: ListingSource,
        searchListing: RawListing,
        nowEpochSeconds: Long,
        refreshAllDetails: Boolean,
    ) {
        val previous = database.findListing(searchListing.source, searchListing.url)
        val withKnownDetails = carryOverKnownDetails(searchListing, previous)
        val fetchDetails = shouldFetchDetails(previous, searchListing, refreshAllDetails) &&
            isWorthDetailFetch(withKnownDetails, filters)
        val detailed = if (fetchDetails) runCatching { source.fetchDetails(withKnownDetails) }.getOrNull() else null
        database.saveListing(detailed ?: withKnownDetails, nowEpochSeconds, detailFetched = detailed != null)
    }
}
