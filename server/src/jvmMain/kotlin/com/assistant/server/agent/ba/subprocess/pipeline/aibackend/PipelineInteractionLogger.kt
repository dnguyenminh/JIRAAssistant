package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.PhaseId
import org.slf4j.LoggerFactory

/**
 * Logs detailed BA Agent ↔ AI interaction for each pipeline phase.
 * Uses SLF4J logger "PipelineInteraction" — output goes to existing
 * logging system (logback config determines file/console destination).
 */
object PipelineInteractionLogger {

    private val log = LoggerFactory.getLogger("PipelineInteraction")

    fun create(ticketId: String): InteractionLog = InteractionLog(ticketId)

    class InteractionLog(private val ticketId: String) {
        private var turnCount = 0

        fun logPhaseStart(phaseId: PhaseId, promptSize: Int) {
            turnCount = 0
            log.info("[{}] ━━━ PHASE: {} ━━━ (prompt: {} chars)", ticketId, phaseId, promptSize)
        }

        fun logPrompt(label: String, content: String) {
            turnCount++
            val preview = content.take(500).replace("\n", "\\n")
            log.info("[{}] → [{}] {} ({} chars): {}", ticketId, turnCount, label, content.length, preview)
        }

        fun logResponse(content: String) {
            val preview = content.take(800).replace("\n", "\\n")
            log.info("[{}] ← [{}] AI_RESPONSE ({} chars): {}", ticketId, turnCount, content.length, preview)
        }

        fun logToolCall(toolName: String, params: Map<String, String>) {
            log.info("[{}] → [{}] TOOL_CALL: {} params={}", ticketId, turnCount, toolName, params)
        }

        fun logToolResult(toolName: String, success: Boolean, size: Int) {
            log.info("[{}] ← [{}] TOOL_RESULT: {} success={} ({} chars)", ticketId, turnCount, toolName, success, size)
        }

        fun logAttachmentDetails(files: Map<String, Int>) {
            if (files.isEmpty()) {
                log.info("[{}]    ATTACHMENTS: none found", ticketId)
            } else {
                log.info("[{}]    ATTACHMENTS: {} files found", ticketId, files.size)
                files.forEach { (filename, chunkCount) ->
                    log.info("[{}]      - {} ({} chunks)", ticketId, filename, chunkCount)
                }
            }
        }

        fun logPhaseEnd(phaseId: PhaseId, outputSize: Int, toolCalls: Int) {
            log.info("[{}] ━━━ PHASE END: {} — output={} chars, tools={}, turns={}", ticketId, phaseId, outputSize, toolCalls, turnCount)
        }

        fun logAssembly(brdSize: Int, diagramSize: Int, finalSize: Int) {
            log.info("[{}] ━━━ ASSEMBLY: brd={} chars, diagrams={} chars → final={} chars", ticketId, brdSize, diagramSize, finalSize)
        }
    }
}
