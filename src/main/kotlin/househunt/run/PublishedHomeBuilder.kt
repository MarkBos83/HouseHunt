package househunt.run

import househunt.filter.passesBasicFilters
import househunt.filter.passesBikeFilter
import househunt.geo.BikeInfoService
import househunt.geo.buildGoogleMapsUrl
import househunt.lifecycle.isWithinGoneRetention
import househunt.lifecycle.updateGoneSince
import househunt.merge.groupListingsByKeys
import househunt.merge.mergeListings
import househunt.model.BikeInfo
import househunt.model.MergedHome
import househunt.model.PublishedHome
import househunt.store.Database
import househunt.store.HomeEntry
import househunt.store.StoredHome
import househunt.store.toRawListing
import kotlin.math.roundToInt

private data class HomeCandidate(
    val merged: MergedHome,
    val present: Boolean,
)

/** Turns every entry in homes.json into the list of homes the website shows. */
class PublishedHomeBuilder(
    private val database: Database,
    private val bikeInfoService: BikeInfoService,
    private val configuration: AppConfiguration,
) {

    fun build(entries: List<HomeEntry>, nowEpochSeconds: Long, baselineRun: Boolean): List<PublishedHome> =
        findCandidates(entries)
            .mapNotNull { candidate -> buildPublishedHome(candidate, nowEpochSeconds, baselineRun) }
            .sortedBy { it.price ?: Int.MAX_VALUE }

    /** One candidate per home; a home is present while at least one source still lists it. */
    private fun findCandidates(entries: List<HomeEntry>): List<HomeCandidate> {
        val listedUrls = entries.filterNot { it.noLongerListed }.map { it.url }.toSet()
        val listings = entries.mapNotNull { toRawListing(it) }
        return groupListingsByKeys(listings).map { group ->
            val presentListings = group.filter { it.url in listedUrls }
            HomeCandidate(mergeListings(presentListings.ifEmpty { group }), presentListings.isNotEmpty())
        }
    }

    private fun buildPublishedHome(candidate: HomeCandidate, nowEpochSeconds: Long, baselineRun: Boolean): PublishedHome? {
        val merged = candidate.merged
        val stored = saveHomeState(candidate, nowEpochSeconds, baselineRun)
        val visible = isWithinGoneRetention(stored.goneSinceEpochSeconds, nowEpochSeconds, configuration.lifecycle.goneRetentionDays)
        if (!visible || !passesBasicFilters(merged, configuration.filters)) return null
        val bikeInfo = bikeInfoService.findBikeInfo(merged)
        if (!passesBikeFilter(bikeInfo.work, configuration.filters)) return null
        return toPublishedHome(stored, merged, bikeInfo.work, bikeInfo.station)
    }

    private fun saveHomeState(candidate: HomeCandidate, nowEpochSeconds: Long, baselineRun: Boolean): StoredHome {
        val merged = candidate.merged
        val existing = database.findHomeByKeys(merged.keys.all())
        if (existing == null) {
            val goneSince = updateGoneSince(null, candidate.present, nowEpochSeconds)
            val home = StoredHome(
                id = 0, postcodeKey = merged.keys.postcodeKey, streetKey = merged.keys.streetKey,
                canonicalAddress = merged.address, firstSeenEpochSeconds = nowEpochSeconds,
                lastSeenEpochSeconds = nowEpochSeconds, goneSinceEpochSeconds = goneSince, baseline = baselineRun,
            )
            return home.copy(id = database.insertHome(home))
        }
        val updated = existing.copy(
            postcodeKey = merged.keys.postcodeKey ?: existing.postcodeKey,
            streetKey = merged.keys.streetKey ?: existing.streetKey,
            canonicalAddress = merged.address,
            lastSeenEpochSeconds = if (candidate.present) nowEpochSeconds else existing.lastSeenEpochSeconds,
            goneSinceEpochSeconds = updateGoneSince(existing.goneSinceEpochSeconds, candidate.present, nowEpochSeconds),
        )
        database.updateHome(updated)
        return updated
    }

    /** The merged listings are already sorted by source priority. */
    private fun listSources(merged: MergedHome): List<String> =
        merged.listings.map { it.source.name }.distinct()

    private fun toPublishedHome(stored: StoredHome, merged: MergedHome, work: BikeInfo?, station: BikeInfo?) = PublishedHome(
        key = "h${stored.id}",
        sources = listSources(merged),
        address = merged.address,
        url = merged.url,
        googleMapsUrl = buildGoogleMapsUrl(merged),
        price = merged.price,
        serviceCostsPerMonth = merged.serviceCostsPerMonth,
        areaSquareMeters = merged.areaSquareMeters,
        bedrooms = merged.bedrooms,
        energyLabel = merged.energyLabel?.display,
        status = merged.status?.name,
        bikeToWorkMeters = work?.distanceMeters,
        bikeToWorkMinutes = work?.minutes?.roundToInt(),
        bikeToStationMeters = station?.distanceMeters,
        bikeToStationMinutes = station?.minutes?.roundToInt(),
        firstSeenEpochSeconds = stored.firstSeenEpochSeconds,
        baseline = stored.baseline,
        goneSinceEpochSeconds = stored.goneSinceEpochSeconds,
    )
}
