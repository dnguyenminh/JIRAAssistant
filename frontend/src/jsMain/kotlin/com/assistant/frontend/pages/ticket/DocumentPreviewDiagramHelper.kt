package com.assistant.frontend.pages.ticket

import com.assistant.ai.deepanalysis.models.DrawioConnection
import com.assistant.ai.deepanalysis.models.DrawioMetadata
import com.assistant.ai.deepanalysis.models.DrawioNode
import kotlinx.serialization.json.*

/**
 * Parses JSON metadata from AI-generated code blocks into DrawioMetadata.
 * Handles both standard DrawioMetadata format and AI output format
 * (diagram_type, nodes with "process" types, etc.).
 * Requirements: 7.5
 */
internal object DocumentPreviewDiagramHelper {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Node type mapping: AI output type → DrawioShapeMapper type */
    private val typeMapping = mapOf(
        "process" to "server",
        "database" to "database",
        "external" to "external_api",
        "external_api" to "external_api",
        "webapp" to "webapp",
        "server" to "server",
        "mobile" to "mobile",
        "cloud" to "cloud",
        "user" to "user",
        "service" to "service",
        "queue" to "queue",
        "cache" to "cache"
    )

    /** Diagram type → template name mapping */
    private val templateMapping = mapOf(
        "system architecture" to "component",
        "component" to "component",
        "flow" to "flow",
        "deployment" to "deployment",
        "dependency" to "dependency",
        "bpmn" to "bpmn",
        "sequence" to "flow",
        "data flow" to "flow",
        "process flow" to "bpmn"
    )

    /**
     * Checks if text looks like JSON diagram metadata.
     * Must contain both "nodes" and "connections" arrays.
     */
    fun isJsonDiagramMetadata(text: String): Boolean {
        val trimmed = text.trim()
        if (!trimmed.startsWith("{")) return false
        return trimmed.contains("\"nodes\"") &&
            trimmed.contains("\"connections\"")
    }

    /**
     * Attempts to parse JSON text into DrawioMetadata.
     * Returns null if parsing fails or format is invalid.
     */
    fun tryParseJsonMetadata(text: String): DrawioMetadata? {
        return try {
            val obj = json.parseToJsonElement(text.trim()).jsonObject
            val template = resolveTemplate(obj)
            val nodes = parseNodes(obj)
            val connections = parseConnections(obj)
            if (nodes.isEmpty()) return null
            DrawioMetadata(template = template, nodes = nodes, connections = connections)
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveTemplate(obj: JsonObject): String {
        val diagramType = obj["diagram_type"]?.jsonPrimitive?.content
            ?: obj["template"]?.jsonPrimitive?.content
            ?: return "component"
        return templateMapping[diagramType.lowercase()] ?: "component"
    }

    private fun parseNodes(obj: JsonObject): List<DrawioNode> {
        val arr = obj["nodes"]?.jsonArray ?: return emptyList()
        return arr.mapNotNull { parseNode(it) }
    }

    private fun parseNode(element: JsonElement): DrawioNode? {
        val node = element.jsonObject
        val id = node["id"]?.jsonPrimitive?.content ?: return null
        val label = node["label"]?.jsonPrimitive?.content ?: id
        val rawType = node["type"]?.jsonPrimitive?.content ?: "process"
        val mappedType = typeMapping[rawType.lowercase()] ?: "server"
        return DrawioNode(id = id, label = label, type = mappedType)
    }

    private fun parseConnections(obj: JsonObject): List<DrawioConnection> {
        val arr = obj["connections"]?.jsonArray ?: return emptyList()
        return arr.mapNotNull { parseConnection(it) }
    }

    private fun parseConnection(element: JsonElement): DrawioConnection? {
        val conn = element.jsonObject
        val from = conn["from"]?.jsonPrimitive?.content ?: return null
        val to = conn["to"]?.jsonPrimitive?.content ?: return null
        val label = conn["label"]?.jsonPrimitive?.content ?: ""
        return DrawioConnection(from = from, to = to, label = label)
    }
}
