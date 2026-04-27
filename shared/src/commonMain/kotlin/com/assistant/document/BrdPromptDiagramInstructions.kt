package com.assistant.document

/**
 * Draw.io diagram instructions for BRD prompt generation.
 * Specifies 3 required diagrams matching Carleton ITS template.
 *
 * Requirements: 10.1, 10.7
 */

/** Req 10.1, 10.7: Request 3 draw.io diagrams matching Carleton ITS template. */
fun StringBuilder.appendDiagramInstructions() {
    appendLine("=== DIAGRAM INSTRUCTIONS ===")
    appendBrdDiagramRequirements()
    appendBrdDiagramFormat()
    appendBrdDiagramExample()
    appendLine()
}

/** List the 3 required BRD diagrams and their target sections. */
private fun StringBuilder.appendBrdDiagramRequirements() {
    appendLine("Embed 3 draw.io diagrams as raw XML in ```xml code blocks:")
    appendLine("1. Process Flow → in section Existing Processes > Summary Process Narrative")
    appendLine("2. Requirements Traceability → in section Project Requirements > Process Overview")
    appendLine("3. Stakeholder Map → in section Project Overview > Project Contributors")
}

/** Specify the exact XML format and labeling rules. */
private fun StringBuilder.appendBrdDiagramFormat() {
    appendLine("Format: each diagram MUST be a ```xml code block containing <mxGraphModel>.")
    appendLine("Use actual ticket IDs, service names, and data from CONTEXT as node labels.")
    appendLine("Do NOT use placeholder or generic text. Do NOT output JSON metadata.")
}

/** Provide a compact draw.io XML example for BRD diagrams. */
private fun StringBuilder.appendBrdDiagramExample() {
    appendLine("Example (embed inline in the relevant section):")
    appendLine("```xml")
    appendLine("""<mxGraphModel><root><mxCell id="0"/><mxCell id="1" parent="0"/>""")
    appendLine("""<mxCell id="2" value="User" style="rounded=1;" vertex="1" parent="1">""")
    appendLine("""<mxGeometry x="50" y="50" width="120" height="60" as="geometry"/></mxCell>""")
    appendLine("""<mxCell id="3" value="System" style="rounded=1;" vertex="1" parent="1">""")
    appendLine("""<mxGeometry x="250" y="50" width="120" height="60" as="geometry"/></mxCell>""")
    appendLine("""<mxCell id="4" value="Request" edge="1" source="2" target="3" parent="1"/>""")
    appendLine("</root></mxGraphModel>")
    appendLine("```")
}
