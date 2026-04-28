package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.AiCliResponse
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.ToolRequest

/**
 * Transport-agnostic interface for all AI backends.
 * Supports stateless and session modes, tool handling, and availability checking.
 */
interface AiBackend {
    val displayName: String

    // Stateless mode
    fun sendPrompt(prompt: String): AiCliResponse

    // Session mode
    fun startSession()
    fun sendMessage(message: String): AiCliResponse
    fun endSession()
    fun isSessionActive(): Boolean

    // Tool handling
    fun isToolCall(response: String): Boolean
    fun parseToolCall(response: String): ToolRequest?

    // Availability
    fun isInstalled(): Boolean
    fun getInstallInstructions(): String
}
