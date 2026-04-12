package com.assistant.server.mcp

import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe circular buffer holding last 100 log entries per MCP server.
 * Requirements: 6.61
 */
data class McpLogEntry(
    val timestamp: String,
    val serverId: String,
    val level: String,
    val message: String
)

object McpLogBuffer {
    private const val MAX_ENTRIES = 100
    private val buffers = ConcurrentHashMap<String, ArrayDeque<McpLogEntry>>()

    fun add(entry: McpLogEntry) {
        val deque = buffers.getOrPut(entry.serverId) { ArrayDeque() }
        synchronized(deque) {
            if (deque.size >= MAX_ENTRIES) deque.removeFirst()
            deque.addLast(entry)
        }
    }

    fun getLogs(serverId: String, limit: Int = MAX_ENTRIES): List<McpLogEntry> {
        val deque = buffers[serverId] ?: return emptyList()
        synchronized(deque) {
            return deque.takeLast(limit)
        }
    }

    fun clear(serverId: String) {
        buffers.remove(serverId)
    }
}
