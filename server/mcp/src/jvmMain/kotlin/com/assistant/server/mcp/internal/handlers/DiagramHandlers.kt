package com.assistant.server.mcp.internal.handlers

import com.assistant.server.mcp.internal.UserContext
import com.assistant.mcp.models.McpToolCallResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Handlers for Draw.io diagram generation tools.
 * Creates native .drawio XML files in the diagrams/ directory.
 */
class DiagramHandlers(
    private val diagramsDir: String = "diagrams"
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val json = Json { encodeDefaults = true }

    suspend fun handleCreateDiagram(
        args: JsonObject, ctx: UserContext
    ): McpToolCallResponse {
        val xml = args.str("xml") ?: return missingField("xml")
        val filename = args.str("filename") ?: return missingField("filename")
        val format = args.str("format") ?: "drawio"

        val validationError = validateXml(xml)
        if (validationError != null) return errorResponse(validationError)

        return writeDiagramFile(xml, filename, format)
    }

    suspend fun handleListDiagrams(
        args: JsonObject, ctx: UserContext
    ): McpToolCallResponse {
        val dir = File(diagramsDir)
        if (!dir.exists()) return textResponse("[]")
        val files = dir.listFiles()
            ?.filter { it.extension == "drawio" }
            ?.map { DiagramFileInfo(it.name, it.length(), it.lastModified()) }
            ?: emptyList()
        return textResponse(json.encodeToString(files))
    }

    private fun validateXml(xml: String): String? {
        if (!xml.contains("<mxGraphModel"))
            return "XML must contain <mxGraphModel> root element"
        if (!xml.contains("id=\"0\""))
            return "XML must contain root cell with id=\"0\""
        if (!xml.contains("id=\"1\""))
            return "XML must contain default layer cell with id=\"1\""
        if (xml.contains("<!--"))
            return "XML must NOT contain comments (<!-- -->)"
        return null
    }

    private fun writeDiagramFile(
        xml: String, filename: String, format: String
    ): McpToolCallResponse {
        val dir = File(diagramsDir)
        if (!dir.exists()) dir.mkdirs()

        val sanitized = sanitizeFilename(filename)
        val outputFile = File(dir, "$sanitized.drawio")
        outputFile.writeText(xml, Charsets.UTF_8)
        logger.info("Diagram created: {}", outputFile.absolutePath)

        val result = DiagramResult(
            file = outputFile.path,
            absolutePath = outputFile.absolutePath,
            format = format,
            sizeBytes = outputFile.length(),
            note = buildResultNote(format, outputFile)
        )
        return textResponse(json.encodeToString(result))
    }

    private fun buildResultNote(format: String, file: File): String {
        if (format == "drawio") {
            return "Diagram saved. Open with draw.io desktop app."
        }
        return "Diagram saved as .drawio. Export to $format " +
            "requires draw.io desktop CLI."
    }

    private fun sanitizeFilename(name: String): String =
        name.replace(Regex("[^a-zA-Z0-9_-]"), "-")
            .lowercase()
            .trimStart('-').trimEnd('-')
}

@Serializable
data class DiagramResult(
    val file: String,
    val absolutePath: String,
    val format: String,
    val sizeBytes: Long,
    val note: String
)

@Serializable
data class DiagramFileInfo(
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long
)
