package com.assistant.server.di

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver

/**
 * Incremental migrations for tables added after initial schema.
 * Uses CREATE TABLE IF NOT EXISTS for new tables and
 * DROP + CREATE for tables with schema changes.
 * Each statement is independent — failures are silently ignored.
 */
fun runIncrementalMigrations(driver: JdbcSqliteDriver) {
    for (sql in buildMigrationStatements()) {
        try { driver.execute(null, sql, 0) } catch (_: Exception) { }
    }
}

private fun buildMigrationStatements(): List<String> = listOf(
    // --- scan_states ---
    """CREATE TABLE IF NOT EXISTS scan_states (
        project_key TEXT NOT NULL PRIMARY KEY,
        status TEXT NOT NULL DEFAULT 'IDLE',
        total_tickets INTEGER NOT NULL DEFAULT 0,
        processed_count INTEGER NOT NULL DEFAULT 0,
        current_ticket_id TEXT,
        ticket_ids TEXT NOT NULL DEFAULT '[]',
        started_at TEXT NOT NULL,
        updated_at TEXT NOT NULL)""",

    // --- scan_log ---
    """CREATE TABLE IF NOT EXISTS scan_log (
        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
        project_key TEXT NOT NULL,
        ticket_id TEXT NOT NULL,
        status TEXT NOT NULL,
        message TEXT NOT NULL,
        timestamp TEXT NOT NULL)""",
    "CREATE INDEX IF NOT EXISTS idx_scan_log_project ON scan_log(project_key, timestamp DESC)",

    // --- app_settings ---
    """CREATE TABLE IF NOT EXISTS app_settings (
        setting_key TEXT NOT NULL PRIMARY KEY,
        setting_value TEXT NOT NULL,
        updated_at TEXT NOT NULL)""",

    // --- chat_messages ---
    """CREATE TABLE IF NOT EXISTS chat_messages (
        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
        user_id TEXT NOT NULL,
        conversation_id TEXT NOT NULL DEFAULT '',
        role TEXT NOT NULL,
        message TEXT NOT NULL,
        context TEXT,
        timestamp TEXT NOT NULL)""",
    "CREATE INDEX IF NOT EXISTS idx_chat_messages_user_timestamp ON chat_messages(user_id, timestamp ASC)",
    "CREATE INDEX IF NOT EXISTS idx_chat_msg_conv ON chat_messages(conversation_id, timestamp ASC)",
    // ALTER: conversation_id added later to chat_messages
    "ALTER TABLE chat_messages ADD COLUMN conversation_id TEXT NOT NULL DEFAULT ''",

    // --- chat_conversations ---
    """CREATE TABLE IF NOT EXISTS chat_conversations (
        id TEXT NOT NULL PRIMARY KEY,
        user_id TEXT NOT NULL,
        title TEXT NOT NULL DEFAULT 'New Chat',
        created_at TEXT NOT NULL,
        updated_at TEXT NOT NULL)""",
    "CREATE INDEX IF NOT EXISTS idx_chat_conv_user ON chat_conversations(user_id, updated_at DESC)",

    // --- user_ai_config ---
    """CREATE TABLE IF NOT EXISTS user_ai_config (
        user_id TEXT NOT NULL PRIMARY KEY,
        skills TEXT NOT NULL DEFAULT '',
        workflow TEXT NOT NULL DEFAULT '',
        instructions TEXT NOT NULL DEFAULT '',
        rules TEXT NOT NULL DEFAULT '',
        updated_at TEXT NOT NULL)""",

    // --- mcp_servers: CREATE if not exists, then ADD missing columns ---
    """CREATE TABLE IF NOT EXISTS mcp_servers (
        id TEXT NOT NULL PRIMARY KEY,
        name TEXT NOT NULL,
        type TEXT NOT NULL DEFAULT 'stdio',
        command TEXT NOT NULL DEFAULT '',
        url TEXT NOT NULL DEFAULT '',
        args TEXT NOT NULL DEFAULT '[]',
        env TEXT NOT NULL DEFAULT '{}',
        auto_approve TEXT NOT NULL DEFAULT '[]',
        disabled INTEGER NOT NULL DEFAULT 0,
        status TEXT NOT NULL DEFAULT 'OFFLINE',
        created_at TEXT NOT NULL,
        updated_at TEXT NOT NULL)""",
    // ALTER: add columns that may be missing from older schema
    "ALTER TABLE mcp_servers ADD COLUMN type TEXT NOT NULL DEFAULT 'stdio'",
    "ALTER TABLE mcp_servers ADD COLUMN url TEXT NOT NULL DEFAULT ''",
    "ALTER TABLE mcp_servers ADD COLUMN auto_approve TEXT NOT NULL DEFAULT '[]'",
    "ALTER TABLE mcp_servers ADD COLUMN disabled INTEGER NOT NULL DEFAULT 0",
    "ALTER TABLE mcp_servers ADD COLUMN status TEXT NOT NULL DEFAULT 'OFFLINE'",

    // --- attachment_chunks ---
    """CREATE TABLE IF NOT EXISTS attachment_chunks (
        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
        ticket_id TEXT NOT NULL,
        attachment_id TEXT NOT NULL,
        filename TEXT NOT NULL,
        chunk_index INTEGER NOT NULL,
        chunk_text TEXT NOT NULL,
        embedding TEXT NOT NULL,
        created_at TEXT NOT NULL,
        chunk_type TEXT NOT NULL DEFAULT 'ATTACHMENT')""",
    "CREATE INDEX IF NOT EXISTS idx_attachment_chunks_ticket ON attachment_chunks(ticket_id)",
    "CREATE INDEX IF NOT EXISTS idx_attachment_chunks_attachment ON attachment_chunks(attachment_id)",
    "CREATE INDEX IF NOT EXISTS idx_attachment_chunks_type ON attachment_chunks(chunk_type)",
    // ALTER: add chunk_type column for existing databases
    "ALTER TABLE attachment_chunks ADD COLUMN chunk_type TEXT NOT NULL DEFAULT 'ATTACHMENT'"
)
