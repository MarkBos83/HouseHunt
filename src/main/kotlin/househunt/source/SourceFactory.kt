package househunt.source

import househunt.model.SourceName
import househunt.run.AppConfiguration

/** The fetchers each source needs; the browser is created only when a source uses it. */
class SourceFetchers(
    val http: PageFetcher,
    browserProvider: () -> PageFetcher,
) : AutoCloseable {
    val browser: PageFetcher = LazyPageFetcher(browserProvider)

    override fun close() {
        browser.close()
        http.close()
    }
}

/** Starts the browser on the first request, so a browser problem only fails the sources that need it. */
class LazyPageFetcher(provider: () -> PageFetcher) : PageFetcher {
    private val delegate = lazy(provider)

    override fun fetchHtml(url: String): String = delegate.value.fetchHtml(url)

    override fun close() {
        if (delegate.isInitialized()) delegate.value.close()
    }
}

/**
 * Huislijn and Buurtje work over plain HTTP. Funda and Pararius are not scraped here:
 * a Claude scheduled task writes them to homes.json.
 */
fun createSources(
    configuration: AppConfiguration,
    fetchers: SourceFetchers,
    onlyEnabled: Boolean = true,
    currentEpochSeconds: () -> Long,
): List<ListingSource> = createAllSources(configuration, fetchers, currentEpochSeconds)
    .filter { !onlyEnabled || it.sourceName in configuration.enabledSources }

private fun createAllSources(
    configuration: AppConfiguration,
    fetchers: SourceFetchers,
    currentEpochSeconds: () -> Long,
): List<ListingSource> {
    val maximumPages = configuration.requests.maximumSearchPages
    fun searchUrl(source: SourceName): String =
        configuration.searchUrls[source] ?: error("No search URL configured for $source")

    return listOf(
        HuislijnSource(searchUrl(SourceName.HUISLIJN), maximumPages, fetchers.http, currentEpochSeconds),
        BuurtjeSource(searchUrl(SourceName.BUURTJE), maximumPages, fetchers.http, currentEpochSeconds),
    )
}
