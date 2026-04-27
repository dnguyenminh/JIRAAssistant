# AI-Generated BRD/FSD — Tasks

## Task 1: Shared Module — Document Models
- [x] 1.1 Create `shared/.../document/models/GenerationContext.kt` with `GenerationContext`, `AttachmentChunkInfo`, `SprintMetadata` data classes
- [x] 1.2 Create `shared/.../document/models/GeneratedDocument.kt` with `GeneratedDocument` data class (documentType, ticketId, markdownContent, generatedAt, sourceTicketIds, attachmentSources, aiProviderUsed)
- [x] 1.3 Create `shared/.../document/models/DocumentType.kt` with enum: BRD, FSD, REQUIREMENT_SLIDES
- [x] 1.4 Create `shared/.../document/models/DocumentStatus.kt` with `DocumentStatus` data class (phase, progressPercent, documentType)
- [x] 1.5 Create `shared/.../document/models/DocumentSection.kt` with `DocumentSection` data class (heading, content, sourceRefs)

## Task 2: Shared Module — Document Aggregator Interface
- [x] 2.1 Create `shared/.../document/DocumentAggregator.kt` — interface with `suspend fun aggregate(ticketId: String): GenerationContext`

## Task 3: Shared Module — BRD Prompt Builder & Response Parser
- [x] 3.1 Create `shared/.../document/BrdPromptBuilder.kt` — builds AI prompt with GenerationContext + 9 BRD section headings + anti-hallucination instructions + source citation format + draw.io diagram instructions (3 diagrams: Business Process Flow, Stakeholder Map, Risk Matrix)
- [x] 3.2 Create `shared/.../document/BrdResponseParser.kt` — parses AI Markdown response, validates 9 sections present, fills missing sections with "⚠️ Insufficient data" default, extracts source citations `[Source: ...]`

## Task 4: Shared Module — FSD Prompt Builder & Response Parser
- [x] 4.1 Create `shared/.../document/FsdPromptBuilder.kt` — builds AI prompt with GenerationContext + 10 FSD section headings + anti-hallucination instructions + source citation format + draw.io diagram instructions (4 diagrams: System Architecture, Data Flow, Integration Architecture, Deployment)
- [x] 4.2 Create `shared/.../document/FsdResponseParser.kt` — parses AI Markdown response, validates 10 sections present, fills missing sections with default, extracts source citations

## Task 5: Shared Module — Document Section Validator
- [x] 5.1 Create `shared/.../document/DocumentSectionValidator.kt` — validates section headings against template, checks source citation format `[Source: ...]`, detects insufficient data warnings `⚠️`

## Task 6: Shared Module — Slide Generator
- [x] 6.1 Create `shared/.../document/SlideGenerator.kt` — parses BRD Markdown → extracts section contents → generates 7 slides (Vision, Requirements Table, Data Flow, Scope, Stakeholders, Risks, Timeline) with `---` separators, max 5-7 bullet points per slide

## Task 7: Server Module — Database Schema & Repository
- [x] 7.1 Add `generated_documents` table to database schema (id, ticket_id, document_type, markdown_content, generated_at, source_ticket_ids, attachment_sources, ai_provider_used) with UNIQUE(ticket_id, document_type) constraint
- [x] 7.2 Create `server/.../db/DocumentRepository.kt` — interface: save, findByTicketId, findByTicketIdAndType, listByTicketId
- [x] 7.3 Create `server/.../db/DocumentRepositoryImpl.kt` — SQLite implementation with upsert logic (overwrite on re-generate)

## Task 8: Server Module — Document Aggregator Implementation
- [x] 8.1 Create `server/.../document/DocumentAggregatorImpl.kt` — implements DocumentAggregator: (a) fetch main KBRecord from KBRepository, error if no deep analysis, (b) fetch linked tickets + sub-tasks KBRecords (max 20), skip missing with log warning, (c) semantic search VectorStore with EmbeddingService (max 10 chunks), (d) combine into GenerationContext

## Task 9: Server Module — Document Routes & Status Tracker
- [x] 9.1 Create `server/.../routes/DocumentStatusTracker.kt` — ConcurrentHashMap tracking 3 phases: AGGREGATING_DATA (0-30%), GENERATING_DOCUMENT (30-90%), COMPLETE (90-100%)
- [x] 9.2 Create `server/.../routes/DocumentRoutes.kt` — POST generate-brd, POST generate-fsd (with RBAC ANALYZE_AI check, aggregate → build prompt → call AI → parse response → save document → return), GET document-status, GET documents (list metadata), GET documents/{type} (full content). Retry AI max 2 times. Reader role → 403
- [x] 9.3 Register DocumentRoutes in Ktor routing configuration and register DocumentRepository + DocumentAggregatorImpl in Koin DI module

## Task 10: Server Module — Slide Generation Endpoint
- [x] 10.1 Add POST `/api/analysis/{ticketId}/generate-slides` endpoint to DocumentRoutes — requires existing BRD (HTTP 400 if not), calls SlideGenerator with BRD content, saves with type REQUIREMENT_SLIDES

## Task 11: Frontend — Document Display Models
- [x] 11.1 Create `frontend/.../models/DocumentModels.kt` — `GeneratedDocumentMeta` (documentType, generatedAt, aiProviderUsed), `GeneratedDocumentFull` (metadata + markdownContent + sourceTicketIds + attachmentSources), `DocumentGenerationStatus` (phase, progressPercent, documentType)

## Task 12: Frontend — HTML Template Extension
- [x] 12.1 Add "DOCUMENT GENERATION" section to `ticket-intelligence.html` — section with id `ti-docgen-section` (hidden by default), "Generate BRD" and "Generate FSD" buttons, badge areas for timestamps, "Generate Slides" button (disabled by default)
- [x] 12.2 Add Document Preview modal to `ticket-intelligence.html` — full-width overlay with id `doc-preview-modal`, metadata bar, TOC sidebar, Markdown content area, Export dropdown button, Close button
- [x] 12.3 Add Slide Preview modal to `ticket-intelligence.html` — overlay with id `slide-preview-modal`, slide content area, Previous/Next buttons, slide counter, Close button
- [x] 12.4 Add `<template>` tags for dynamic elements: `tmpl-doc-badge` (generated badge with timestamp), `tmpl-toc-item` (TOC sidebar entry), `tmpl-slide` (single slide container)

## Task 13: Frontend — Markdown Renderer
- [x] 13.1 Create `frontend/.../pages/ticket/MarkdownRenderer.kt` — lazy loads marked.js from CDN, renders Markdown string → sanitized HTML string, fallback to raw text on CDN failure

## Task 14: Frontend — Document Generation Section
- [x] 14.1 Create `frontend/.../pages/ticket/DocumentGenerationSection.kt` — renders DOCUMENT GENERATION section: shows/hides based on ticket analysis state (ANALYZED → visible, else hidden), renders Generate BRD/FSD buttons, fetches existing document metadata via GET /documents to show badges, disables buttons for Reader role, handles Generate Slides button state (enabled only when BRD exists)

## Task 15: Frontend — Document Generation Flow
- [x] 15.1 Create `frontend/.../pages/ticket/DocumentGenerationFlow.kt` — orchestrates generation: BlockingOverlay show → POST generate-brd/fsd → poll document-status for progress → on complete: fetch full document → open DocumentPreviewPanel → BlockingOverlay remove

## Task 16: Frontend — Document Preview Panel
- [x] 16.1 Create `frontend/.../pages/ticket/DocumentPreviewPanel.kt` — opens doc-preview-modal, renders metadata bar (type, ticketId, timestamp, provider, source tickets), renders Markdown via MarkdownRenderer, renders draw.io diagrams inline via DrawioDiagramRenderer, handles Close button and Escape key
- [x] 16.2 Create `frontend/.../pages/ticket/DocumentPreviewToc.kt` — extracts H2 headings from rendered HTML, creates TOC sidebar entries via template clone, click handler scrolls to section

## Task 17: Frontend — Document Exporter
- [x] 17.1 Create `frontend/.../pages/ticket/DocumentExporter.kt` — Export dropdown: Markdown export creates Blob with header (type, ticketId, timestamp, source tickets) + markdownContent, triggers download as `{ticketId}-{type}.md`. PDF export applies print CSS, calls window.print()

## Task 18: Frontend — Slide Preview Panel
- [x] 18.1 Create `frontend/.../pages/ticket/SlidePreviewPanel.kt` — opens slide-preview-modal, splits Markdown by `---`, renders current slide via MarkdownRenderer, Previous/Next navigation, slide counter display, Close/Escape handling

## Task 19: Frontend — Integration with TicketIntelligencePage
- [x] 19.1 Update `TicketIntelligencePage.kt` — after analysis result renders, call `DocumentGenerationSection.render()` to show/hide document generation section
- [x] 19.2 Update `TicketResultTabs.kt` — after tab content renders, check and refresh document generation section state (badge updates when returning from preview)

## Task 20: Frontend — CSS for Document Components
- [x] 20.1 Add CSS rules to `ticket-intelligence.css` — styles for doc-preview-modal (full-width overlay, glass-card background), TOC sidebar (fixed left, scrollable), metadata bar, slide navigation buttons, print CSS (@media print), export dropdown

## Task 21: Property-Based Tests
- [x] 21.1 ⬡ PBT: Aggregation respects capacity limits — generate random linked ticket lists (0-100) and attachment chunk lists (0-50), verify GenerationContext always has ≤20 linked analyses and ≤10 chunks
- [x] 21.2 ⬡ PBT: Aggregation fault tolerance — generate random mixes of available/missing linked tickets, verify aggregation always succeeds
- [x] 21.3 ⬡ PBT: BRD prompt completeness — generate random GenerationContext, verify prompt contains all 9 section headings + anti-hallucination instructions + source citation format
- [x] 21.4 ⬡ PBT: FSD prompt completeness — generate random GenerationContext, verify prompt contains all 10 section headings + anti-hallucination instructions + source citation format
- [x] 21.5 ⬡ PBT: BRD parser section invariant — generate random Markdown (0-9 sections), verify parser always outputs exactly 9 sections
- [x] 21.6 ⬡ PBT: FSD parser section invariant — generate random Markdown (0-10 sections), verify parser always outputs exactly 10 sections
- [x] 21.7 ⬡ PBT: BRD Markdown round-trip — generate random 9-section Markdown, parse → serialize → verify all headings preserved in order
- [x] 21.8 ⬡ PBT: FSD Markdown round-trip — generate random 10-section Markdown, parse → serialize → verify all headings preserved in order
- [x] 21.9 ⬡ PBT: Document overwrite idempotence — save document N times, verify findByTicketIdAndType returns exactly 1 with latest content
- [x] 21.10 ⬡ PBT: Slide generation structure — generate random BRD Markdown, verify slides have `---` separators, ≤7 slides, ≤7 bullets per slide
- [x] 21.11 ⬡ PBT: GeneratedDocument serialization round-trip — generate random GeneratedDocument, serialize → deserialize → verify equality

## Task 22: Unit Tests (Example-Based)
- [x] 22.1 Test: Aggregator rejects unanalyzed ticket (no deep analysis → error)
- [x] 22.2 Test: BRD prompt includes businessSummary and dependency context
- [x] 22.3 Test: FSD prompt expands API specifications and database changes
- [x] 22.4 Test: Parser fills empty string with all default sections
- [x] 22.5 Test: SlideGenerator rejects when BRD not provided
- [x] 22.6 Test: Export filename format matches `{ticketId}-{documentType}.md`
- [x] 22.7 Test: Draw.io prompt requests correct diagram types (BRD=3, FSD=4)
