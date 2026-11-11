package househunt

import househunt.model.SourceName
import househunt.model.SourceRunResult
import househunt.run.RunSlot
import househunt.store.Database
import househunt.store.StoredHome
import java.nio.file.Files
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DatabaseTest {

    private fun withDatabase(block: (Database) -> Unit) {
        val directory = Files.createTempDirectory("househunt-test")
        Database(directory.resolve("test.db")).use(block)
    }

    @Test
    fun `a saved listing can be read back and keeps its detail fetch time`() = withDatabase { database ->
        val listing = buildListing()
        database.saveListing(listing, seenAtEpochSeconds = 100, detailFetched = true)
        database.saveListing(listing, seenAtEpochSeconds = 200, detailFetched = false)

        val stored = database.findListing(listing.source, listing.url)
        assertNotNull(stored)
        assertEquals(200, stored.lastSeenEpochSeconds)
        assertEquals(100, stored.lastDetailFetchEpochSeconds)
        assertEquals(listing, stored.listing)
    }

    @Test
    fun `a home is found again by either of its keys`() = withDatabase { database ->
        val home = StoredHome(0, "pc:3811AB:12:", "st:stationsstraat:12:", "Stationsstraat 12", 1, 1, null, false)
        database.insertHome(home)

        assertNotNull(database.findHomeByKeys(listOf("pc:3811AB:12:")))
        assertNotNull(database.findHomeByKeys(listOf("st:stationsstraat:12:")))
        assertNull(database.findHomeByKeys(listOf("st:other:1:")))
    }

    @Test
    fun `slots and successful runs are tracked`() = withDatabase { database ->
        val slot = RunSlot(LocalDate.of(2026, 1, 15), 8)
        database.recordSourceRun(100, slot, SourceRunResult(SourceName.FUNDA, true, 10))
        database.recordSourceRun(100, slot, SourceRunResult(SourceName.PARARIUS, false, 0, "blocked"))

        assertEquals(setOf(slot), database.findSlotsRunOn(slot.date))
        assertEquals(mapOf(SourceName.FUNDA to 100L), database.findLatestSuccessPerSource())
        assertEquals("blocked", database.findLatestRunPerSource().getValue(SourceName.PARARIUS).error)
    }

    @Test
    fun `route and geocode caches return what was saved`() = withDatabase { database ->
        database.saveRouteDistance("52.1,5.4", "Databankweg 12K", 2500)
        assertEquals(2500, database.findRouteDistance("52.1,5.4", "Databankweg 12K"))
        assertNull(database.findRouteDistance("52.1,5.4", "elsewhere"))
    }
}
