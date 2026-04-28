package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

/**
 * Draw.io XML diagram templates and placement instructions for BRD.
 *
 * Provides 4 diagram types: Sequence, Class, Activity, Deployment.
 * Each template is minimal but valid draw.io XML that can be imported
 * directly into draw.io/diagrams.net without modification.
 */

// ── Main entry point ────────────────────────────────────

internal fun StringBuilder.appendDiagramInstructions(detected: DetectedTools) {
    appendLine("## DIAGRAM INSTRUCTIONS")
    appendLine()
    // Inject draw.io skill reference from resource files
    val skillContent = DrawioSkillLoader.getSkillContent()
    if (skillContent.isNotBlank()) {
        appendLine(skillContent)
        appendLine()
    }
    if (detected.hasDiagramTools) {
        appendMcpDiagramInstructions(detected)
    } else {
        appendInlineDiagramInstructions()
    }
    appendDiagramPlacementRules()
    appendDiagramFallbackRules()
}

// ── Dynamic diagram strategy ────────────────────────────

private fun StringBuilder.appendMcpDiagramInstructions(detected: DetectedTools) {
    appendLine("You have a diagram generation tool available: `${detected.diagramTool}`")
    appendLine("**PREFER using this tool** to generate diagrams instead of writing XML manually.")
    appendLine("Call the tool for each diagram needed, then embed the result in the BRD.")
    appendLine()
    appendLine("Generate at least 2 diagrams:")
    appendLine("1. **Process Flow** — AS-IS or TO-BE process flow")
    appendLine("2. **System/Data diagram** — system architecture or data flow")
    appendLine()
    appendLine("If the tool fails or returns an error, fall back to generating draw.io XML directly.")
    appendLine()
    appendInlineXmlFallbackNote()
}

private fun StringBuilder.appendInlineDiagramInstructions() {
    appendLine("No diagram tool available — generate draw.io XML directly.")
    appendLine("Each diagram MUST be a ```xml code block containing <mxGraphModel>.")
    appendLine("Use actual ticket IDs, service names, and data as node labels.")
    appendLine("Do NOT write 'The following diagram illustrates...' — generate the XML directly.")
    appendLine()
    appendLine("Generate at least 2 diagrams:")
    appendLine("1. **Process Flow** — AS-IS or TO-BE process flow")
    appendLine("2. **System/Data diagram** — system architecture or data flow")
    appendLine()
    appendLine("Use these templates as reference for valid XML structure:")
    appendLine()
    appendActivityDiagramTemplate()
    appendSequenceDiagramTemplate()
}

private fun StringBuilder.appendInlineXmlFallbackNote() {
    appendLine("**Fallback XML format** (if tool fails):")
    appendLine("```xml")
    appendLine("""<mxGraphModel><root><mxCell id="0"/><mxCell id="1" parent="0"/>""")
    appendLine("""<mxCell id="2" value="Step 1" style="rounded=1;" vertex="1" parent="1">""")
    appendLine("""<mxGeometry x="50" y="50" width="120" height="60" as="geometry"/></mxCell>""")
    appendLine("</root></mxGraphModel>")
    appendLine("```")
    appendLine()
}

// ── Diagram templates (used for inline fallback) ────────

private fun StringBuilder.appendSequenceDiagramTemplate() {
    appendLine("### Sequence Diagram Template")
    appendLine("```xml")
    appendLine("""<mxGraphModel><root>""")
    appendLine("""  <mxCell id="0"/>""")
    appendLine("""  <mxCell id="1" parent="0"/>""")
    appendLine("""  <mxCell id="s1" value="Actor A" style="shape=rectangle;" vertex="1" parent="1">""")
    appendLine("""    <mxGeometry x="100" y="20" width="120" height="40" as="geometry"/>""")
    appendLine("""  </mxCell>""")
    appendLine("""  <mxCell id="s2" value="System B" style="shape=rectangle;" vertex="1" parent="1">""")
    appendLine("""    <mxGeometry x="350" y="20" width="120" height="40" as="geometry"/>""")
    appendLine("""  </mxCell>""")
    appendLine("""  <mxCell id="s3" value="Request" style="endArrow=block;" edge="1" source="s1" target="s2" parent="1">""")
    appendLine("""    <mxGeometry relative="1" as="geometry"/>""")
    appendLine("""  </mxCell>""")
    appendLine("""  <mxCell id="s4" value="Response" style="endArrow=open;dashed=1;" edge="1" source="s2" target="s1" parent="1">""")
    appendLine("""    <mxGeometry relative="1" as="geometry"/>""")
    appendLine("""  </mxCell>""")
    appendLine("""</root></mxGraphModel>""")
    appendLine("```")
    appendLine()
}

// ── Class Diagram ───────────────────────────────────────

private fun StringBuilder.appendClassDiagramTemplate() {
    appendLine("### Class Diagram Template")
    appendLine("```xml")
    appendLine("""<mxGraphModel><root>""")
    appendLine("""  <mxCell id="0"/>""")
    appendLine("""  <mxCell id="1" parent="0"/>""")
    appendLine("""  <mxCell id="c1" value="ClassName&#xa;- field: Type&#xa;+ method(): Return" style="shape=rectangle;align=left;" vertex="1" parent="1">""")
    appendLine("""    <mxGeometry x="100" y="50" width="200" height="80" as="geometry"/>""")
    appendLine("""  </mxCell>""")
    appendLine("""  <mxCell id="c2" value="RelatedClass&#xa;- id: Long&#xa;+ getName(): String" style="shape=rectangle;align=left;" vertex="1" parent="1">""")
    appendLine("""    <mxGeometry x="400" y="50" width="200" height="80" as="geometry"/>""")
    appendLine("""  </mxCell>""")
    appendLine("""  <mxCell id="c3" value="1..*" style="endArrow=diamondThin;" edge="1" source="c1" target="c2" parent="1">""")
    appendLine("""    <mxGeometry relative="1" as="geometry"/>""")
    appendLine("""  </mxCell>""")
    appendLine("""</root></mxGraphModel>""")
    appendLine("```")
    appendLine()
}

// ── Activity Diagram ────────────────────────────────────

private fun StringBuilder.appendActivityDiagramTemplate() {
    appendLine("### Activity Diagram Template")
    appendLine("```xml")
    appendLine("""<mxGraphModel><root>""")
    appendLine("""  <mxCell id="0"/>""")
    appendLine("""  <mxCell id="1" parent="0"/>""")
    appendLine("""  <mxCell id="a1" value="" style="ellipse;fillColor=#000000;" vertex="1" parent="1">""")
    appendLine("""    <mxGeometry x="200" y="20" width="30" height="30" as="geometry"/>""")
    appendLine("""  </mxCell>""")
    appendLine("""  <mxCell id="a2" value="Action Step" style="shape=rectangle;rounded=1;" vertex="1" parent="1">""")
    appendLine("""    <mxGeometry x="160" y="80" width="120" height="40" as="geometry"/>""")
    appendLine("""  </mxCell>""")
    appendLine("""  <mxCell id="a3" value="Decision?" style="shape=rhombus;" vertex="1" parent="1">""")
    appendLine("""    <mxGeometry x="175" y="160" width="80" height="80" as="geometry"/>""")
    appendLine("""  </mxCell>""")
    appendActivityDiagramEndAndEdges()
    appendLine("""</root></mxGraphModel>""")
    appendLine("```")
    appendLine()
}

private fun StringBuilder.appendActivityDiagramEndAndEdges() {
    appendLine("""  <mxCell id="a4" value="" style="ellipse;fillColor=#000000;strokeColor=#ffffff;" vertex="1" parent="1">""")
    appendLine("""    <mxGeometry x="200" y="280" width="30" height="30" as="geometry"/>""")
    appendLine("""  </mxCell>""")
    appendLine("""  <mxCell id="a5" value="" edge="1" source="a1" target="a2" parent="1">""")
    appendLine("""    <mxGeometry relative="1" as="geometry"/>""")
    appendLine("""  </mxCell>""")
    appendLine("""  <mxCell id="a6" value="" edge="1" source="a2" target="a3" parent="1">""")
    appendLine("""    <mxGeometry relative="1" as="geometry"/>""")
    appendLine("""  </mxCell>""")
    appendLine("""  <mxCell id="a7" value="Yes" edge="1" source="a3" target="a4" parent="1">""")
    appendLine("""    <mxGeometry relative="1" as="geometry"/>""")
    appendLine("""  </mxCell>""")
}

// ── Deployment Diagram ──────────────────────────────────

private fun StringBuilder.appendDeploymentDiagramTemplate() {
    appendLine("### Deployment Diagram Template")
    appendLine("```xml")
    appendLine("""<mxGraphModel><root>""")
    appendLine("""  <mxCell id="0"/>""")
    appendLine("""  <mxCell id="1" parent="0"/>""")
    appendLine("""  <mxCell id="d1" value="Web Server" style="shape=mxgraph.aws3.server;" vertex="1" parent="1">""")
    appendLine("""    <mxGeometry x="100" y="50" width="140" height="60" as="geometry"/>""")
    appendLine("""  </mxCell>""")
    appendLine("""  <mxCell id="d2" value="Database" style="shape=cylinder3;" vertex="1" parent="1">""")
    appendLine("""    <mxGeometry x="380" y="50" width="100" height="80" as="geometry"/>""")
    appendLine("""  </mxCell>""")
    appendLine("""  <mxCell id="d3" value="HTTPS" style="endArrow=block;" edge="1" source="d1" target="d2" parent="1">""")
    appendLine("""    <mxGeometry relative="1" as="geometry"/>""")
    appendLine("""  </mxCell>""")
    appendLine("""</root></mxGraphModel>""")
    appendLine("```")
    appendLine()
}

// ── Placement & Fallback Rules ──────────────────────────

private fun StringBuilder.appendDiagramPlacementRules() {
    appendLine("### Diagram Placement Rules")
    appendLine("**CRITICAL:** Diagrams MUST be placed INSIDE the existing BRD sections.")
    appendLine("Do NOT create new ## headings for diagrams.")
    appendLine("Embed the ```xml code block directly within the relevant section content:")
    appendLine("- **Process Flow diagram** → inside \"Existing Processes\" section")
    appendLine("- **Activity diagram** → inside \"Project Requirements\" > Process Overview")
    appendLine("- **Class/Data diagram** → inside \"Project Requirements\" > Data Requirements")
    appendLine("- **Deployment diagram** → inside \"Appendix\" section")
    appendLine()
}

private fun StringBuilder.appendDiagramFallbackRules() {
    appendLine("### Diagram Fallback")
    appendLine("If you lack sufficient data for a diagram type, output:")
    appendLine("`[Diagram không khả dụng: thiếu dữ liệu về {topic}]`")
    appendLine("Do NOT generate empty or invalid XML diagrams.")
    appendLine()
    appendLine("XML validity rules:")
    appendLine("- Root element MUST be `<mxGraphModel>`")
    appendLine("- Every `<mxCell>` MUST have a unique `id` attribute")
    appendLine("- Every edge (`edge=\"1\"`) MUST have valid `source` and `target`")
    appendLine("- Include `<mxGeometry>` for positioning on all nodes")
}
