package com.assistant.server.ai

import com.assistant.ai.AIAgent
import com.assistant.ai.AIContext
import com.assistant.ai.AIResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AI Agent that delegates to the Amazon Kiro CLI binary.
 * Spawns Kiro CLI process per request, sends prompt via stdin,
 * reads response from stdout.
 */
class KiroCliAgent(
    private val cliPath: String,
    private val model: String = "kiro"
) : AIAgent {

    companion object {
        private const val TIMEOUT_MS = 240_000L
    }

    private val logger = org.slf4j.LoggerFactory.getLogger(KiroCliAgent::class.java)

    override suspend fun analyze(
        prompt: String, context: AIContext?
    ): AIResult = withContext(Dispatchers.IO) {
        try {
            val fullPrompt = buildPrompt(prompt, context)
            logger.info("Kiro CLI call: model=$model, prompt=${fullPrompt.length} chars")
            val cmd = if (model.isBlank() || model == "auto") {
                listOf(cliPath)
            } else {
                listOf(cliPath, "-m", model)
            }
            val result = executeCliCommand(cmd, input = fullPrompt, timeout = TIMEOUT_MS, logger = logger)
            if (result.exitCode == 0 && result.stdout.isNotBlank()) {
                logger.info("Kiro CLI success: response=${result.stdout.length} chars")
                AIResult.Success(result.stdout.trim())
            } else {
                val err = result.stderr.ifBlank { "Empty response" }
                logger.warn("Kiro CLI failed: exit=${result.exitCode}, stderr=$err")
                AIResult.Failure("Kiro CLI error (exit ${result.exitCode}): $err")
            }
        } catch (e: Exception) {
            logger.error("Kiro CLI exception: ${e.message}", e)
            AIResult.Failure("Kiro CLI error: ${e.message}")
        }
    }

    /**
     * Test connectivity by running CLI with `--version`.
     * Returns version string on success, null on failure.
     */
    suspend fun testConnection(): String? = withContext(Dispatchers.IO) {
        try {
            val result = executeCliCommand(
                listOf(cliPath, "--version"), timeout = 15_000L, logger = logger
            )
            if (result.exitCode == 0 && result.stdout.isNotBlank()) {
                "Connected — ${result.stdout.trim()}"
            } else null
        } catch (_: Exception) { null }
    }

    override fun getAgentName(): String = "Kiro CLI - $model"

    private fun buildPrompt(
        prompt: String, context: AIContext?
    ): String {
        if (context == null) return prompt
        val ctx = context.tickets.joinToString("\n") {
            "[${it.id}] ${it.summary}: ${it.description}"
        }
        return "Context:\n$ctx\n\nUser Request: $prompt"
    }
}
