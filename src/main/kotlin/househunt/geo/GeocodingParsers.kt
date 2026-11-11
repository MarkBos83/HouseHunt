package househunt.geo

import househunt.model.Coordinates
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val lenientJson = Json { ignoreUnknownKeys = true }
private val wktPointPattern = Regex("""POINT\(\s*(-?[\d.]+)\s+(-?[\d.]+)\s*\)""")

/** PDOK answers with "centroide_ll": "POINT(longitude latitude)". */
fun parsePdokResponse(body: String): Coordinates? {
    val documents = lenientJson.parseToJsonElement(body).jsonObject["response"]
        ?.jsonObject?.get("docs") as? JsonArray ?: return null
    val centroid = documents.firstOrNull()?.jsonObject?.get("centroide_ll")?.jsonPrimitive?.content ?: return null
    val match = wktPointPattern.find(centroid) ?: return null
    return Coordinates(
        latitude = match.groupValues[2].toDouble(),
        longitude = match.groupValues[1].toDouble(),
    )
}

fun parseNominatimResponse(body: String): Coordinates? {
    val first = lenientJson.parseToJsonElement(body).jsonArray.firstOrNull()?.jsonObject ?: return null
    return readNominatimCoordinates(first)
}

private fun readNominatimCoordinates(entry: JsonObject): Coordinates? {
    val latitude = entry["lat"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return null
    val longitude = entry["lon"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return null
    return Coordinates(latitude, longitude)
}

/** Reads the first row of an OSRM table answer: distances from one source to each destination. */
fun parseOsrmTableDistances(body: String): List<Double?> {
    val row = lenientJson.parseToJsonElement(body).jsonObject["distances"]
        ?.jsonArray?.firstOrNull()?.jsonArray ?: return emptyList()
    return row.map { it.jsonPrimitive.doubleOrNull }
}

fun parseOsrmRouteDistance(body: String): Double? =
    lenientJson.parseToJsonElement(body).jsonObject["routes"]
        ?.jsonArray?.firstOrNull()?.jsonObject?.get("distance")?.jsonPrimitive?.doubleOrNull
