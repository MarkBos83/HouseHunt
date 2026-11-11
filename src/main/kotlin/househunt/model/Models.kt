package househunt.model

import kotlinx.serialization.Serializable

@Serializable
enum class SourceName(val priority: Int) {
    FUNDA(0),
    PARARIUS(1),
    HUISLIJN(2),
    BUURTJE(3),
}

@Serializable
enum class ListingStatus {
    AVAILABLE,
    UNDER_OFFER,
    SOLD_CONDITIONS_PENDING,
    SOLD,
}

/** Ordered best first, so a lower ordinal is a better label. */
@Serializable
enum class EnergyLabel(val display: String) {
    A_PLUS_5("A+++++"),
    A_PLUS_4("A++++"),
    A_PLUS_3("A+++"),
    A_PLUS_2("A++"),
    A_PLUS_1("A+"),
    A("A"),
    B("B"),
    C("C"),
    D("D"),
    E("E"),
    F("F"),
    G("G");

    fun isAtLeast(minimum: EnergyLabel): Boolean = ordinal <= minimum.ordinal
}

@Serializable
data class Coordinates(val latitude: Double, val longitude: Double)

@Serializable
data class RawListing(
    val source: SourceName,
    val url: String,
    val street: String? = null,
    val houseNumber: Int? = null,
    val addition: String = "",
    val postcode: String? = null,
    val city: String? = null,
    val price: Int? = null,
    val priceOnRequest: Boolean = false,
    val areaSquareMeters: Int? = null,
    val bedrooms: Int? = null,
    val energyLabel: EnergyLabel? = null,
    val status: ListingStatus? = null,
    val serviceCostsPerMonth: Int? = null,
    val coordinates: Coordinates? = null,
    val fetchedAtEpochSeconds: Long = 0,
)

/** A listing as kept in the database, together with its bookkeeping. */
data class StoredListing(
    val listing: RawListing,
    val lastSeenEpochSeconds: Long,
    val lastDetailFetchEpochSeconds: Long?,
)

data class HomeKeys(val postcodeKey: String?, val streetKey: String?) {
    fun all(): List<String> = listOfNotNull(postcodeKey, streetKey)
}

/** One home after listings from several sources were combined. */
data class MergedHome(
    val keys: HomeKeys,
    val address: String,
    val url: String,
    val city: String?,
    val price: Int?,
    val priceOnRequest: Boolean,
    val areaSquareMeters: Int?,
    val bedrooms: Int?,
    val energyLabel: EnergyLabel?,
    val status: ListingStatus?,
    val serviceCostsPerMonth: Int?,
    val coordinates: Coordinates?,
    val listings: List<RawListing>,
)

data class BikeInfo(val distanceMeters: Int, val minutes: Double)

data class HomeBikeInfo(val work: BikeInfo?, val station: BikeInfo?)

data class SourceRunResult(
    val source: SourceName,
    val success: Boolean,
    val listingCount: Int,
    val error: String? = null,
)

@Serializable
data class PublishedHome(
    val key: String,
    val address: String,
    val url: String,
    val price: Int?,
    val serviceCostsPerMonth: Int?,
    val areaSquareMeters: Int?,
    val bedrooms: Int?,
    val energyLabel: String?,
    val status: String?,
    val bikeToWorkMeters: Int?,
    val bikeToWorkMinutes: Int?,
    val bikeToStationMeters: Int?,
    val bikeToStationMinutes: Int?,
    val firstSeenEpochSeconds: Long,
    val baseline: Boolean,
    val goneSinceEpochSeconds: Long?,
)

@Serializable
data class PublishedSourceStatus(
    val name: String,
    val ok: Boolean,
    val listingCount: Int,
    val error: String? = null,
)

@Serializable
data class PublishedData(
    val updatedAtEpochSeconds: Long,
    val newBadgeHours: Int,
    val sources: List<PublishedSourceStatus>,
    val homes: List<PublishedHome>,
)
