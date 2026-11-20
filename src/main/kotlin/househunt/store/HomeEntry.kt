package househunt.store

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One listing in homes.json. The Claude scheduled task writes the Funda and Pararius entries,
 * this program writes the Huislijn and Buurtje entries; both use this shape.
 */
@Serializable
data class HomeEntry(
    val source: String,
    val address: String,
    @SerialName("price_eur") val priceEuros: Int? = null,
    @SerialName("living_area_m2") val livingAreaSquareMeters: Int? = null,
    val bedrooms: Int? = null,
    @SerialName("energy_label") val energyLabel: String? = null,
    val status: String? = null,
    val url: String,
    @SerialName("service_costs_eur") val serviceCostsEuros: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("first_seen") val firstSeen: String,
    @SerialName("last_seen") val lastSeen: String,
    @SerialName("no_longer_listed") val noLongerListed: Boolean = false,
)
