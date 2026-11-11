package househunt.store

import househunt.model.Coordinates
import househunt.model.RawListing
import househunt.model.SourceName
import househunt.model.SourceRunResult
import househunt.model.StoredListing
import househunt.run.RunSlot
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.time.LocalDate

data class StoredHome(
    val id: Long,
    val postcodeKey: String?,
    val streetKey: String?,
    val canonicalAddress: String,
    val firstSeenEpochSeconds: Long,
    val lastSeenEpochSeconds: Long,
    val goneSinceEpochSeconds: Long?,
    val baseline: Boolean,
)

class Database(databaseFile: Path) : AutoCloseable {
    private val connection: Connection
    private val json = Json { ignoreUnknownKeys = true }

    init {
        databaseFile.parent?.let { Files.createDirectories(it) }
        connection = DriverManager.getConnection("jdbc:sqlite:$databaseFile")
        createSchema()
    }

    override fun close() = connection.close()

    private fun createSchema() {
        schemaStatements.forEach { statement -> connection.createStatement().use { it.execute(statement) } }
    }

    // ---- source listings ----

    fun saveListing(listing: RawListing, seenAtEpochSeconds: Long, detailFetched: Boolean) {
        val previousDetailFetch = findListing(listing.source, listing.url)?.lastDetailFetchEpochSeconds
        val detailFetchTime = if (detailFetched) seenAtEpochSeconds else previousDetailFetch
        connection.prepareStatement(
            """INSERT OR REPLACE INTO source_listing
               (source, url, raw_json, last_seen, last_detail_fetch) VALUES (?, ?, ?, ?, ?)""",
        ).use { statement ->
            statement.setString(1, listing.source.name)
            statement.setString(2, listing.url)
            statement.setString(3, json.encodeToString(RawListing.serializer(), listing))
            statement.setLong(4, seenAtEpochSeconds)
            statement.setObject(5, detailFetchTime)
            statement.executeUpdate()
        }
    }

    fun findListing(source: SourceName, url: String): StoredListing? =
        connection.prepareStatement("SELECT * FROM source_listing WHERE source = ? AND url = ?").use { statement ->
            statement.setString(1, source.name)
            statement.setString(2, url)
            statement.executeQuery().use { if (it.next()) readStoredListing(it) else null }
        }

    fun findAllListings(): List<StoredListing> =
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT * FROM source_listing").use { results ->
                buildList { while (results.next()) add(readStoredListing(results)) }
            }
        }

    private fun readStoredListing(results: ResultSet): StoredListing = StoredListing(
        listing = json.decodeFromString(RawListing.serializer(), results.getString("raw_json")),
        lastSeenEpochSeconds = results.getLong("last_seen"),
        lastDetailFetchEpochSeconds = results.getLong("last_detail_fetch").takeUnless { results.wasNull() },
    )

    // ---- homes ----

    fun findHomeByKeys(keys: List<String>): StoredHome? {
        if (keys.isEmpty()) return null
        val placeholders = keys.joinToString(",") { "?" }
        val sql = "SELECT * FROM home WHERE postcode_key IN ($placeholders) OR street_key IN ($placeholders) ORDER BY id LIMIT 1"
        return connection.prepareStatement(sql).use { statement ->
            keys.forEachIndexed { index, key ->
                statement.setString(index + 1, key)
                statement.setString(index + 1 + keys.size, key)
            }
            statement.executeQuery().use { if (it.next()) readHome(it) else null }
        }
    }

    fun insertHome(home: StoredHome): Long =
        connection.prepareStatement(
            """INSERT INTO home (postcode_key, street_key, canonical_address, first_seen, last_seen, gone_since, baseline)
               VALUES (?, ?, ?, ?, ?, ?, ?)""",
            java.sql.Statement.RETURN_GENERATED_KEYS,
        ).use { statement ->
            statement.setString(1, home.postcodeKey)
            statement.setString(2, home.streetKey)
            statement.setString(3, home.canonicalAddress)
            statement.setLong(4, home.firstSeenEpochSeconds)
            statement.setLong(5, home.lastSeenEpochSeconds)
            statement.setObject(6, home.goneSinceEpochSeconds)
            statement.setInt(7, if (home.baseline) 1 else 0)
            statement.executeUpdate()
            statement.generatedKeys.use { it.next(); it.getLong(1) }
        }

    fun updateHome(home: StoredHome) {
        connection.prepareStatement(
            """UPDATE home SET postcode_key = ?, street_key = ?, canonical_address = ?,
               last_seen = ?, gone_since = ? WHERE id = ?""",
        ).use { statement ->
            statement.setString(1, home.postcodeKey)
            statement.setString(2, home.streetKey)
            statement.setString(3, home.canonicalAddress)
            statement.setLong(4, home.lastSeenEpochSeconds)
            statement.setObject(5, home.goneSinceEpochSeconds)
            statement.setLong(6, home.id)
            statement.executeUpdate()
        }
    }

    private fun readHome(results: ResultSet): StoredHome = StoredHome(
        id = results.getLong("id"),
        postcodeKey = results.getString("postcode_key"),
        streetKey = results.getString("street_key"),
        canonicalAddress = results.getString("canonical_address"),
        firstSeenEpochSeconds = results.getLong("first_seen"),
        lastSeenEpochSeconds = results.getLong("last_seen"),
        goneSinceEpochSeconds = results.getLong("gone_since").takeUnless { results.wasNull() },
        baseline = results.getInt("baseline") == 1,
    )

    // ---- caches ----

    fun findGeocode(addressKey: String): Coordinates? =
        connection.prepareStatement("SELECT latitude, longitude FROM geocode_cache WHERE address_key = ?").use { statement ->
            statement.setString(1, addressKey)
            statement.executeQuery().use {
                if (it.next()) Coordinates(it.getDouble("latitude"), it.getDouble("longitude")) else null
            }
        }

    fun saveGeocode(addressKey: String, coordinates: Coordinates, service: String) {
        connection.prepareStatement(
            "INSERT OR REPLACE INTO geocode_cache (address_key, latitude, longitude, service) VALUES (?, ?, ?, ?)",
        ).use { statement ->
            statement.setString(1, addressKey)
            statement.setDouble(2, coordinates.latitude)
            statement.setDouble(3, coordinates.longitude)
            statement.setString(4, service)
            statement.executeUpdate()
        }
    }

    fun findRouteDistance(coordinateKey: String, destinationName: String): Int? =
        connection.prepareStatement(
            "SELECT distance_meters FROM route_cache WHERE coordinate_key = ? AND destination = ?",
        ).use { statement ->
            statement.setString(1, coordinateKey)
            statement.setString(2, destinationName)
            statement.executeQuery().use { if (it.next()) it.getInt(1) else null }
        }

    fun saveRouteDistance(coordinateKey: String, destinationName: String, distanceMeters: Int) {
        connection.prepareStatement(
            "INSERT OR REPLACE INTO route_cache (coordinate_key, destination, distance_meters) VALUES (?, ?, ?)",
        ).use { statement ->
            statement.setString(1, coordinateKey)
            statement.setString(2, destinationName)
            statement.setInt(3, distanceMeters)
            statement.executeUpdate()
        }
    }

    // ---- runs ----

    fun recordSourceRun(startedAtEpochSeconds: Long, slot: RunSlot?, result: SourceRunResult) {
        connection.prepareStatement(
            """INSERT INTO run (started_at, slot_date, slot_hour, source, success, listing_count, error)
               VALUES (?, ?, ?, ?, ?, ?, ?)""",
        ).use { statement ->
            statement.setLong(1, startedAtEpochSeconds)
            statement.setString(2, slot?.date?.toString())
            statement.setObject(3, slot?.hour)
            statement.setString(4, result.source.name)
            statement.setInt(5, if (result.success) 1 else 0)
            statement.setInt(6, result.listingCount)
            statement.setString(7, result.error)
            statement.executeUpdate()
        }
    }

    fun findLatestSuccessPerSource(): Map<SourceName, Long> =
        connection.createStatement().use { statement ->
            statement.executeQuery(
                "SELECT source, MAX(started_at) AS latest FROM run WHERE success = 1 GROUP BY source",
            ).use { results ->
                buildMap { while (results.next()) put(SourceName.valueOf(results.getString("source")), results.getLong("latest")) }
            }
        }

    fun findLatestRunPerSource(): Map<SourceName, SourceRunResult> =
        connection.createStatement().use { statement ->
            statement.executeQuery(
                """SELECT r.* FROM run r
                   JOIN (SELECT source, MAX(started_at) AS latest FROM run GROUP BY source) m
                     ON r.source = m.source AND r.started_at = m.latest""",
            ).use { results ->
                buildMap {
                    while (results.next()) {
                        val source = SourceName.valueOf(results.getString("source"))
                        put(
                            source,
                            SourceRunResult(
                                source = source,
                                success = results.getInt("success") == 1,
                                listingCount = results.getInt("listing_count"),
                                error = results.getString("error"),
                            ),
                        )
                    }
                }
            }
        }

    fun findSlotsRunOn(date: LocalDate): Set<RunSlot> =
        connection.prepareStatement("SELECT DISTINCT slot_hour FROM run WHERE slot_date = ?").use { statement ->
            statement.setString(1, date.toString())
            statement.executeQuery().use { results ->
                buildSet { while (results.next()) add(RunSlot(date, results.getInt("slot_hour"))) }
            }
        }

    fun hasAnyRun(): Boolean =
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT 1 FROM run LIMIT 1").use { it.next() }
        }

    private companion object {
        val schemaStatements = listOf(
            """CREATE TABLE IF NOT EXISTS home (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                postcode_key TEXT, street_key TEXT, canonical_address TEXT NOT NULL,
                first_seen INTEGER NOT NULL, last_seen INTEGER NOT NULL,
                gone_since INTEGER, baseline INTEGER NOT NULL DEFAULT 0)""",
            """CREATE TABLE IF NOT EXISTS source_listing (
                source TEXT NOT NULL, url TEXT NOT NULL, raw_json TEXT NOT NULL,
                last_seen INTEGER NOT NULL, last_detail_fetch INTEGER,
                PRIMARY KEY (source, url))""",
            """CREATE TABLE IF NOT EXISTS geocode_cache (
                address_key TEXT PRIMARY KEY, latitude REAL NOT NULL,
                longitude REAL NOT NULL, service TEXT NOT NULL)""",
            """CREATE TABLE IF NOT EXISTS route_cache (
                coordinate_key TEXT NOT NULL, destination TEXT NOT NULL,
                distance_meters INTEGER NOT NULL, PRIMARY KEY (coordinate_key, destination))""",
            """CREATE TABLE IF NOT EXISTS run (
                id INTEGER PRIMARY KEY AUTOINCREMENT, started_at INTEGER NOT NULL,
                slot_date TEXT, slot_hour INTEGER, source TEXT NOT NULL,
                success INTEGER NOT NULL, listing_count INTEGER NOT NULL, error TEXT)""",
        )
    }
}
