package househunt.source

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.options.LoadState
import househunt.run.RequestConfiguration
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.random.Random

interface PageFetcher : AutoCloseable {
    fun fetchHtml(url: String): String
    override fun close() {}
}

/** Waits a random 2-5 seconds between two requests to the same host. */
class RequestPacer(
    private val configuration: RequestConfiguration,
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val lastRequestPerHost = HashMap<String, Long>()

    fun waitForTurn(url: String) {
        val host = URI.create(url).host ?: return
        val last = lastRequestPerHost[host]
        if (last != null) {
            val wanted = Random.nextLong(configuration.minimumDelayMillis, configuration.maximumDelayMillis + 1)
            val remaining = wanted - (clock() - last)
            if (remaining > 0) sleeper(remaining)
        }
        lastRequestPerHost[host] = clock()
    }
}

/** A page answered with an error status; paging code treats a 404 after page 1 as "no more pages". */
class HttpStatusException(val statusCode: Int, val url: String) :
    IllegalStateException("HTTP $statusCode for $url")

class HttpPageFetcher(
    private val configuration: RequestConfiguration,
    private val pacer: RequestPacer,
) : PageFetcher {
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    override fun fetchHtml(url: String): String {
        pacer.waitForTurn(url)
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(40))
            .header("User-Agent", configuration.userAgent)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "nl-NL,nl;q=0.9,en;q=0.5")
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) throw HttpStatusException(response.statusCode(), url)
        return response.body()
    }
}

/** Headless Chromium set to Dutch language and the Europe/Amsterdam time zone. */
class BrowserPageFetcher(
    private val configuration: RequestConfiguration,
    private val pacer: RequestPacer,
    timeZone: String,
) : PageFetcher {
    private val playwright: Playwright = Playwright.create()
    private val browser: Browser = playwright.chromium().launch(BrowserType.LaunchOptions().setHeadless(true))
    private val context: BrowserContext = browser.newContext(
        Browser.NewContextOptions()
            .setLocale("nl-NL")
            .setTimezoneId(timeZone)
            .setUserAgent(configuration.userAgent),
    )
    private val page: Page = context.newPage()

    override fun fetchHtml(url: String): String {
        pacer.waitForTurn(url)
        val response = page.navigate(url)
        if (response == null || response.status() !in 200..299) throw HttpStatusException(response?.status() ?: 0, url)
        page.waitForLoadState(LoadState.NETWORKIDLE)
        acceptCookiesIfAsked()
        return page.content()
    }

    private fun acceptCookiesIfAsked() {
        for (selector in cookieButtonSelectors) {
            val button = page.locator(selector).first()
            if (clickIfVisible(button)) {
                page.waitForLoadState(LoadState.NETWORKIDLE)
                return
            }
        }
    }

    private fun clickIfVisible(button: Locator): Boolean = runCatching {
        if (button.count() == 0 || !button.isVisible) return false
        button.click(Locator.ClickOptions().setTimeout(3000.0))
        true
    }.getOrDefault(false)

    override fun close() {
        context.close()
        browser.close()
        playwright.close()
    }

    private companion object {
        val cookieButtonSelectors = listOf(
            "#CybotCookiebotDialogBodyLevelButtonLevelOptinAllowAll",
            "#CybotCookiebotDialogBodyButtonAccept",
            "button:has-text('Alles toestaan')",
            "button:has-text('Accepteren')",
            "button:has-text('Akkoord')",
        )
    }
}
