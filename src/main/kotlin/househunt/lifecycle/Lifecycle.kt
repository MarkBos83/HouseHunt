package househunt.lifecycle

import househunt.model.SourceName
import househunt.model.StoredListing

private const val SECONDS_PER_HOUR = 3600L
private const val SECONDS_PER_DAY = 86400L

/**
 * A listing counts as present when its source's latest successful run saw it.
 * A source that failed keeps its previous state, so a broken scraper never makes homes disappear.
 */
fun isListingPresent(stored: StoredListing, latestSuccessPerSource: Map<SourceName, Long>): Boolean {
    val latestSuccess = latestSuccessPerSource[stored.listing.source] ?: return false
    return stored.lastSeenEpochSeconds >= latestSuccess
}

fun isHomePresent(listings: List<StoredListing>, latestSuccessPerSource: Map<SourceName, Long>): Boolean =
    listings.any { isListingPresent(it, latestSuccessPerSource) }

/** Sets the gone time on the first missing run and clears it when the home reappears. */
fun updateGoneSince(currentGoneSince: Long?, present: Boolean, nowEpochSeconds: Long): Long? = when {
    present -> null
    currentGoneSince != null -> currentGoneSince
    else -> nowEpochSeconds
}

fun isWithinGoneRetention(goneSince: Long?, nowEpochSeconds: Long, retentionDays: Int): Boolean {
    if (goneSince == null) return true
    return nowEpochSeconds - goneSince <= retentionDays * SECONDS_PER_DAY
}

/** The browser applies the same rule so the badge expires between runs. */
fun isNewHome(firstSeenEpochSeconds: Long, baseline: Boolean, nowEpochSeconds: Long, newBadgeHours: Int): Boolean =
    !baseline && nowEpochSeconds - firstSeenEpochSeconds < newBadgeHours * SECONDS_PER_HOUR
