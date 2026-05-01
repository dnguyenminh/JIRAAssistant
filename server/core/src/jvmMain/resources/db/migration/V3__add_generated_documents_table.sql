-- V3: Add generated_documents table for BRD/FSD/REQUIREMENT_SLIDES storage.
-- Supports document generation feature (Req 5.1).

CREATE TABLE generated_documents (
    id BIGSERIAL PRIMARY KEY,
    ticket_id TEXT NOT NULL,
    document_type TEXT NOT NULL,
    markdown_content TEXT NOT NULL,
    generated_at TEXT NOT NULL,
    source_ticket_ids TEXT NOT NULL DEFAULT '[]',
    attachment_sources TEXT NOT NULL DEFAULT '[]',
    ai_provider_used TEXT NOT NULL DEFAULT '',
    UNIQUE(ticket_id, document_type)
);

CREATE INDEX idx_generated_documents_ticket
    ON generated_documents(ticket_id);
