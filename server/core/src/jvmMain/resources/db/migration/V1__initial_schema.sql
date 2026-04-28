-- V1__initial_schema.sql
CREATE EXTENSION IF NOT EXISTS vector;

-- Knowledge Base Records
CREATE TABLE kb_records (
    ticket_id TEXT NOT NULL PRIMARY KEY,
    requirement_summary TEXT NOT NULL,
    evolution_history TEXT NOT NULL,
    scrum_points DOUBLE PRECISION NOT NULL,
    confidence_score DOUBLE PRECISION NOT NULL,
    rationale TEXT NOT NULL,
    similar_ticket_refs TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    deep_analysis_json TEXT NOT NULL DEFAULT '{}'
);

-- Graph Data
CREATE TABLE graph_data (
    project_key TEXT NOT NULL PRIMARY KEY,
    graph_json TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

-- Users
CREATE TABLE users (
    id TEXT NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    email TEXT NOT NULL UNIQUE,
    role TEXT NOT NULL DEFAULT 'READER',
    avatar_url TEXT,
    custom_permissions TEXT NOT NULL DEFAULT '[]'
);

-- Audit Log
CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    timestamp TEXT NOT NULL,
    actor_id TEXT NOT NULL,
    target_user_id TEXT NOT NULL,
    action TEXT NOT NULL,
    old_value TEXT NOT NULL,
    new_value TEXT NOT NULL,
    tag TEXT NOT NULL
);

-- Provider Configs
CREATE TABLE provider_configs (
    provider_id TEXT NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    type TEXT NOT NULL,
    endpoint TEXT NOT NULL,
    api_key TEXT,
    model TEXT,
    priority INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'OFFLINE'
);

-- App Settings
CREATE TABLE app_settings (
    setting_key TEXT NOT NULL PRIMARY KEY,
    setting_value TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

-- Scan States
CREATE TABLE scan_states (
    project_key TEXT NOT NULL PRIMARY KEY,
    status TEXT NOT NULL DEFAULT 'IDLE',
    total_tickets INTEGER NOT NULL DEFAULT 0,
    processed_count INTEGER NOT NULL DEFAULT 0,
    current_ticket_id TEXT,
    ticket_ids TEXT NOT NULL DEFAULT '[]',
    started_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

-- Scan Log
CREATE TABLE scan_log (
    id BIGSERIAL PRIMARY KEY,
    project_key TEXT NOT NULL,
    ticket_id TEXT NOT NULL,
    status TEXT NOT NULL,
    message TEXT NOT NULL,
    timestamp TEXT NOT NULL
);
CREATE INDEX idx_scan_log_project ON scan_log(project_key, timestamp DESC);

-- Chat Messages
CREATE TABLE chat_messages (
    id BIGSERIAL PRIMARY KEY,
    user_id TEXT NOT NULL,
    conversation_id TEXT NOT NULL DEFAULT '',
    role TEXT NOT NULL,
    message TEXT NOT NULL,
    context TEXT,
    timestamp TEXT NOT NULL
);
CREATE INDEX idx_chat_messages_user_timestamp
    ON chat_messages(user_id, timestamp ASC);
CREATE INDEX idx_chat_msg_conv
    ON chat_messages(conversation_id, timestamp ASC);

-- Chat Conversations
CREATE TABLE chat_conversations (
    id TEXT NOT NULL PRIMARY KEY,
    user_id TEXT NOT NULL,
    title TEXT NOT NULL DEFAULT 'New Chat',
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);
CREATE INDEX idx_chat_conv_user
    ON chat_conversations(user_id, updated_at DESC);

-- User AI Config
CREATE TABLE user_ai_config (
    user_id TEXT NOT NULL PRIMARY KEY,
    skills_json TEXT NOT NULL DEFAULT '[]',
    workflow_json TEXT NOT NULL DEFAULT '[]',
    instructions_json TEXT NOT NULL DEFAULT '[]',
    rules_json TEXT NOT NULL DEFAULT '[]',
    updated_at TEXT NOT NULL
);

-- MCP Servers
CREATE TABLE mcp_servers (
    id TEXT NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    type TEXT NOT NULL DEFAULT 'stdio',
    command TEXT NOT NULL DEFAULT '',
    url TEXT NOT NULL DEFAULT '',
    args TEXT NOT NULL DEFAULT '[]',
    env TEXT NOT NULL DEFAULT '{}',
    auto_approve TEXT NOT NULL DEFAULT '[]',
    disabled BOOLEAN NOT NULL DEFAULT FALSE,
    status TEXT NOT NULL DEFAULT 'OFFLINE',
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    internal BOOLEAN NOT NULL DEFAULT FALSE
);

-- Attachment Chunks (with pgvector)
CREATE TABLE attachment_chunks (
    id BIGSERIAL PRIMARY KEY,
    ticket_id TEXT NOT NULL,
    attachment_id TEXT NOT NULL,
    filename TEXT NOT NULL,
    chunk_index INTEGER NOT NULL,
    chunk_text TEXT NOT NULL,
    embedding vector(768) NOT NULL,
    created_at TEXT NOT NULL,
    chunk_type TEXT NOT NULL DEFAULT 'ATTACHMENT'
);
CREATE INDEX idx_attachment_chunks_ticket
    ON attachment_chunks(ticket_id);
CREATE INDEX idx_attachment_chunks_attachment
    ON attachment_chunks(attachment_id);
CREATE INDEX idx_attachment_chunks_type
    ON attachment_chunks(chunk_type);

-- HNSW Index for cosine distance ANN search
CREATE INDEX idx_attachment_chunks_embedding_hnsw
    ON attachment_chunks
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- User Tool Permissions
CREATE TABLE user_tool_permissions (
    user_id TEXT NOT NULL PRIMARY KEY,
    permissions_json TEXT NOT NULL DEFAULT '{}',
    updated_at TEXT NOT NULL
);
