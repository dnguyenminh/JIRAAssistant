package com.assistant.server.agent.ba.subprocess

import com.assistant.agent.subprocess.SubprocessConfig
import com.assistant.ai.ProviderType
import com.assistant.kb.ProviderConfigRepository
import com.assistant.settings.SettingsRepository

/**
 * Resolves a CLI backend name (e.g., "gemini", "copilot") into a
 * [SubprocessConfig] by reading CLI paths and models.
 *
 * Resolution order: [ProviderConfigRepository] (Integrations page)
 * first, then [SettingsRepository] as fallback.
 *
 * Returns [Result.failure] when the backend is unknown or the CLI path
 * is missing from both sources.
 */
class CliBackendResolver(
    private val settingsRepository: SettingsRepository,
    private val providerConfigRepo: ProviderConfigRepository? = null
) {

    companion object {
        private const val AGENT_TYPE = "ba-agent"

        // Settings keys per backend
        private const val GEMINI_CLI_PATH = "ai_cli_path"
        private const val GEMINI_CLI_MODEL = "ai_cli_model"
        private const val COPILOT_CLI_PATH = "copilot_cli_path"
        private const val KIRO_CLI_PATH = "kiro_cli_path"
        private const val OLLAMA_CLI_PATH = "ollama_cli_path"
        private const val OLLAMA_CLI_MODEL = "ollama_cli_model"

        val SUPPORTED_BACKENDS = setOf(
            "gemini", "copilot", "kiro", "ollama"
        )
    }

    /**
     * Resolves [cliBackend] to a [SubprocessConfig].
     *
     * @return [Result.success] with the config, or [Result.failure]
     *         if the backend is invalid or CLI path is missing.
     */
    suspend fun resolve(cliBackend: String): Result<SubprocessConfig> {
        if (cliBackend !in SUPPORTED_BACKENDS) {
            return Result.failure(
                IllegalArgumentException(
                    "Unsupported CLI backend: $cliBackend. " +
                        "Supported: $SUPPORTED_BACKENDS"
                )
            )
        }
        return when (cliBackend) {
            "gemini" -> resolveGemini()
            "copilot" -> resolveCopilot()
            "kiro" -> resolveKiro()
            "ollama" -> resolveOllama()
            else -> Result.failure(
                IllegalArgumentException("Unsupported: $cliBackend")
            )
        }
    }

    private suspend fun resolveGemini(): Result<SubprocessConfig> {
        val cliPath = requireCliPath(GEMINI_CLI_PATH, "gemini")
            ?: return missingPathError(GEMINI_CLI_PATH, "gemini")
        val model = resolveModelFromProviderConfig("gemini")
            ?: settingsRepository.get(GEMINI_CLI_MODEL)?.takeIf { it.isNotBlank() }
            ?: ""
        val args = if (model.isNotBlank()) {
            listOf(cliPath, "-m", model)
        } else {
            listOf(cliPath)
        }
        return Result.success(buildConfig(cliPath, args))
    }

    private suspend fun resolveCopilot(): Result<SubprocessConfig> {
        val cliPath = requireCliPath(COPILOT_CLI_PATH, "copilot")
            ?: return missingPathError(COPILOT_CLI_PATH, "copilot")
        return Result.success(buildConfig(cliPath, listOf(cliPath)))
    }

    private suspend fun resolveKiro(): Result<SubprocessConfig> {
        val cliPath = requireCliPath(KIRO_CLI_PATH, "kiro")
            ?: return missingPathError(KIRO_CLI_PATH, "kiro")
        return Result.success(buildConfig(cliPath, listOf(cliPath)))
    }

    private suspend fun resolveOllama(): Result<SubprocessConfig> {
        val cliPath = requireCliPath(OLLAMA_CLI_PATH, "ollama")
            ?: return missingPathError(OLLAMA_CLI_PATH, "ollama")
        val model = resolveModelFromProviderConfig("ollama")
            ?: settingsRepository.get(OLLAMA_CLI_MODEL)?.takeIf { it.isNotBlank() }
            ?: ""
        val args = if (model.isNotBlank()) {
            listOf(cliPath, "run", model)
        } else {
            listOf(cliPath)
        }
        return Result.success(buildConfig(cliPath, args))
    }

    private suspend fun requireCliPath(
        settingsKey: String, backend: String
    ): String? {
        // Primary: Integrations page (ProviderConfigRepository)
        val fromProvider = resolveFromProviderConfig(backend)
        if (fromProvider != null) return fromProvider
        // Fallback: SettingsRepository
        val path = settingsRepository.get(settingsKey)
        return if (path.isNullOrBlank()) null else path
    }

    private fun resolveFromProviderConfig(backend: String): String? {
        val repo = providerConfigRepo ?: return null
        val type = backendToProviderType(backend) ?: return null
        val config = repo.findByType(type) ?: return null
        return config.endpoint.takeIf { it.isNotBlank() }
    }

    private fun backendToProviderType(backend: String): ProviderType? =
        when (backend) {
            "gemini" -> ProviderType.GEMINI_CLI
            "copilot" -> ProviderType.COPILOT_CLI
            "kiro" -> ProviderType.KIRO_CLI
            else -> null
        }

    private fun resolveModelFromProviderConfig(backend: String): String? {
        val repo = providerConfigRepo ?: return null
        val type = backendToProviderType(backend) ?: return null
        val config = repo.findByType(type) ?: return null
        return config.model?.takeIf { it.isNotBlank() }
    }

    /**
     * Resolves the model name for a backend from ProviderConfigRepository
     * first, then SettingsRepository as fallback.
     *
     * @return the model name, or null if not configured anywhere.
     */
    suspend fun resolveModel(backend: String): String? {
        val fromProvider = resolveModelFromProviderConfig(backend)
        if (fromProvider != null) return fromProvider
        val settingsKey = modelSettingsKey(backend) ?: return null
        return settingsRepository.get(settingsKey)?.takeIf { it.isNotBlank() }
    }

    private fun modelSettingsKey(backend: String): String? = when (backend) {
        "gemini" -> GEMINI_CLI_MODEL
        "ollama" -> OLLAMA_CLI_MODEL
        else -> null
    }

    private fun missingPathError(
        settingsKey: String, backend: String
    ): Result<SubprocessConfig> = Result.failure(
        IllegalStateException(
            "CLI path not configured for backend '$backend'. " +
                "Configure it on the Integrations page or set '$settingsKey' in settings."
        )
    )

    private fun buildConfig(
        cliCommand: String, cliArgs: List<String>
    ) = SubprocessConfig(
        agentType = AGENT_TYPE,
        cliCommand = cliCommand,
        cliArgs = cliArgs,
        isRealCli = true
    )
}
