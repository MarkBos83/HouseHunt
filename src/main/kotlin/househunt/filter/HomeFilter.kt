package househunt.filter

import househunt.model.BikeInfo
import househunt.model.EnergyLabel
import househunt.model.ListingStatus
import househunt.model.MergedHome
import househunt.run.FilterConfiguration

private val minimumEnergyLabel = EnergyLabel.B

fun passesPlaceFilter(home: MergedHome, filters: FilterConfiguration): Boolean =
    home.city == null || home.city.contains(filters.place, ignoreCase = true)

fun passesPriceFilter(home: MergedHome, filters: FilterConfiguration): Boolean {
    val price = home.price ?: return false
    return price <= filters.maximumPrice
}

fun passesAreaFilter(home: MergedHome, filters: FilterConfiguration): Boolean {
    val area = home.areaSquareMeters ?: return false
    return area >= filters.minimumAreaSquareMeters
}

fun passesBedroomFilter(home: MergedHome, filters: FilterConfiguration): Boolean {
    val bedrooms = home.bedrooms ?: return true
    return bedrooms >= filters.minimumBedrooms
}

fun passesEnergyLabelFilter(home: MergedHome): Boolean {
    val label = home.energyLabel ?: return true
    return label.isAtLeast(minimumEnergyLabel)
}

fun passesStatusFilter(home: MergedHome): Boolean = home.status != ListingStatus.SOLD

/** A missing route never hides a home; only a known, too-long route does. */
fun passesBikeFilter(workRoute: BikeInfo?, filters: FilterConfiguration): Boolean {
    val route = workRoute ?: return true
    return route.minutes <= filters.maximumBikeMinutes
}

/** Everything that can be decided without any geocoding or routing. */
fun passesBasicFilters(home: MergedHome, filters: FilterConfiguration): Boolean =
    passesPlaceFilter(home, filters) &&
        passesPriceFilter(home, filters) &&
        passesAreaFilter(home, filters) &&
        passesBedroomFilter(home, filters) &&
        passesEnergyLabelFilter(home) &&
        passesStatusFilter(home)
