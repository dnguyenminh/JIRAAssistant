package com.assistant.server.agent.ba

import com.assistant.agent.config.AgentConfig
import com.assistant.agent.config.agentConfig
import com.assistant.agent.models.ErrorStrategy
import com.assistant.agent.subprocess.SubprocessConfig
import com.assistant.settings.SettingsRepository

/**
 * BA Agent configuration: subprocess config and AgentConfig DSL.
 *
 * Provides CLI subprocess configuration and memory/tool/limit
 * definitions for the BA agent.
 * Requirements: 4.2, 9.1, 9.2, 9.3, 9.4
 */
object BAAgentConfig {

    private const val AGENT_TYPE = "ba-agent"
    private const val CLI_PATH_KEY = "ai_cli_path"
    private const val CLI_MODEL_KEY = "ai_cli_model"

    /**
     * Build a [SubprocessConfig] for the AI CLI backend by reading
     * CLI path, model, and arguments from [SettingsRepository].
     *
     * Requirements: 4.2
     */
    suspend fun buildSubprocessConfig(
        settings: SettingsRepository
    ): SubprocessConfig {
        val cliPath = settings.get(CLI_PATH_KEY) ?: ""
        val model = settings.get(CLI_MODEL_KEY) ?: ""
        val args = buildCliArgs(cliPath, model)
        return SubprocessConfig(
            agentType = AGENT_TYPE,
            cliCommand = cliPath,
            cliArgs = args
        )
    }

    private fun buildCliArgs(
        cliPath: String, model: String
    ): List<String> = if (model.isNotBlank()) {
        listOf(cliPath, "-m", model)
    } else {
        listOf(cliPath)
    }

    /**
     * Build the serializable AgentConfig for the BA agent.
     */
    fun buildBAAgentConfig(): AgentConfig = agentConfig {
        memorySchema {
            stringSlot("summary", 10_000)
            stringSlot("description", 10_000)
            listSlot("comments", 50)
            listSlot("attachmentsData", 30)
            mapSlot("linkedTickets", 20)
            stringSlot("businessGoals", 10_000)
            mapSlot("kbRecords", 20)
            stringSlot("technicalDetails", 10_000)
            listSlot("acceptanceCriteria", 50)
            mapSlot("ticketClassifications", 20)
        }
        phases {
            phase("collect")
            phase("expand")
            phase("visualize")
            phase("synthesize")
        }
        // tools block removed — tools discovered dynamically via
        // SubprocessProxy.getAvailableToolDescriptors() from MCP servers
        limits {
            maxTotalDurationSeconds = 90
            maxToolCalls = 50
            maxIterations = 3
            maxConcurrentTools = 5
        }
        errorStrategy {
            default = ErrorStrategy.SKIP
        }
    }


}
