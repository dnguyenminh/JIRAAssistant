package com.assistant.agent.ba.models

import kotlinx.serialization.Serializable

/**
 * Configuration for a BA subprocess task execution.
 *
 * Defines the input parameters for the BA_Subprocess_Orchestrator:
 * which ticket to analyze, what document type to produce, and
 * execution constraints (tool call limit, timeout, CLI backend).
 */
@Serializable
data class BATaskConfig(
    val rootTicketId: String,
    val docType: String = "BRD",
    val maxToolCalls: Int = 30,
    val taskTimeoutSeconds: Int = 600,
    val cliBackend: String = "ollama"
) {
    companion object {
        val VALID_DOC_TYPES = setOf("BRD", "FSD", "SLIDES")
        val VALID_CLI_BACKENDS = setOf("gemini", "copilot", "kiro", "ollama")
    }

    init {
        require(rootTicketId.isNotBlank()) {
            "rootTicketId must be non-blank"
        }
        require(docType in VALID_DOC_TYPES) {
            "docType must be one of $VALID_DOC_TYPES, got: $docType"
        }
        require(maxToolCalls > 0) {
            "maxToolCalls must be > 0, got: $maxToolCalls"
        }
        require(taskTimeoutSeconds > 0) {
            "taskTimeoutSeconds must be > 0, got: $taskTimeoutSeconds"
        }
        require(cliBackend in VALID_CLI_BACKENDS) {
            "cliBackend must be one of $VALID_CLI_BACKENDS, got: $cliBackend"
        }
    }
}
