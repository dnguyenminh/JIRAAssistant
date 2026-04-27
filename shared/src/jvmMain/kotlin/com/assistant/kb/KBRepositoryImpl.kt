package com.assistant.kb

import com.assistant.db.JiraDatabase
import com.assistant.domain.NetworkGraph
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * SQLDelight-backed implementation of KBRepository.
 * Complex fields (evolution_history, similar_ticket_refs) are stored as JSON strings.
 * Deep analysis fields stored as single JSON blob in deep_analysis_json column (20.1, 20.4).
 * Write operations use retry logic (max 3 attempts).
 */
class KBRepositoryImpl(
    private val database: JiraDatabase
) : KBRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    companion object {
        private const val MAX_RETRIES = 3
    }

    /** Parse deep_analysis_json column, returning defaults for empty/invalid JSON (20.4). */
    private fun parseDeepAnalysisData(raw: String): KBDeepAnalysisData {
        return try {
            if (raw.isBlank() || raw == "{}") KBDeepAnalysisData()
            else json.decodeFromString<KBDeepAnalysisData>(raw)
        } catch (_: Exception) {
            KBDeepAnalysisData()
        }
    }

    override suspend fun findByTicketId(ticketId: String): KBRecord? {
        val row = database.knowledgeBaseQueries.findKBRecordByTicketId(ticketId).executeAsOneOrNull()
            ?: return null
        val deepData = parseDeepAnalysisData(row.deep_analysis_json)
        return KBRecord(
            ticketId = row.ticket_id,
            requirementSummary = row.requirement_summary,
            evolutionHistory = json.decodeFromString(row.evolution_history),
            scrumPoints = row.scrum_points,
            confidenceScore = row.confidence_score,
            rationale = row.rationale,
            similarTicketRefs = json.decodeFromString(row.similar_ticket_refs),
            timestamp = row.updated_at,
            technicalDetails = deepData.technicalDetails,
            acceptanceCriteria = deepData.acceptanceCriteria,
            dependencies = deepData.dependencies,
            analysisMetadata = deepData.analysisMetadata,
            businessSummary = deepData.businessSummary,
            asIsState = deepData.asIsState,
            toBeState = deepData.toBeState,
            extractedRequirements = deepData.extractedRequirements,
            diagrams = deepData.diagrams
        )
    }

    override suspend fun save(record: KBRecord): Boolean {
        return retryWrite {
            val now = record.timestamp
            database.knowledgeBaseQueries.insertOrReplaceKBRecord(
                ticket_id = record.ticketId,
                requirement_summary = record.requirementSummary,
                evolution_history = json.encodeToString(record.evolutionHistory),
                scrum_points = record.scrumPoints,
                confidence_score = record.confidenceScore,
                rationale = record.rationale,
                similar_ticket_refs = json.encodeToString(record.similarTicketRefs),
                created_at = now,
                updated_at = now,
                deep_analysis_json = json.encodeToString(record.toDeepAnalysisData())
            )
        }
    }

    override suspend fun overwrite(record: KBRecord): Boolean {
        return retryWrite {
            val now = record.timestamp
            database.knowledgeBaseQueries.insertOrReplaceKBRecord(
                ticket_id = record.ticketId,
                requirement_summary = record.requirementSummary,
                evolution_history = json.encodeToString(record.evolutionHistory),
                scrum_points = record.scrumPoints,
                confidence_score = record.confidenceScore,
                rationale = record.rationale,
                similar_ticket_refs = json.encodeToString(record.similarTicketRefs),
                created_at = now,
                updated_at = now,
                deep_analysis_json = json.encodeToString(record.toDeepAnalysisData())
            )
        }
    }

    override suspend fun saveGraphData(projectKey: String, graph: NetworkGraph): Boolean {
        return retryWrite {
            val now = kotlinx.datetime.Clock.System.now().toString()
            database.knowledgeBaseQueries.insertOrReplaceGraph(
                project_key = projectKey,
                graph_json = json.encodeToString(graph),
                updated_at = now
            )
        }
    }

    override suspend fun getGraphData(projectKey: String): NetworkGraph? {
        val row = database.knowledgeBaseQueries.findGraphByProjectKey(projectKey).executeAsOneOrNull()
            ?: return null
        return json.decodeFromString(row.graph_json)
    }

    /**
     * Retry logic: attempts a write operation up to MAX_RETRIES times.
     * Returns true if successful, false if all retries exhausted.
     */
    private inline fun retryWrite(block: () -> Unit): Boolean {
        var lastException: Exception? = null
        repeat(MAX_RETRIES) {
            try {
                block()
                return true
            } catch (e: Exception) {
                lastException = e
            }
        }
        System.err.println("KB write failed after $MAX_RETRIES retries: ${lastException?.message}")
        return false
    }
}
