package househunt

import househunt.model.PublishedData
import househunt.publish.ENCRYPTED_DATA_PLACEHOLDER
import househunt.publish.assembleHtml
import househunt.publish.decryptText
import househunt.publish.encryptText
import househunt.publish.exportToJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EncryptionTest {
    // Few iterations keep the test fast; production uses 600,000.
    private val testIterations = 1000

    @Test
    fun `encrypted data decrypts with the same password`() {
        val payload = encryptText("secret homes", "hunter2", iterations = testIterations)
        assertEquals("secret homes", decryptText(payload, "hunter2"))
    }

    @Test
    fun `a wrong password fails`() {
        val payload = encryptText("secret homes", "hunter2", iterations = testIterations)
        assertFailsWith<Exception> { decryptText(payload, "wrong") }
    }

    @Test
    fun `every encryption uses a new salt and iv`() {
        val first = encryptText("same", "pw", iterations = testIterations)
        val second = encryptText("same", "pw", iterations = testIterations)
        assertFalse(first.salt == second.salt || first.iv == second.iv)
    }

    @Test
    fun `the published page contains no readable data`() {
        val data = PublishedData(1, 48, emptyList(), emptyList())
        val payload = encryptText(exportToJson(data), "pw", iterations = testIterations)
        val html = assembleHtml("<html>$ENCRYPTED_DATA_PLACEHOLDER</html>", payload)
        assertTrue(html.contains("""id="encrypted-data""""))
        assertFalse(html.contains("newBadgeHours"))
    }
}
