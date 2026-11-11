package househunt.run

import househunt.model.EnergyLabel
import househunt.model.RawListing
import househunt.model.StoredListing

/**
 * Detail pages are fetched when a listing is new, when price or status changed,
 * when an earlier detail fetch never succeeded, or for everything in the 08:00 run.
 */
fun shouldFetchDetails(previous: StoredListing?, current: RawListing, refreshAllDetails: Boolean): Boolean {
    if (refreshAllDetails || previous == null) return true
    val before = previous.listing
    return previous.lastDetailFetchEpochSeconds == null ||
        before.price != current.price ||
        before.status != current.status
}

/** Search pages do not show details, so values found earlier are kept. */
fun carryOverKnownDetails(current: RawListing, previous: StoredListing?): RawListing {
    val before = previous?.listing ?: return current
    return current.copy(
        bedrooms = current.bedrooms ?: before.bedrooms,
        energyLabel = current.energyLabel ?: before.energyLabel,
        areaSquareMeters = current.areaSquareMeters ?: before.areaSquareMeters,
        serviceCostsPerMonth = current.serviceCostsPerMonth ?: before.serviceCostsPerMonth,
        coordinates = current.coordinates ?: before.coordinates,
    )
}

/**
 * A home that already fails a filter on what the search page shows will never be displayed,
 * so its detail page is not worth a request. If its price changes, the detail is fetched then.
 */
fun isWorthDetailFetch(listing: RawListing, filters: FilterConfiguration): Boolean {
    val priceTooHigh = listing.price?.let { it > filters.maximumPrice } ?: false
    val areaTooSmall = listing.areaSquareMeters?.let { it < filters.minimumAreaSquareMeters } ?: false
    val tooFewBedrooms = listing.bedrooms?.let { it < filters.minimumBedrooms } ?: false
    val labelTooLow = listing.energyLabel?.let { !it.isAtLeast(EnergyLabel.B) } ?: false
    return !(priceTooHigh || areaTooSmall || tooFewBedrooms || labelTooLow)
}
