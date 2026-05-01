package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.AiCliResponse
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.AiCliType
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.NodeCliConfig
import org.slf4j.LoggerFactory

/**
 * Gemini CLI Client Implementation.
 *
 * Uses @google/gemini-cli to interact with Gemini AI.
 * Install: npm install -g @google/gemini-cli
 *
 * Persistent mode uses `--output-format json` (NOT stream-json)
 * + `--resume latest` to preserve context. Stream-json mode causes
 * Gemini CLI to process tool calls internally, bypassing the agent.
 */
open class GeminiCliClientImpl(
    private val model: String? = null
) : BaseNodeCliClient() {

    private val log = LoggerFactory.getLogger(GeminiCliClientImpl::class.java)

    override val type: AiCliType = AiCliType.GEMINI
    override val displayName: String = "Gemini CLI"

    override val cliConfig: NodeCliConfig = NodeCliConfig(
        commandName = "gemini",
        npmPackage = "@google/gemini-cli",
        jsEntryPath = "bundle/gemini.js"
    )

    override val cliJsPath: String by lazy {
        pathResolver.findCliJsPath(cliConfig)
            ?: throw RuntimeException(
                "Cannot find Gemini CLI (gemini.js). " +
                    "Please install: npm install -g @google/gemini-cli"
            )
    }

    /** Track whether messages have been sent (for --resume flag). */
    private var geminiHasMessages: Boolean = false

    /**
     * Stateless args: -p "" --output-format json --sandbox [-m model]
     */
    override fun buildCommandArgs(prompt: String): List<String> {
        val args = mutableListOf("-p", "", "--output-format", "json", "--sandbox")
        if (!model.isNullOrBlank()) args.addAll(listOf("-m", model))
        return args
    }

    /**
     * Persistent args use json output (NOT stream-json) + --resume latest.
     * Stream-json causes Gemini CLI to handle tool calls internally.
     */
    override fun buildPersistentCommandArgs(isResume: Boolean): List<String> {
        val args = mutableListOf("-p", "", "--output-format", "json", "--sandbox")
        if (!model.isNullOrBlank()) args.addAll(listOf("-m", model))
        if (isResume) args.addAll(listOf("--resume", "latest"))
        return args
    }

    /**
     * Persistent mode = stateless execution + --resume latest.
     * Gemini CLI with json output terminates after response,
     * so we use sendPrompt-style execution with persistent args.
     */
    override suspend fun sendMessage(message: String): AiCliResponse {
        log.info("Sending message to {}... [PERSISTENT]", displayName)
        if (!isSessionActive()) startSession()

        val args = buildPersistentCommandArgs(isResume = geminiHasMessages)
        val command = listOf(nodePath, cliJsPath) + args
        val response = executeStatelessProcess(
            command, message, displayName, timeoutSeconds, json, log
        )

        geminiHasMessages = true
        return response
    }

    override fun startSession() {
        super.startSession()
        geminiHasMessages = false
    }

    override fun endSession() {
        super.endSession()
        geminiHasMessages = false
    }

    override fun getInstallInstructions(): String = """
        Gemini CLI Installation:
        1. Ensure Node.js is installed (v18+)
        2. Install globally: npm install -g @google/gemini-cli
        3. Authenticate: gemini auth login
    """.trimIndent()
}
