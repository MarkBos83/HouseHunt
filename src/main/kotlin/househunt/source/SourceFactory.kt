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
 * Per-source choice of plain HTTP or browser. Phase 0 showed that Pararius, Huislijn and Buurtje
 * work over plain HTTP (the headless browser even got a 403 on Pararius detail pages).
 * Funda shows a bot check to both plain HTTP and the headless browser.
 */
fun createSources(
    configuration: AppConfiguration,
    fetchers: SourceFetchers,
    onlyEnabled: Boolean = true,
    currentEpochSeconds: () -> Long,
): List<ListingSource> = createAllSources(configuration, fetchers, currentEpochSeconds)
    .filter { !onlyEnabled || it.sourceName in configuration.enabledSources }

/** Funda stays in the code but is off by default (see `enabledSources` in the config). */
private fun createAllSources(
    configuration: AppConfiguration,
    fetchers: SourceFetchers,
    currentEpochSeconds: () -> Long,
): List<ListingSource> {
    val maximumPages = configuration.requests.maximumSearchPages
    fun searchUrl(source: SourceName): String =
        configuration.searchUrls[source] ?: error("No search URL configured for $source")

    return listOf(
        CardLinkSource(
            SourceName.FUNDA, searchUrl(SourceName.FUNDA), Regex("/detail/koop/"), "search_result",
            maximumPages, fetchers.http, fetchers.http, currentEpochSeconds,
        ),
        ParariusSource(searchUrl(SourceName.PARARIUS), maximumPages, fetchers.http, currentEpochSeconds),
        HuislijnSource(searchUrl(SourceName.HUISLIJN), maximumPages, fetchers.http, currentEpochSeconds),
        BuurtjeSource(searchUrl(SourceName.BUURTJE), maximumPages, fetchers.http, currentEpochSeconds),
    )
}
