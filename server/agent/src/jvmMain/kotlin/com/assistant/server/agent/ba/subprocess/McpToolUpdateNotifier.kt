package com.assistant.server.agent.ba.subprocess

import com.assistant.agent.subprocess.SubprocessManager
import com.assistant.agent.subprocess.SubprocessProxy
import org.slf4j.LoggerFactory

/**
 * Notifies active BA subprocess sessions when the MCP tool set
 * changes at runtime (e.g., MCP server started/stopped via the
 * Integrations page).
 *
 * Call [notifyToolsUpdated] whenever a shared MCP server is
 * added or removed. If a BA subprocess session is active, the
 * updated tool list is written to its stdin so the AI subprocess
 * stays in sync with the current tool inventory.
 *
 * Requirements: 5.4
 */
class McpToolUpdateNotifier(
    private val subprocessManager: SubprocessManager,
    private val subprocessProxy: SubprocessProxy
) {

    private val logger = LoggerFactory.getLogger(
        McpToolUpdateNotifier::class.java
    )

    companion object {
        private const val AGENT_TYPE = "ba-agent"
    }

    /**
     * Sends a tools-updated message to the active BA subprocess
     * session, if one is running.
     *
     * Called when a new MCP server is started or stopped via the
     * Integrations page during an active session.
     */
    suspend fun notifyToolsUpdated() {
        if (!subprocessManager.isRunning(AGENT_TYPE)) {
            logger.debug(
                "No active BA subprocess session, skipping update"
            )
            return
        }
        try {
            val message = subprocessProxy.buildToolsUpdatedMessage()
            subprocessManager.sendCommand(AGENT_TYPE, message)
            logger.info(
                "Sent tools-updated notification to BA subprocess"
            )
        } catch (e: Exception) {
            logger.warn(
                "Failed to send tools-updated notification: {}",
                e.message
            )
        }
    }
}
