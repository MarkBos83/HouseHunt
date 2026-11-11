package househunt

import househunt.model.SourceName
import househunt.run.AppConfiguration
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ConfigurationTest {
    @Test
    fun `a search url without a scheme is rejected with a clear message`() {
        val exception = assertFailsWith<IllegalArgumentException> { configurationWith("buurtje.nl/koopwoningen") }
        assertTrue(exception.message!!.contains("BUURTJE"))
    }

    private fun configurationWith(url: String): AppConfiguration {
        val sample = sampleConfiguration()
        return sample.copy(searchUrls = mapOf(SourceName.BUURTJE to url))
    }
}
