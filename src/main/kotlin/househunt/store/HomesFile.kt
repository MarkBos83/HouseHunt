package househunt.store

import househunt.model.SourceName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

private val homesJson = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
    encodeDefaults = true
}

/**
 * homes.json is shared with the Claude scheduled task. This program only rewrites the entries
 * of the sources it collected itself; every other entry is written back exactly as it was read.
 */
class HomesFile(private val path: Path) {

    fun readEntries(): List<HomeEntry> =
        readElements().map { homesJson.decodeFromJsonElement(HomeEntry.serializer(), it) }

    /** Replaces the entries of the given sources and returns the full new list. */
    fun updateSources(freshEntriesPerSource: Map<SourceName, List<HomeEntry>>, today: String): List<HomeEntry> {
        val elements = readElements()
        val ownSourceNames = freshEntriesPerSource.keys.map { it.name.lowercase() }.toSet()
        val (ownElements, otherElements) = elements.partition { readSourceText(it) in ownSourceNames }
        val previousOwn = ownElements.map { homesJson.decodeFromJsonElement(HomeEntry.serializer(), it) }
        val updatedOwn = freshEntriesPerSource.flatMap { (source, fresh) ->
            val previous = previousOwn.filter { parseSourceName(it.source) == source }
            mergeSourceEntries(previous, fresh, today)
        }
        writeElements(otherElements + updatedOwn.map { homesJson.encodeToJsonElement(HomeEntry.serializer(), it) })
        return readEntries()
    }

    private fun readElements(): List<JsonElement> {
        if (!Files.exists(path)) return emptyList()
        val text = Files.readString(path)
        if (text.isBlank()) return emptyList()
        return homesJson.parseToJsonElement(text).jsonArray
    }

    /** Writes to a temporary file first, so a crash never leaves a half-written homes.json. */
    private fun writeElements(elements: List<JsonElement>) {
        path.toAbsolutePath().parent?.let { Files.createDirectories(it) }
        val temporary = path.resolveSibling("${path.fileName}.tmp")
        Files.writeString(temporary, homesJson.encodeToString(JsonElement.serializer(), JsonArray(elements)) + "\n")
        Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun readSourceText(element: JsonElement): String =
        element.jsonObject["source"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase().orEmpty()
}

/**
 * Same rules as the Claude task: homes still listed keep their first_seen and get today's last_seen,
 * new homes start today, and homes that disappeared stay in the file flagged no_longer_listed.
 */
fun mergeSourceEntries(previous: List<HomeEntry>, fresh: List<HomeEntry>, today: String): List<HomeEntry> {
    val previousByUrl = previous.associateBy { it.url }
    val freshUrls = fresh.map { it.url }.toSet()
    val stillListed = fresh.map { entry ->
        entry.copy(firstSeen = previousByUrl[entry.url]?.firstSeen ?: today, lastSeen = today, noLongerListed = false)
    }
    val disappeared = previous.filter { it.url !in freshUrls }.map { it.copy(noLongerListed = true) }
    return stillListed + disappeared
}
