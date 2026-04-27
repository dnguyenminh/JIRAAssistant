-- Add started_at column to track when job transitions QUEUED → RUNNING.
-- Supports Document Generation UX Improvement (Req 1.6, 7.1).

ALTER TABLE generation_jobs ADD COLUMN started_at TEXT;
