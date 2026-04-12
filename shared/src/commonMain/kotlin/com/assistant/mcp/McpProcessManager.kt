package com.assistant.mcp

import com.assistant.mcp.models.McpAggregatedTool
import com.assistant.mcp.models.McpProcessStatus

/**
 * Manages MCP server process lifecycles.
 * Requirements: 6.31, 6.32, 6.33, 6.37
 */
interface McpProcessManager {

    /** Start an MCP server process. Req: 6.31, 6.32 */
    suspend fun startServer(configId: String): McpProcessStatus

    /** Stop process gracefully (SIGTERM → timeout → SIGKILL). Req: 6.36 */
    suspend fun stopServer(configId: String): McpProcessStatus

    /** Restart = stop + start. Req: 6.32 */
    suspend fun restartServer(configId: String): McpProcessStatus

    /** Map of currently running servers. Req: 6.32 */
    fun getRunningServers(): Map<String, McpProcessStatus>

    /** Detailed status for a single server. Req: 6.57 */
    fun getStatus(configId: String): McpProcessStatus?

    /** Start all enabled servers on app startup. Req: 6.33 */
    suspend fun startAllEnabled()

    /** Stop all servers (shutdown hook). */
    suspend fun stopAll()

    /** Aggregated tools from all running servers. Req: 6.46 */
    fun getActiveTools(): List<McpAggregatedTool>

    /** Get protocol client for a specific server. */
    fun getClient(configId: String): McpProtocolClient?
}
