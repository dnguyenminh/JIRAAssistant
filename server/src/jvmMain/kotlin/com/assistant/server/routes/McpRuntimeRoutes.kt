package com.assistant.server.routes

import com.assistant.mcp.McpProcessManager
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

/**
 * MCP Runtime API routes: start/stop, tools, status, logs.
 * Requirements: 6.37, 6.45, 6.46, 6.48, 6.50, 6.57, 6.61
 */
fun Routing.mcpRuntimeRoutes() {
    val processManager by inject<McpProcessManager>()

    route("/api/integrations/mcp") {
        authenticate("auth-jwt") {
            // Start/Stop — Administrator only (114.1)
            post("/{id}/start") { handleStartServer(processManager) }
            post("/{id}/stop") { handleStopServer(processManager) }

            // Tools — Reader+ (114.2)
            get("/{id}/tools") { handleServerTools(processManager) }
            get("/tools") { handleAggregatedTools(processManager) }

            // Tool call — Administrator (114.3)
            post("/tools/call") { handleToolCall(processManager) }

            // Status & Logs (114.4)
            get("/{id}/status") { handleServerStatus(processManager) }
            get("/{id}/logs") { handleServerLogs(processManager) }
        }
    }
}
