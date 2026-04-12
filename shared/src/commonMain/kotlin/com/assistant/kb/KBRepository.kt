package com.assistant.kb

import com.assistant.domain.NetworkGraph

/**
 * Repository interface for Knowledge Base persistence.
 * Manages KB records (AI analysis results) and graph data.
 */
interface KBRepository {
    suspend fun findByTicketId(ticketId: String): KBRecord?
    suspend fun save(record: KBRecord): Boolean
    suspend fun overwrite(record: KBRecord): Boolean
    suspend fun saveGraphData(projectKey: String, graph: NetworkGraph): Boolean
    suspend fun getGraphData(projectKey: String): NetworkGraph?
}
