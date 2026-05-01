package com.assistant.server.ai

import com.assistant.ai.AIAgent
import com.assistant.ai.AIContext
import com.assistant.ai.AIResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AI Agent that delegates to the GitHub Copilot CLI binary.
 * Spawns `gh copilot` process per request, sends prompt via stdin,
 * reads response from stdout.
 */
class CopilotCliAgent(
    private val cliPath: String,
    private val model: String = "copilot"
) : AIAgent {

    companion object {
        private const val TIMEOUT_MS = 240_000L
    }

    private val logger = org.slf4j.LoggerFactory.getLogger(CopilotCliAgent::class.java)

    override suspend fun analyze(
        prompt: String, context: AIContext?
    ): AIResult = withContext(Dispatchers.IO) {
        try {
            val fullPrompt = buildPrompt(prompt, context)
            logger.info("Copilot CLI call: model=$model, prompt=${fullPrompt.length} chars")
            val cmd = if (model.isBlank() || model == "auto") {
                listOf(cliPath)
            } else {
                listOf(cliPath, "-m", model)
            }
            val result = executeCliCommand(cmd, input = fullPrompt)
            if (result.exitCode == 0 && result.stdout.isNotBlank()) {
                logger.info("Copilot CLI success: response=${result.stdout.length} chars")
                AIResult.Success(result.stdout.trim())
            } else {
                val err = result.stderr.ifBlank { "Empty response" }
                logger.warn("Copilot CLI failed: exit=${result.exitCode}, stderr=$err")
                AIResult.Failure("Copilot CLI error (exit ${result.exitCode}): $err")
            }
        } catch (e: Exception) {
            logger.error("Copilot CLI exception: ${e.message}", e)
            AIResult.Failure("Copilot CLI error: ${e.message}")
        }
    }

    /**
     * Test connectivity by running CLI with `--version`.
     * Returns version string on success, null on failure.
     */
    suspend fun testConnection(): String? = withContext(Dispatchers.IO) {
        try {
            val result = executeCliCommand(
                listOf(cliPath, "--version"), timeout = 15_000L
            )
            if (result.exitCode == 0 && result.stdout.isNotBlank()) {
                "Connected — ${result.stdout.trim()}"
            } else null
        } catch (_: Exception) { null }
    }

    override fun getAgentName(): String = "Copilot CLI - $model"

    private fun buildPrompt(
        prompt: String, context: AIContext?
    ): String {
        if (context == null) return prompt
        val ctx = context.tickets.joinToString("\n") {
            "[${it.id}] ${it.summary}: ${it.description}"
        }
        return "Context:\n$ctx\n\nUser Request: $prompt"
    }

    private fun executeCliCommand(
        command: List<String>,
        input: String? = null,
        timeout: Long = TIMEOUT_MS
    ): ProcessResult {
        val isWindows = System.getProperty("os.name")
            .lowercase().contains("win")
        val actualCommand = if (isWindows) {
            listOf("cmd", "/c") + command
        } else {
            command
        }

        val pb = ProcessBuilder(actualCommand)
            .redirectErrorStream(false)
        val process = pb.start()

        if (input != null) {
            process.outputStream.bufferedWriter().use { it.write(input) }
        }

        val stdoutFuture = java.util.concurrent.CompletableFuture.supplyAsync {
            process.inputStream.bufferedReader().readText()
        }
        val stderrFuture = java.util.concurrent.CompletableFuture.supplyAsync {
            process.errorStream.bufferedReader().readText()
        }

        val completed = process.waitFor(
            timeout, java.util.concurrent.TimeUnit.MILLISECONDS
        )
        return handleProcessCompletion(completed, process, stdoutFuture, stderrFuture, timeout)
    }

    private fun handleProcessCompletion(
        completed: Boolean,
        process: Process,
        stdoutFuture: java.util.concurrent.CompletableFuture<String>,
        stderrFuture: java.util.concurrent.CompletableFuture<String>,
        timeout: Long
    ): ProcessResult {
        if (!completed) {
            process.destroyForcibly()
            val partialOut = try { stdoutFuture.getNow("") } catch (_: Exception) { "" }
            val partialErr = try { stderrFuture.getNow("") } catch (_: Exception) { "" }
            logger.warn("Copilot CLI timeout: partial stdout=${partialOut.length} chars, stderr=${partialErr.take(500)}")
            return ProcessResult(1, partialOut, "Timeout after ${timeout}ms. stderr: ${partialErr.take(500)}")
        }
        return ProcessResult(process.exitValue(), stdoutFuture.get(), stderrFuture.get())
    }

    private data class ProcessResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String
    )
}
