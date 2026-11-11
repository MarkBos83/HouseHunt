package househunt

import househunt.model.EnergyLabel
import househunt.model.ListingStatus
import househunt.model.MergedHome
import househunt.model.RawListing
import househunt.model.SourceName
import househunt.merge.mergeListings
import househunt.run.FilterConfiguration

fun buildListing(
    source: SourceName = SourceName.FUNDA,
    url: String = "https://example.test/${source.name.lowercase()}/1",
    street: String? = "Stationsstraat",
    houseNumber: Int? = 12,
    addition: String = "",
    postcode: String? = "3811AB",
    city: String? = "Amersfoort",
    price: Int? = 350_000,
    area: Int? = 90,
    bedrooms: Int? = 3,
    label: EnergyLabel? = EnergyLabel.A,
    status: ListingStatus? = ListingStatus.AVAILABLE,
    serviceCosts: Int? = null,
) = RawListing(
    source = source, url = url, street = street, houseNumber = houseNumber, addition = addition,
    postcode = postcode, city = city, price = price, areaSquareMeters = area, bedrooms = bedrooms,
    energyLabel = label, status = status, serviceCostsPerMonth = serviceCosts,
)

fun buildHome(
    price: Int? = 350_000,
    area: Int? = 90,
    bedrooms: Int? = 3,
    label: EnergyLabel? = EnergyLabel.A,
    status: ListingStatus? = ListingStatus.AVAILABLE,
    city: String? = "Amersfoort",
): MergedHome = mergeListings(
    listOf(buildListing(price = price, area = area, bedrooms = bedrooms, label = label, status = status, city = city)),
)

fun buildFilters(maximumBikeMinutes: Double = 13.0) = FilterConfiguration(
    place = "Amersfoort", maximumPrice = 400_000, minimumAreaSquareMeters = 75,
    minimumBedrooms = 2, maximumBikeMinutes = maximumBikeMinutes,
)

fun sampleConfiguration() = househunt.run.AppConfiguration(
    searchUrls = mapOf(househunt.model.SourceName.BUURTJE to "https://buurtje.nl/koopwoningen/amersfoort/"),
    enabledSources = setOf(househunt.model.SourceName.BUURTJE),
    filters = buildFilters(),
    searchArea = househunt.run.SearchAreaConfiguration(52.05, 52.30, 5.20, 5.60),
    bike = househunt.run.BikeConfiguration(16.0, "https://example.test", "driving"),
    destinations = househunt.run.DestinationsConfiguration(
        work = househunt.run.DestinationConfiguration("Work", "Work Amersfoort"),
        stations = emptyList(),
    ),
    requests = househunt.run.RequestConfiguration(1, 2, 3, "test"),
    lifecycle = househunt.run.LifecycleConfiguration(48, 7),
    schedule = ScheduleConfigurationForTest,
    paths = househunt.run.PathConfiguration("d", "t", "o", "f"),
)

private val ScheduleConfigurationForTest = househunt.run.ScheduleConfiguration("Europe/Amsterdam", listOf(8, 15, 20), 2)
