package househunt.publish

import househunt.model.PublishedData
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

const val ENCRYPTED_DATA_PLACEHOLDER = "<!--ENCRYPTED_DATA-->"

private val exportJson = Json { encodeDefaults = true }

fun exportToJson(data: PublishedData): String = exportJson.encodeToString(PublishedData.serializer(), data)

/** Base64 and JSON braces are safe inside a script tag, but "</" never is. */
fun buildEncryptedDataTag(payload: EncryptedPayload): String {
    val json = encodePayloadAsJson(payload).replace("</", "<\\/")
    return """<script id="encrypted-data" type="application/json">$json</script>"""
}

fun assembleHtml(template: String, payload: EncryptedPayload): String {
    require(ENCRYPTED_DATA_PLACEHOLDER in template) { "Template has no $ENCRYPTED_DATA_PLACEHOLDER marker" }
    return template.replace(ENCRYPTED_DATA_PLACEHOLDER, buildEncryptedDataTag(payload))
}

fun publishSite(data: PublishedData, password: String, templateFile: Path, outputFile: Path) {
    val payload = encryptText(exportToJson(data), password)
    val html = assembleHtml(Files.readString(templateFile), payload)
    outputFile.parent?.let { Files.createDirectories(it) }
    Files.writeString(outputFile, html)
}
