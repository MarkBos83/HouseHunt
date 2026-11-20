package househunt

import househunt.source.BuurtjeSource
import househunt.source.HttpStatusException
import househunt.source.PageFetcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PagingTest {
    private val firstPage = """<div class="pc"><a class="pc-body" href="/woning/ringweg-5"></a><span class="pc-adres">Ringweg 5</span></div>"""
    private val searchUrl = "https://example.test/zoeken"

    private fun sourceWith(secondPageStatus: Int?): BuurtjeSource {
        val fetcher = object : PageFetcher {
            override fun fetchHtml(url: String): String = when {
                "page=2" !in url -> firstPage
                secondPageStatus == null -> firstPage
                else -> throw HttpStatusException(secondPageStatus, url)
            }
        }
        return BuurtjeSource(searchUrl, 5, fetcher) { 0 }
    }

    @Test
    fun `a 404 after the first page means there are no more pages`() {
        assertEquals(1, sourceWith(404).fetchSearchListings().size)
    }

    @Test
    fun `other errors after the first page fail the source so homes are not wrongly marked gone`() {
        assertFailsWith<HttpStatusException> { sourceWith(500).fetchSearchListings() }
    }

    @Test
    fun `a repeated page with nothing new ends the paging`() {
        assertEquals(1, sourceWith(null).fetchSearchListings().size)
    }

    @Test
    fun `a 404 on the first page is a failure`() {
        val fetcher = object : PageFetcher {
            override fun fetchHtml(url: String): String = throw HttpStatusException(404, url)
        }
        val source = BuurtjeSource(searchUrl, 5, fetcher) { 0 }
        assertFailsWith<HttpStatusException> { source.fetchSearchListings() }
    }
}
