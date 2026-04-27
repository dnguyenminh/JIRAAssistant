package com.assistant.server.db

import java.sql.Connection
import java.sql.DriverManager

/**
 * Shared helpers for DataMigration property tests.
 * Creates SQLite databases with the full 17-table schema.
 */
internal object DataMigrationTestHelper {

    val ALL_TABLES = listOf(
        "kb_records", "graph_data", "users", "audit_log",
        "provider_configs", "app_settings", "scan_states", "scan_log",
        "chat_messages", "chat_conversations", "user_ai_config",
        "mcp_servers", "attachment_chunks", "user_tool_permissions",
        "collection_jobs", "traversal_cache", "deep_collection_rate_limits"
    )

    /** Apply the full SQLite schema to an existing connection. */
    fun createSqliteSchema(conn: Connection) {
        conn.createStatement().use { stmt ->
            SQLITE_SCHEMA.split(";")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { stmt.execute(it) }
        }
    }

    fun insertKbRecord(conn: Connection, ticketId: String, summary: String = "sum") {
        conn.prepareStatement(
            """INSERT INTO kb_records (ticket_id, requirement_summary, evolution_history,
               scrum_points, confidence_score, rationale, similar_ticket_refs,
               created_at, updated_at, deep_analysis_json)
               VALUES (?, ?, 'hist', 3.0, 0.8, 'rat', 'refs', '2024-01-01', '2024-01-01', '{}')"""
        ).use { ps ->
            ps.setString(1, ticketId)
            ps.setString(2, summary)
            ps.executeUpdate()
        }
    }

    fun insertAppSetting(conn: Connection, key: String, value: String) {
        conn.prepareStatement(
            "INSERT INTO app_settings (setting_key, setting_value, updated_at) VALUES (?, ?, '2024-01-01')"
        ).use { ps ->
            ps.setString(1, key)
            ps.setString(2, value)
            ps.executeUpdate()
        }
    }

    fun insertAttachmentChunk(conn: Connection, ticketId: String, attId: String, embedding: List<Float>) {
        val embJson = embedding.joinToString(",", "[", "]")
        conn.prepareStatement(
            """INSERT INTO attachment_chunks (ticket_id, attachment_id, filename,
               chunk_index, chunk_text, embedding, created_at, chunk_type)
               VALUES (?, ?, 'file.txt', 0, 'text', ?, '2024-01-01', 'ATTACHMENT')"""
        ).use { ps ->
            ps.setString(1, ticketId)
            ps.setString(2, attId)
            ps.setString(3, embJson)
            ps.executeUpdate()
        }
    }

    fun countPgRows(pgConn: Connection, table: String): Int {
        pgConn.createStatement().use { stmt ->
            stmt.executeQuery("SELECT COUNT(*) FROM $table").use { rs ->
                rs.next()
                return rs.getInt(1)
            }
        }
    }

    @Suppress("MaxLineLength")
    private val SQLITE_SCHEMA = """
CREATE TABLE kb_records (ticket_id TEXT NOT NULL PRIMARY KEY, requirement_summary TEXT NOT NULL, evolution_history TEXT NOT NULL, scrum_points REAL NOT NULL, confidence_score REAL NOT NULL, rationale TEXT NOT NULL, similar_ticket_refs TEXT NOT NULL, created_at TEXT NOT NULL, updated_at TEXT NOT NULL, deep_analysis_json TEXT NOT NULL DEFAULT '{}');
CREATE TABLE graph_data (project_key TEXT NOT NULL PRIMARY KEY, graph_json TEXT NOT NULL, updated_at TEXT NOT NULL);
CREATE TABLE users (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, email TEXT NOT NULL UNIQUE, role TEXT NOT NULL DEFAULT 'READER', avatar_url TEXT, custom_permissions TEXT NOT NULL DEFAULT '[]');
CREATE TABLE audit_log (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, timestamp TEXT NOT NULL, actor_id TEXT NOT NULL, target_user_id TEXT NOT NULL, action TEXT NOT NULL, old_value TEXT NOT NULL, new_value TEXT NOT NULL, tag TEXT NOT NULL);
CREATE TABLE provider_configs (provider_id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, type TEXT NOT NULL, endpoint TEXT NOT NULL, api_key TEXT, model TEXT, priority INTEGER NOT NULL DEFAULT 0, status TEXT NOT NULL DEFAULT 'OFFLINE');
CREATE TABLE app_settings (setting_key TEXT NOT NULL PRIMARY KEY, setting_value TEXT NOT NULL, updated_at TEXT NOT NULL);
CREATE TABLE scan_states (project_key TEXT NOT NULL PRIMARY KEY, status TEXT NOT NULL DEFAULT 'IDLE', total_tickets INTEGER NOT NULL DEFAULT 0, processed_count INTEGER NOT NULL DEFAULT 0, current_ticket_id TEXT, ticket_ids TEXT NOT NULL DEFAULT '[]', started_at TEXT NOT NULL, updated_at TEXT NOT NULL);
CREATE TABLE scan_log (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, project_key TEXT NOT NULL, ticket_id TEXT NOT NULL, status TEXT NOT NULL, message TEXT NOT NULL, timestamp TEXT NOT NULL);
CREATE TABLE chat_messages (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, user_id TEXT NOT NULL, conversation_id TEXT NOT NULL DEFAULT '', role TEXT NOT NULL, message TEXT NOT NULL, context TEXT, timestamp TEXT NOT NULL);
CREATE TABLE chat_conversations (id TEXT NOT NULL PRIMARY KEY, user_id TEXT NOT NULL, title TEXT NOT NULL DEFAULT 'New Chat', created_at TEXT NOT NULL, updated_at TEXT NOT NULL);
CREATE TABLE user_ai_config (user_id TEXT NOT NULL PRIMARY KEY, skills_json TEXT NOT NULL DEFAULT '[]', workflow_json TEXT NOT NULL DEFAULT '[]', instructions_json TEXT NOT NULL DEFAULT '[]', rules_json TEXT NOT NULL DEFAULT '[]', updated_at TEXT NOT NULL);
CREATE TABLE mcp_servers (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, type TEXT NOT NULL DEFAULT 'stdio', command TEXT NOT NULL DEFAULT '', url TEXT NOT NULL DEFAULT '', args TEXT NOT NULL DEFAULT '[]', env TEXT NOT NULL DEFAULT '{}', auto_approve TEXT NOT NULL DEFAULT '[]', disabled INTEGER NOT NULL DEFAULT 0, status TEXT NOT NULL DEFAULT 'OFFLINE', created_at TEXT NOT NULL, updated_at TEXT NOT NULL, internal INTEGER NOT NULL DEFAULT 0);
CREATE TABLE attachment_chunks (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, ticket_id TEXT NOT NULL, attachment_id TEXT NOT NULL, filename TEXT NOT NULL, chunk_index INTEGER NOT NULL, chunk_text TEXT NOT NULL, embedding TEXT NOT NULL, created_at TEXT NOT NULL, chunk_type TEXT NOT NULL DEFAULT 'ATTACHMENT');
CREATE TABLE user_tool_permissions (user_id TEXT NOT NULL PRIMARY KEY, permissions_json TEXT NOT NULL DEFAULT '{}', updated_at TEXT NOT NULL);
CREATE TABLE collection_jobs (job_id TEXT NOT NULL PRIMARY KEY, parent_ticket_id TEXT NOT NULL, job_type TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'QUEUED', total_items INTEGER NOT NULL, completed_items INTEGER NOT NULL DEFAULT 0, failed_items INTEGER NOT NULL DEFAULT 0, items_json TEXT NOT NULL DEFAULT '[]', created_at TEXT NOT NULL, updated_at TEXT NOT NULL, version INTEGER NOT NULL DEFAULT 1);
CREATE TABLE traversal_cache (root_ticket_id TEXT NOT NULL PRIMARY KEY, graph_json TEXT NOT NULL, cached_at TEXT NOT NULL, root_updated_at TEXT NOT NULL);
CREATE TABLE deep_collection_rate_limits (user_id TEXT NOT NULL, requested_at TEXT NOT NULL, PRIMARY KEY (user_id, requested_at))
    """.trimIndent()
}
