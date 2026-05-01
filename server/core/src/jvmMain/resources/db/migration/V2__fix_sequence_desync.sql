-- V2: Fix sequence desync for chat_messages and other SERIAL/BIGSERIAL tables.
--
-- Root cause: sequences can fall behind MAX(id) after bulk imports,
-- pg_restore with --data-only, or manual INSERT with explicit IDs.
-- This causes "duplicate key" errors on subsequent INSERTs.
--
-- Fix: reset each sequence to MAX(id) of its owning table.

SELECT setval('chat_messages_id_seq',
    GREATEST((SELECT COALESCE(MAX(id), 0) FROM chat_messages), 1));

SELECT setval('scan_log_id_seq',
    GREATEST((SELECT COALESCE(MAX(id), 0) FROM scan_log), 1));

SELECT setval('attachment_chunks_id_seq',
    GREATEST((SELECT COALESCE(MAX(id), 0) FROM attachment_chunks), 1));
