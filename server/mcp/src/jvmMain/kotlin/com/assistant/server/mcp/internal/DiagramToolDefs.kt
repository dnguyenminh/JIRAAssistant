package com.assistant.server.mcp.internal

import com.assistant.mcp.models.InternalToolDefinition
import com.assistant.mcp.models.ToolCategory
import kotlinx.serialization.json.*

/**
 * Draw.io diagram generation tool definitions.
 * Provides tools for creating native .drawio XML diagrams.
 */
object DiagramToolDefs {

    private val diagramTypes = listOf(
        "flowchart", "sequence", "class", "er",
        "architecture", "network", "mindmap", "state",
        "activity", "component", "deployment", "custom"
    )

    private val exportFormats = listOf("drawio", "png", "svg", "pdf")

    fun all(): List<InternalToolDefinition> = listOf(
        createDiagram(), listDiagrams()
    )

    private fun createDiagram() = InternalToolDefinition(
        name = "create_drawio_diagram",
        description = buildCreateDescription(),
        inputSchema = buildCreateSchema(),
        requiredPermission = "VIEW_ANALYSIS",
        requiredRole = "Reader",
        category = ToolCategory.DIAGRAM
    )

    private fun listDiagrams() = InternalToolDefinition(
        name = "list_drawio_diagrams",
        description = "List all .drawio diagram files in the diagrams directory. " +
            "[Permission: VIEW_ANALYSIS] [Role: Reader]",
        inputSchema = buildSchema(),
        requiredPermission = "VIEW_ANALYSIS",
        requiredRole = "Reader",
        category = ToolCategory.DIAGRAM
    )

    private fun buildCreateSchema() = buildSchema(
        properties = buildJsonObject {
            put("xml", stringProp(
                "Complete draw.io mxGraphModel XML content. " +
                "Must include root cells id=0 and id=1."
            ))
            put("filename", stringProp(
                "Output filename without extension (e.g. 'login-flow'). " +
                "Use lowercase with hyphens."
            ))
            put("diagramType", enumProp(
                "Type of diagram being created", diagramTypes
            ))
            put("format", enumProp(
                "Output format. Default: drawio", exportFormats
            ))
        },
        required = listOf("xml", "filename")
    )

    private fun buildCreateDescription(): String = """
        |Create a native Draw.io diagram file (.drawio) from mxGraphModel XML.
        |
        |INSTRUCTIONS — follow these exactly when generating XML:
        |
        |1. Every diagram MUST have this structure:
        |   <mxGraphModel adaptiveColors="auto">
        |     <root>
        |       <mxCell id="0"/>
        |       <mxCell id="1" parent="0"/>
        |       <!-- cells here with parent="1" -->
        |     </root>
        |   </mxGraphModel>
        |
        |2. NEVER include XML comments (<!-- -->) in the output.
        |3. Every edge MUST have a child <mxGeometry relative="1" as="geometry"/>.
        |4. Use unique id values for each mxCell.
        |5. Escape special chars: &amp; &lt; &gt; &quot;
        |6. Use html=1 in style when value contains HTML tags.
        |7. Use &#xa; or &lt;br&gt; for line breaks, NEVER \n.
        |
        |GRID for node placement:
        |  Column x = col_index * 180 + 40
        |  Row y = row_index * 120 + 40
        |  Rectangles: 140x60, Diamonds: 140x80, Circles: 60x60
        |
        |EDGE STYLES:
        |  Flowcharts: edgeStyle=orthogonalEdgeStyle;rounded=1;
        |  ER diagrams: edgeStyle=entityRelationEdgeStyle;
        |  UML class/sequence: no edgeStyle (straight)
        |  Mind maps: curved=1;
        |
        |CONTAINERS: Use parent="containerId" on children.
        |  Group (invisible): style="group;"
        |  Swimlane (titled): style="swimlane;startSize=30;"
        |  Custom: add container=1;pointerEvents=0; to any shape
        |
        |COMMON SHAPES:
        |  rounded=1;whiteSpace=wrap;html=1; — Rounded rectangle
        |  rhombus;whiteSpace=wrap;html=1; — Diamond/decision
        |  ellipse;whiteSpace=wrap;html=1; — Circle/oval
        |  shape=cylinder3;whiteSpace=wrap;html=1; — Database
        |  shape=mxgraph.flowchart.document — Document
        |  swimlane;startSize=30; — Container with title
        |
        |[Permission: VIEW_ANALYSIS] [Role: Reader]
    """.trimMargin()
}
