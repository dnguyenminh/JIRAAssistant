package com.assistant.server.db.pg

import com.assistant.server.attachment.VectorStore
import com.assistant.server.attachment.models.AttachmentChunk
import com.pgvector.PGvector
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import javax.sql.DataSource

/**
 * PostgreSQL + pgvector backed VectorStore implementation.
 * Uses HNSW-indexed cosine distance (<=> operator) for ANN search.
 * Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 11.1, 11.2, 11.3, 11.4
 */
class PgVectorStoreImpl(
    private val dataSource: DataSource,
    private val efSearch: Int = 40
) : VectorStore {

    override suspend fun saveChunk(chunk: AttachmentChunk): Boolean = try {
        dataSource.connection.use { conn ->
            conn.prepareStatement(PgVectorStoreSql.INSERT_SQL).use { ps ->
                bindInsertParams(ps, chunk)
                ps.executeUpdate() > 0
            }
        }
    } catch (e: Exception) {
        println("[PgVectorStore] saveChunk failed: ${e.message}")
        false
    }

    override suspend fun existsByAttachmentId(attachmentId: String): Boolean = try {
        dataSource.connection.use { conn ->
            conn.prepareStatement(PgVectorStoreSql.EXISTS_BY_ATTACHMENT_SQL).use { ps ->
                ps.setString(1, attachmentId)
                ps.executeQuery().use { it.next() && it.getInt(1) > 0 }
            }
        }
    } catch (_: Exception) { false }

    override suspend fun search(queryEmbedding: FloatArray, topK: Int): List<AttachmentChunk> =
        search(queryEmbedding, topK, chunkType = null)

    override suspend fun search(
        queryEmbedding: FloatArray,
        topK: Int,
        chunkType: String?
    ): List<AttachmentChunk> =
        searchWithScores(queryEmbedding, topK, chunkType).map { it.first }

    override suspend fun searchWithScores(
        queryEmbedding: FloatArray,
        topK: Int,
        chunkType: String?
    ): List<Pair<AttachmentChunk, Float>> = try {
        dataSource.connection.use { conn ->
            setEfSearch(conn)
            executeSearchWithScores(conn, queryEmbedding, topK, chunkType)
        }
    } catch (e: Exception) {
        println("[PgVectorStore] searchWithScores failed: ${e.message}")
        emptyList()
    }

    override suspend fun deleteByTicketId(ticketId: String): Boolean = try {
        dataSource.connection.use { conn ->
            conn.prepareStatement(PgVectorStoreSql.DELETE_BY_TICKET_SQL).use { ps ->
                ps.setString(1, ticketId)
                ps.executeUpdate() >= 0
            }
        }
    } catch (_: Exception) { false }

    override suspend fun deleteByProjectKey(
        projectKey: String,
        chunkType: String?
    ): Boolean = try {
        dataSource.connection.use { conn ->
            if (chunkType != null) {
                conn.prepareStatement(PgVectorStoreSql.DELETE_BY_PROJECT_AND_TYPE_SQL).use { ps ->
                    ps.setString(1, projectKey)
                    ps.setString(2, chunkType)
                    ps.executeUpdate() >= 0
                }
            } else {
                conn.prepareStatement(PgVectorStoreSql.DELETE_BY_PROJECT_SQL).use { ps ->
                    ps.setString(1, projectKey)
                    ps.executeUpdate() >= 0
                }
            }
        }
    } catch (e: Exception) {
        println("[PgVectorStore] deleteByProjectKey failed: ${e.message}")
        false
    }

    override suspend fun findByTicketId(ticketId: String): List<AttachmentChunk> = try {
        dataSource.connection.use { conn ->
            conn.prepareStatement(PgVectorStoreSql.FIND_BY_TICKET_SQL).use { ps ->
                ps.setString(1, ticketId)
                ps.executeQuery().use { rs -> collectChunks(rs) }
            }
        }
    } catch (e: Exception) {
        println("[PgVectorStore] findByTicketId failed: ${e.message}")
        emptyList()
    }

    // ── Private helpers ──────────────────────────────────────────────

    private fun setEfSearch(conn: Connection) {
        conn.createStatement().use { stmt ->
            stmt.execute("SET LOCAL hnsw.ef_search = $efSearch")
        }
    }

    private fun executeSearch(
        conn: Connection,
        queryEmbedding: FloatArray,
        topK: Int,
        chunkType: String?
    ): List<AttachmentChunk> =
        executeSearchWithScores(conn, queryEmbedding, topK, chunkType).map { it.first }

    private fun executeSearchWithScores(
        conn: Connection,
        queryEmbedding: FloatArray,
        topK: Int,
        chunkType: String?
    ): List<Pair<AttachmentChunk, Float>> {
        val vectorStr = toVectorString(queryEmbedding)
        return if (chunkType != null) {
            searchByTypeWithScores(conn, vectorStr, topK, chunkType)
        } else {
            searchAllWithScores(conn, vectorStr, topK)
        }
    }

    private fun searchAllWithScores(
        conn: Connection, vectorStr: String, topK: Int
    ): List<Pair<AttachmentChunk, Float>> {
        return conn.prepareStatement(PgVectorStoreSql.SEARCH_ALL_WITH_SCORE_SQL).use { ps ->
            ps.setString(1, vectorStr)
            ps.setInt(2, topK)
            ps.executeQuery().use { rs -> collectChunksWithScores(rs) }
        }
    }

    private fun searchByTypeWithScores(
        conn: Connection, vectorStr: String, topK: Int, chunkType: String
    ): List<Pair<AttachmentChunk, Float>> {
        return conn.prepareStatement(PgVectorStoreSql.SEARCH_BY_TYPE_WITH_SCORE_SQL).use { ps ->
            ps.setString(1, chunkType)
            ps.setString(2, vectorStr)
            ps.setInt(3, topK)
            ps.executeQuery().use { rs -> collectChunksWithScores(rs) }
        }
    }

    private fun bindInsertParams(ps: PreparedStatement, chunk: AttachmentChunk) {
        ps.setString(1, chunk.ticketId)
        ps.setString(2, chunk.attachmentId)
        ps.setString(3, chunk.filename)
        ps.setInt(4, chunk.chunkIndex)
        ps.setString(5, chunk.chunkText)
        ps.setString(6, toVectorString(chunk.embedding))
        ps.setString(7, chunk.createdAt)
        ps.setString(8, chunk.chunkType)
    }

    private fun collectChunks(rs: ResultSet): List<AttachmentChunk> {
        val chunks = mutableListOf<AttachmentChunk>()
        while (rs.next()) { chunks.add(mapRow(rs)) }
        return chunks
    }

    private fun collectChunksWithScores(rs: ResultSet): List<Pair<AttachmentChunk, Float>> {
        val results = mutableListOf<Pair<AttachmentChunk, Float>>()
        while (rs.next()) {
            val chunk = mapRow(rs)
            val distance = try { rs.getFloat("distance") } catch (_: Exception) { 0f }
            val score = 1f - distance // cosine distance to similarity
            results.add(chunk to score)
        }
        return results
    }

    private fun mapRow(rs: ResultSet): AttachmentChunk {
        val embedding = parseEmbedding(rs.getString("embedding"))
        return AttachmentChunk(
            id = rs.getLong("id"),
            ticketId = rs.getString("ticket_id"),
            attachmentId = rs.getString("attachment_id"),
            filename = rs.getString("filename"),
            chunkIndex = rs.getInt("chunk_index"),
            chunkText = rs.getString("chunk_text"),
            embedding = embedding,
            createdAt = rs.getString("created_at"),
            chunkType = rs.getString("chunk_type")
        )
    }

    private fun parseEmbedding(raw: String): List<Float> {
        val inner = raw.removePrefix("[").removeSuffix("]")
        return inner.split(",").map { it.trim().toFloat() }
    }

    private fun toVectorString(embedding: List<Float>): String =
        embedding.joinToString(",", "[", "]")

    private fun toVectorString(embedding: FloatArray): String =
        embedding.joinToString(",", "[", "]")
}
