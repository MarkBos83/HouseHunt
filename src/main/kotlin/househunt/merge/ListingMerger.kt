package househunt.merge

import househunt.model.HomeKeys
import househunt.model.MergedHome
import househunt.model.RawListing
import househunt.normalize.buildHomeKeys
import househunt.normalize.formatAddress

/** Groups listings that share a postcode key or a street key. */
fun groupListingsByKeys(listings: List<RawListing>): List<List<RawListing>> {
    val groupIndexes = UnionFind(listings.size)
    val firstIndexPerKey = HashMap<String, Int>()
    listings.forEachIndexed { index, listing ->
        buildHomeKeys(listing).all().forEach { key ->
            val earlierIndex = firstIndexPerKey.putIfAbsent(key, index)
            if (earlierIndex != null) groupIndexes.union(earlierIndex, index)
        }
    }
    return listings.indices
        .groupBy { groupIndexes.find(it) }
        .values
        .map { indexes -> indexes.map { listings[it] } }
}

fun mergeListings(group: List<RawListing>): MergedHome {
    val byPriority = group.sortedBy { it.source.priority }
    val primary = byPriority.first { it.street != null || it == byPriority.last() }
    val price = firstValue(byPriority) { it.price }
    return MergedHome(
        keys = mergeKeys(byPriority),
        address = formatAddress(primary),
        url = primary.url,
        city = firstValue(byPriority) { it.city },
        price = price,
        priceOnRequest = price == null && byPriority.any { it.priceOnRequest },
        areaSquareMeters = firstValue(byPriority) { it.areaSquareMeters },
        bedrooms = firstValue(byPriority) { it.bedrooms },
        energyLabel = firstValue(byPriority) { it.energyLabel },
        status = firstValue(byPriority) { it.status },
        serviceCostsPerMonth = firstValue(byPriority) { it.serviceCostsPerMonth },
        coordinates = firstValue(byPriority) { it.coordinates },
        listings = byPriority,
    )
}

fun mergeAllGroups(listings: List<RawListing>): List<MergedHome> =
    groupListingsByKeys(listings).map { mergeListings(it) }

private fun mergeKeys(byPriority: List<RawListing>): HomeKeys {
    val allKeys = byPriority.map { buildHomeKeys(it) }
    return HomeKeys(
        postcodeKey = allKeys.firstNotNullOfOrNull { it.postcodeKey },
        streetKey = allKeys.firstNotNullOfOrNull { it.streetKey },
    )
}

/** The highest-priority source with a value wins; lower sources only fill gaps. */
private fun <T : Any> firstValue(byPriority: List<RawListing>, field: (RawListing) -> T?): T? =
    byPriority.firstNotNullOfOrNull(field)

private class UnionFind(size: Int) {
    private val parents = IntArray(size) { it }

    fun find(index: Int): Int {
        var root = index
        while (parents[root] != root) root = parents[root]
        parents[index] = root
        return root
    }

    fun union(first: Int, second: Int) {
        parents[find(first)] = find(second)
    }
}
