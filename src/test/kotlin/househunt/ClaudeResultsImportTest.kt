package househunt

import househunt.store.ClaudeResultHome
import househunt.store.ClaudeResults
import househunt.store.HomesFile
import househunt.store.importClaudeResults
import househunt.store.isEnergyLabelKept
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClaudeResultsImportTest {

    private fun home(url: String, label: String? = "A") =
        ClaudeResultHome(address = "De Beurs 40, 3823 GA Amersfoort", priceEuros = 375_000, energyLabel = label, url = url)

    @Test
    fun `label b or better and unknown labels are kept`() {
        assertTrue(isEnergyLabelKept("A++"))
        assertTrue(isEnergyLabelKept("B"))
        assertTrue(isEnergyLabelKept(null))
        assertTrue(isEnergyLabelKept("unknown"))
        assertFalse(isEnergyLabelKept("C"))
    }

    @Test
    fun `a failed source keeps its entries and huislijn is untouched`() {
        val file = Files.createTempFile("homes", ".json")
        Files.writeString(file, """[
            {"source":"pararius","address":"Ariaplein 74, 3816 EK Amersfoort","url":"p","first_seen":"2026-11-01","last_seen":"2026-11-01"},
            {"source":"huislijn","address":"Karper 74, 3824 LZ Amersfoort","url":"h","first_seen":"2026-11-01","last_seen":"2026-11-01","extra":"kept"}
        ]""")
        val results = ClaudeResults(funda = listOf(home("f1"), home("f2", label = "C"), home("f1")), pararius = null)

        val summary = importClaudeResults(results, HomesFile(file), "2026-11-21")
        val entries = HomesFile(file).readEntries()

        assertEquals("funda: 1 listed, 1 first seen today", summary)
        assertEquals(listOf("p", "h", "f1"), entries.map { it.url })
        assertEquals("2026-11-01", entries.first { it.url == "p" }.lastSeen)
        assertTrue(Files.readString(file).contains("\"extra\": \"kept\""))
    }

    @Test
    fun `both sources failing changes nothing`() {
        val file = Files.createTempFile("homes", ".json")
        Files.writeString(file, "[]")
        importClaudeResults(ClaudeResults(null, null), HomesFile(file), "2026-11-21")
        assertEquals("[]", Files.readString(file))
    }
}
