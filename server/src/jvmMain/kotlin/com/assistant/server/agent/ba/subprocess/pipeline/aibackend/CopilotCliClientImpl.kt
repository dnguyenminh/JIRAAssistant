package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.AiCliResponse
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.AiCliType
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.NodeCliConfig
import org.slf4j.LoggerFactory

/**
 * GitHub Copilot CLI Client Implementation.
 *
 * Uses @github/copilot to interact with GitHub Copilot.
 * Install: npm install -g @github/copilot
 *
 * LIMITATION: Copilot CLI uses MCP for tools, not custom JSON
 * tool protocol. Responses are always plain text.
 */
open class CopilotCliClientImpl : BaseNodeCliClient() {

    private val logger = LoggerFactory.getLogger(CopilotCliClientImpl::class.java)

    override val type: AiCliType = AiCliType.COPILOT

    override val displayName: String = "GitHub Copilot CLI"

    override val cliConfig: NodeCliConfig = NodeCliConfig(
        commandName = "copilot",
        npmPackage = "@github/copilot",
        jsEntryPath = "index.js"
    )

    override val cliJsPath: String by lazy {
        pathResolver.findCliJsPath(cliConfig)
            ?: throw RuntimeException(
                "Cannot find GitHub Copilot CLI. " +
                    "Please install: npm install -g @github/copilot"
            )
    }

    /** Track whether messages have been sent (for --continue flag). */
    private var copilotHasMessages: Boolean = false

    /**
     * Stateless args: -p "" -s --allow-all-tools
     */
    override fun buildCommandArgs(prompt: String): List<String> {
        return listOf("-p", "", "-s", "--allow-all-tools")
    }

    /**
     * Persistent args: same as stateless + --continue for subsequent.
     * Copilot uses --continue instead of --resume latest.
     */
    override fun buildPersistentCommandArgs(isResume: Boolean): List<String> {
        val args = mutableListOf("-p", "", "-s", "--allow-all-tools")
        if (isResume) {
            args.add("--continue")
        }
        return args
    }

    /**
     * Copilot persistent mode = stateless execution + --continue flag.
     * Override sendMessage to use sendPrompt with persistent args.
     */
    override fun sendMessage(message: String): AiCliResponse {
        logger.info("Sending message to {}... [PERSISTENT]", displayName)
        if (!isSessionActive()) startSession()

        val isResume = copilotHasMessages
        val args = buildPersistentCommandArgs(isResume = isResume)
        val command = listOf(nodePath, cliJsPath) + args
        val response = executeStatelessProcess(
            command, message, displayName, timeoutSeconds, json, logger
        )

        copilotHasMessages = true
        return response
    }

    override fun startSession() {
        super.startSession()
        copilotHasMessages = false
    }

    override fun endSession() {
        super.endSession()
        copilotHasMessages = false
    }

    /**
     * Copilot CLI returns plain text — no JSON format option.
     * Treat all output as plain text response.
     */
    override fun parseResponse(output: String): AiCliResponse {
        return AiCliResponse(response = output.trim())
    }

    override fun getInstallInstructions(): String = """
        GitHub Copilot CLI Installation:
        1. Ensure Node.js is installed (v18+)
        2. Install globally: npm install -g @github/copilot
        3. Authenticate: copilot auth
    """.trimIndent()
}
