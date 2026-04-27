-- V4: Add generation_jobs table and versioning columns to generated_documents.
-- Supports Document Job Manager feature (Req 2.1, 7.1).

CREATE TABLE generation_jobs (
    job_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id TEXT NOT NULL,
    document_type TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'QUEUED',
    progress_percent INTEGER NOT NULL DEFAULT 0,
    phase TEXT NOT NULL DEFAULT 'QUEUED',
    chain_id UUID,
    created_by TEXT NOT NULL DEFAULT '',
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    error_message TEXT
);

CREATE INDEX idx_jobs_ticket ON generation_jobs(ticket_id);
CREATE INDEX idx_jobs_status ON generation_jobs(status);
CREATE INDEX idx_jobs_chain ON generation_jobs(chain_id);

-- Drop old UNIQUE constraint to allow multiple versions per ticket+type
ALTER TABLE generated_documents
    DROP CONSTRAINT IF EXISTS generated_documents_ticket_id_document_type_key;

-- Add versioning and approval columns
ALTER TABLE generated_documents ADD COLUMN approval_status TEXT NOT NULL DEFAULT 'DRAFT';
ALTER TABLE generated_documents ADD COLUMN version_number INTEGER;
ALTER TABLE generated_documents ADD COLUMN reject_reason TEXT;
ALTER TABLE generated_documents ADD COLUMN reviewed_by TEXT;
ALTER TABLE generated_documents ADD COLUMN reviewed_at TEXT;

CREATE INDEX idx_docs_approval ON generated_documents(ticket_id, document_type, approval_status);
CREATE INDEX idx_docs_version ON generated_documents(ticket_id, document_type, version_number);
