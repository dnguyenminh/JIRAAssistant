package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

/**
 * Pure Kotlin assembler that merges BRD markdown (Phase 2)
 * with diagram XML blocks (Phase 3) into final BRD.
 *
 * No AI calls — deterministic string manipulation only.
 */
class BrdAssembler {

    /**
     * Assemble final BRD by merging markdown with diagram blocks.
     * If [diagramOutput] is null/blank → return [brdMarkdown] as-is (graceful degradation).
     */
    fun assemble(brdMarkdown: String, diagramOutput: String?): String {
        if (diagramOutput.isNullOrBlank()) return brdMarkdown
        val diagrams = parseDiagramBlocks(diagramOutput)
        if (diagrams.isEmpty()) return appendRawDiagrams(brdMarkdown, diagramOutput)
        val hasPlaceholders = DIAGRAM_LABELS.any { label ->
            brdMarkdown.contains("$PLACEHOLDER_PREFIX$label$PLACEHOLDER_SUFFIX")
        }
        return if (hasPlaceholders) {
            replacePlaceholders(brdMarkdown, diagrams)
        } else {
            injectDiagramsBySection(brdMarkdown, diagrams)
        }
    }

    /** Inject parsed diagrams into matching BRD sections by label. */
    internal fun injectDiagramsBySection(
        brdMarkdown: String,
        diagrams: Map<String, String>
    ): String {
        var result = brdMarkdown
        for ((label, xml) in diagrams) {
            val target = SECTION_TARGETS[label] ?: continue
            val idx = findSectionInsertPoint(result, target)
            if (idx >= 0) {
                result = result.substring(0, idx) +
                    "\n\n$xml\n\n" + result.substring(idx)
            }
        }
        return result
    }

    /** Find insert point: end of target section content (before next ## heading). */
    private fun findSectionInsertPoint(md: String, sectionName: String): Int {
        val sectionIdx = md.indexOf(sectionName, ignoreCase = true)
        if (sectionIdx < 0) return -1
        val afterSection = sectionIdx + sectionName.length
        val nextHeading = md.indexOf("\n## ", afterSection)
        return if (nextHeading >= 0) nextHeading else md.length
    }

    private fun appendRawDiagrams(brd: String, raw: String): String {
        val idx = brd.indexOf("## 7. Appendix", ignoreCase = true)
            .takeIf { it >= 0 }
            ?: brd.indexOf("## Appendix", ignoreCase = true).takeIf { it >= 0 }
        return if (idx != null) {
            brd.substring(0, idx) + "## Diagrams\n\n$raw\n\n" + brd.substring(idx)
        } else "$brd\n\n## Diagrams\n\n$raw"
    }

    /**
     * Parse Phase 3 output: find `<!-- DIAGRAM:LABEL -->` followed by ```xml code blocks.
     * Returns Map of label → XML content.
     */
    internal fun parseDiagramBlocks(diagramOutput: String): Map<String, String> {
        // Try fenced code blocks first: <!-- DIAGRAM:LABEL --> ```xml ... ```
        val fencedPattern = Regex(
            """<!-- DIAGRAM:([\w\s]+?) -->\s*```xml\s*\n([\s\S]*?)```""",
            RegexOption.MULTILINE
        )
        val result = mutableMapOf<String, String>()
        fencedPattern.findAll(diagramOutput).forEach { match ->
            val label = match.groupValues[1].trim().uppercase().replace(" ", "_")
            val xml = match.groupValues[2].trim()
            if (label.isNotBlank() && xml.isNotBlank()) result[label] = xml
        }
        if (result.isNotEmpty()) return result
        // Fallback: <!-- DIAGRAM:LABEL --> followed directly by <mxGraphModel>
        val rawPattern = Regex(
            """<!-- DIAGRAM:([\w\s]+?) -->\s*(<mxGraphModel>[\s\S]*?</mxGraphModel>)""",
            RegexOption.MULTILINE
        )
        rawPattern.findAll(diagramOutput).forEach { match ->
            val label = match.groupValues[1].trim().uppercase().replace(" ", "_")
            val xml = match.groupValues[2].trim()
            if (label.isNotBlank() && xml.isNotBlank()) result[label] = xml
        }
        return result
    }

    /**
     * Replace placeholder markers in BRD with matching diagrams or fallback text.
     * Output contains zero remaining placeholder markers.
     */
    internal fun replacePlaceholders(
        brdMarkdown: String,
        diagrams: Map<String, String>
    ): String {
        var result = brdMarkdown
        DIAGRAM_LABELS.forEach { label ->
            val placeholder = "$PLACEHOLDER_PREFIX$label$PLACEHOLDER_SUFFIX"
            val replacement = diagrams[label]
                ?: FALLBACK_TEXT
            result = result.replace(placeholder, replacement)
        }
        return result
    }

    companion object {
        val DIAGRAM_LABELS = listOf(
            "PROCESS_FLOW", "ACTIVITY",
            "DATA_MODEL", "DEPLOYMENT"
        )
        const val PLACEHOLDER_PREFIX = "<!-- DIAGRAM:"
        const val PLACEHOLDER_SUFFIX = " -->"
        const val FALLBACK_TEXT = "[Diagram không khả dụng]"

        /** Map diagram labels to BRD section headings for injection. */
        internal val SECTION_TARGETS = mapOf(
            "PROCESS_FLOW" to "Existing Processes",
            "ACTIVITY" to "Project Requirements",
            "DATA_MODEL" to "Data Requirements",
            "DEPLOYMENT" to "Appendix"
        )
    }
}
