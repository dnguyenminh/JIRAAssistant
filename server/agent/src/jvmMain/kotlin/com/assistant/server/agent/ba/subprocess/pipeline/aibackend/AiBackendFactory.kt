package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

import com.assistant.server.agent.ba.subprocess.CliBackendResolver
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.ollama.OllamaApiClient
import org.slf4j.LoggerFactory

/**
 * Factory that creates the correct [AiBackend] implementation
 * based on the configured backend name.
 *
 * Mapping:
 * - `"gemini"` → [GeminiCliClientImpl]
 * - `"copilot"` → [CopilotCliClientImpl]
 * - `"kiro"` → [KiroCliClientImpl]
 * - `"ollama"` → [OllamaApiClient] with baseUrl/model from settings
 *
 * Returns [Result.failure] for unsupported backend names.
 */
class AiBackendFactory(
    private val cliBackendResolver: CliBackendResolver
) {

    private val log = LoggerFactory.getLogger(AiBackendFactory::class.java)

    companion object {
        private const val DEFAULT_OLLAMA_BASE_URL = "http://localhost:11434"
        private const val DEFAULT_OLLAMA_MODEL = "batiai/gemma4-e2b:q4"

        val SUPPORTED_BACKENDS = setOf(
            "gemini", "copilot", "kiro", "ollama"
        )
    }

    /**
     * Creates an [AiBackend] for the given [cliBackend] name.
     *
     * @return [Result.success] with the backend, or [Result.failure]
     *         if the backend name is unsupported.
     */
    suspend fun create(cliBackend: String): Result<AiBackend> {
        log.info("Creating AI backend for: {}", cliBackend)

        if (cliBackend !in SUPPORTED_BACKENDS) {
            return Result.failure(
                IllegalArgumentException(
                    "Unsupported AI backend: '$cliBackend'. " +
                        "Supported: $SUPPORTED_BACKENDS"
                )
            )
        }

        return when (cliBackend) {
            "gemini" -> createGemini()
            "copilot" -> createCopilot()
            "kiro" -> createKiro()
            "ollama" -> createOllama()
            else -> unsupportedError(cliBackend)
        }
    }

    private suspend fun createGemini(): Result<AiBackend> {
        log.debug("Creating GeminiCliClientImpl")
        val model = cliBackendResolver.resolveModel("gemini")
        return Result.success(GeminiCliClientImpl(model))
    }

    private fun createCopilot(): Result<AiBackend> {
        log.debug("Creating CopilotCliClientImpl")
        return Result.success(CopilotCliClientImpl())
    }

    private fun createKiro(): Result<AiBackend> {
        log.debug("Creating KiroCliClientImpl")
        return Result.success(KiroCliClientImpl())
    }

    private suspend fun createOllama(): Result<AiBackend> {
        log.debug("Creating OllamaApiClient")
        val model = cliBackendResolver.resolveModel("ollama")
            ?: DEFAULT_OLLAMA_MODEL
        return Result.success(
            OllamaApiClient(
                baseUrl = DEFAULT_OLLAMA_BASE_URL,
                model = model
            )
        )
    }

    private fun unsupportedError(name: String): Result<AiBackend> =
        Result.failure(
            IllegalArgumentException("Unsupported: $name")
        )
}
