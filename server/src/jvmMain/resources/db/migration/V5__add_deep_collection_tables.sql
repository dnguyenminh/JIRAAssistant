-- V5: Add deep collection tables for background jobs, traversal cache, and rate limiting.
-- Supports Deep Ticket Data Collection feature (Req 13.3, 15.1, 16.1).

-- Collection Jobs — background processing of linked tickets and attachments
CREATE TABLE collection_jobs (
    job_id TEXT PRIMARY KEY,
    parent_ticket_id TEXT NOT NULL,
    job_type TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'QUEUED',
    total_items INTEGER NOT NULL,
    completed_items INTEGER NOT NULL DEFAULT 0,
    failed_items INTEGER NOT NULL DEFAULT 0,
    items_json TEXT NOT NULL DEFAULT '[]',
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    version INTEGER NOT NULL DEFAULT 1
);

CREATE INDEX idx_collection_jobs_parent ON collection_jobs(parent_ticket_id);
CREATE INDEX idx_collection_jobs_status ON collection_jobs(status);

-- Traversal Cache — cached BFS ticket graphs
CREATE TABLE traversal_cache (
    root_ticket_id TEXT PRIMARY KEY,
    graph_json TEXT NOT NULL,
    cached_at TEXT NOT NULL,
    root_updated_at TEXT NOT NULL
);

-- Deep Collection Rate Limits — per-user hourly cap
CREATE TABLE deep_collection_rate_limits (
    user_id TEXT NOT NULL,
    requested_at TEXT NOT NULL,
    PRIMARY KEY (user_id, requested_at)
);

CREATE INDEX idx_rate_limits_user ON deep_collection_rate_limits(user_id);
