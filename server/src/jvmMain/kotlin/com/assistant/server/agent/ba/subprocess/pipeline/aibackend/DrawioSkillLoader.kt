package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

import org.slf4j.LoggerFactory

/**
 * Provides condensed draw.io XML rules for Phase 3 prompts.
 * Only critical rules — no complex patterns (swimlanes, containers)
 * that confuse smaller AI models.
 */
object DrawioSkillLoader {

    private val log = LoggerFactory.getLogger(DrawioSkillLoader::class.java)

    /** Condensed XML rules — hardcoded for reliability. */
    fun getSkillContent(): String = SKILL_CONTENT

    private const val SKILL_CONTENT = """## DRAW.IO XML RULES (MUST FOLLOW)

### Structure
```xml
<mxGraphModel>
  <root>
    <mxCell id="0"/>
    <mxCell id="1" parent="0"/>
    <!-- nodes and edges here, parent="1" -->
  </root>
</mxGraphModel>
```

### Node positioning (rigid grid)
- x = column * 180 + 40 (col 0=40, col 1=220, col 2=400)
- y = row * 120 + 40 (row 0=40, row 1=160, row 2=280)
- Rectangle: width=140, height=60
- Diamond: width=140, height=80

### Edge rules (CRITICAL)
Every edge MUST have a <mxGeometry> child — self-closing edges are INVALID:
```xml
<mxCell id="e1" edge="1" source="n1" target="n2" parent="1"
        style="edgeStyle=orthogonalEdgeStyle;rounded=1;html=1;">
  <mxGeometry relative="1" as="geometry"/>
</mxCell>
```
- NEVER duplicate attributes (e.g. edge="1" twice)
- NEVER add exitX/exitY/entryX/entryY — routing is automatic

### XML well-formedness (CRITICAL)
- Escape & as &amp; in value attributes
- Escape < as &lt; and > as &gt;
- Every mxCell MUST have unique id
- NEVER include XML comments <!-- -->
- Use html=1 in style for all cells"""
}
