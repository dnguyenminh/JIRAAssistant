package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Abstract base class for Node.js CLI clients.
 *
 * Supports two process modes:
 * - STATELESS: spawn → write stdin → close stdin → read stdout → wait for exit
 * - PERSISTENT: spawn with stream-json → read NDJSON → detect "type":"result" → resume
 */
abstract class BaseNodeCliClient : AiCliClient {

    private val logger = LoggerFactory.getLogger(this::class.java)

    protected val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    protected val pathResolver = NodeCliPathResolver()

    protected val nodePath: String by lazy { pathResolver.findNodePath() }
    protected abstract val cliJsPath: String

    protected abstract val cliConfig: NodeCliConfig
    protected abstract fun buildCommandArgs(prompt: String): List<String>
    protected abstract fun buildPersistentCommandArgs(isResume: Boolean): List<String>

    protected open val timeoutSeconds: Long = 300

    override var processMode: ProcessMode = ProcessMode.STATELESS

    // ==================== PERSISTENT MODE STATE ====================

    private var sessionInitialized: Boolean = false
    private var hasMessages: Boolean = false
    private var currentSessionId: String? = null

    // ==================== STATELESS MODE ====================

    override fun sendPrompt(prompt: String): AiCliResponse {
        logger.info("Sending prompt to {}... [STATELESS]", displayName)
        logger.debug("Prompt length: {} chars", prompt.length)
        val args = buildCommandArgs(prompt)
        val command = listOf(nodePath, cliJsPath) + args
        return executeStatelessProcess(command, prompt, displayName, timeoutSeconds, json, logger)
    }

    // ==================== PERSISTENT MODE ====================

    override fun startSession() {
        if (sessionInitialized) {
            logger.debug("Session already initialized, will resume on next sendMessage")
            return
        }
        logger.info("Persistent session initialized for {}", displayName)
        sessionInitialized = true
        hasMessages = false
        currentSessionId = null
    }

    override fun sendMessage(message: String): AiCliResponse {
        logger.info("Sending message to {}... [PERSISTENT]", displayName)
        if (!sessionInitialized) startSession()

        val args = buildPersistentCommandArgs(isResume = hasMessages)
        val command = listOf(nodePath, cliJsPath) + args
        val response = executeStreamProcess(
            command, message, displayName, currentSessionId, timeoutSeconds, json, logger
        )

        hasMessages = true
        if (response.sessionId != null) currentSessionId = response.sessionId
        return response
    }

    override fun endSession() {
        logger.info("Ending persistent session with {}...", displayName)
        sessionInitialized = false
        hasMessages = false
        currentSessionId = null
    }

    override fun isSessionActive(): Boolean = sessionInitialized

    // ==================== TOOL CALL PARSING ====================

    override fun isToolCall(response: String): Boolean {
        return try {
            val obj = json.parseToJsonElement(response).jsonObject
            obj["type"]?.jsonPrimitive?.content == "tool_call"
        } catch (_: Exception) {
            false
        }
    }

    override fun parseToolCall(response: String): ToolRequest? {
        return try {
            json.decodeFromString<ToolRequest>(response)
        } catch (e: Exception) {
            logger.debug("Failed to parse tool call: {}", e.message)
            null
        }
    }

    // ==================== RESPONSE PARSING ====================

    protected open fun parseResponse(output: String): AiCliResponse {
        return try {
            val obj = json.parseToJsonElement(output).jsonObject
            AiCliResponse(
                response = obj["response"]?.jsonPrimitive?.content ?: "",
                sessionId = obj["session_id"]?.jsonPrimitive?.content,
                rawJson = output
            )
        } catch (_: Exception) {
            logger.debug("Response is not JSON, treating as plain text")
            AiCliResponse(response = output.trim())
        }
    }

    // ==================== AVAILABILITY ====================

    override fun isInstalled(): Boolean {
        return try {
            val jsPath = pathResolver.findCliJsPath(cliConfig)
            jsPath != null && File(jsPath).exists()
        } catch (_: Exception) {
            false
        }
    }
}
