package com.assistant.server.db.pg

import com.assistant.domain.NetworkGraph
import com.assistant.kb.KBDeepAnalysisData
import com.assistant.kb.KBRecord
import com.assistant.kb.KBRepository
import com.assistant.kb.toDeepAnalysisData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.sql.ResultSet
import javax.sql.DataSource

/**
 * PostgreSQL-backed implementation of [KBRepository].
 * Uses DataSource + PreparedStatement for all queries.
 * Requirements: 6.1, 6.2, 6.4
 */
class PgKBRepository(
    private val dataSource: DataSource
) : KBRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun findByTicketId(ticketId: String): KBRecord? = try {
        dataSource.connection.use { conn ->
            conn.prepareStatement(PgKBSql.FIND_BY_TICKET_ID).use { ps ->
                ps.setString(1, ticketId)
                ps.executeQuery().use { rs ->
                    if (rs.next()) mapRow(rs) else null
                }
            }
        }
    } catch (e: Exception) {
        println("[PgKBRepository] findByTicketId failed: ${e.message}")
        null
    }

    override suspend fun save(record: KBRecord): Boolean = upsert(record)

    override suspend fun overwrite(record: KBRecord): Boolean = upsert(record)

    override suspend fun saveGraphData(
        projectKey: String,
        graph: NetworkGraph
    ): Boolean = try {
        dataSource.connection.use { conn ->
            conn.prepareStatement(PgKBSql.UPSERT_GRAPH).use { ps ->
                ps.setString(1, projectKey)
                ps.setString(2, json.encodeToString(graph))
                ps.setString(3, now())
                ps.setString(4, json.encodeToString(graph))
                ps.setString(5, now())
                ps.executeUpdate() > 0
            }
        }
    } catch (e: Exception) {
        println("[PgKBRepository] saveGraphData failed: ${e.message}")
        false
    }

    override suspend fun getGraphData(projectKey: String): NetworkGraph? = try {
        dataSource.connection.use { conn ->
            conn.prepareStatement(PgKBSql.FIND_GRAPH).use { ps ->
                ps.setString(1, projectKey)
                ps.executeQuery().use { rs ->
                    if (rs.next()) json.decodeFromString(rs.getString("graph_json"))
                    else null
                }
            }
        }
    } catch (e: Exception) {
        println("[PgKBRepository] getGraphData failed: ${e.message}")
        null
    }

    // ── Private helpers ──────────────────────────────────────────

    private fun upsert(record: KBRecord): Boolean = try {
        dataSource.connection.use { conn ->
            conn.prepareStatement(PgKBSql.UPSERT_RECORD).use { ps ->
                bindRecordParams(ps, record)
                ps.executeUpdate() > 0
            }
        }
    } catch (e: Exception) {
        println("[PgKBRepository] upsert failed: ${e.message}")
        false
    }

    private fun bindRecordParams(
        ps: java.sql.PreparedStatement,
        record: KBRecord
    ) {
        val ts = record.timestamp
        val deepJson = json.encodeToString(record.toDeepAnalysisData())
        val evolJson = json.encodeToString(record.evolutionHistory)
        val refsJson = json.encodeToString(record.similarTicketRefs)
        // INSERT values (1-10)
        ps.setString(1, record.ticketId)
        ps.setString(2, record.requirementSummary)
        ps.setString(3, evolJson)
        ps.setDouble(4, record.scrumPoints)
        ps.setDouble(5, record.confidenceScore)
        ps.setString(6, record.rationale)
        ps.setString(7, refsJson)
        ps.setString(8, ts)
        ps.setString(9, ts)
        ps.setString(10, deepJson)
        // ON CONFLICT UPDATE values (11-17)
        ps.setString(11, record.requirementSummary)
        ps.setString(12, evolJson)
        ps.setDouble(13, record.scrumPoints)
        ps.setDouble(14, record.confidenceScore)
        ps.setString(15, record.rationale)
        ps.setString(16, refsJson)
        ps.setString(17, ts)
        ps.setString(18, deepJson)
    }

    private fun mapRow(rs: ResultSet): KBRecord {
        val deepData = parseDeepAnalysis(rs.getString("deep_analysis_json"))
        return KBRecord(
            ticketId = rs.getString("ticket_id"),
            requirementSummary = rs.getString("requirement_summary"),
            evolutionHistory = json.decodeFromString(rs.getString("evolution_history")),
            scrumPoints = rs.getDouble("scrum_points"),
            confidenceScore = rs.getDouble("confidence_score"),
            rationale = rs.getString("rationale"),
            similarTicketRefs = json.decodeFromString(rs.getString("similar_ticket_refs")),
            timestamp = rs.getString("updated_at"),
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

    private fun parseDeepAnalysis(raw: String): KBDeepAnalysisData = try {
        if (raw.isBlank() || raw == "{}") KBDeepAnalysisData()
        else json.decodeFromString(raw)
    } catch (_: Exception) { KBDeepAnalysisData() }

    private fun now(): String = java.time.Instant.now().toString()
}
