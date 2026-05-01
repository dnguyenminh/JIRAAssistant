# AI-Generated BRD/FSD from Ticket Analysis — Design

## Overview

Feature này cho phép hệ thống tổng hợp dữ liệu từ ticket chính + linked tickets + sub-tasks + attachment content (VectorStore) để sinh ra BRD (Business Requirements Document) và FSD (Functional Specification Document) hoàn chỉnh, có thể preview và export. Ngoài ra hỗ trợ sinh Requirements Summary Slides từ BRD.

### Quyết định thiết kế chính

1. **Document_Aggregator** là pure function trong shared module — thu thập KBRecord + linked tickets + VectorStore chunks thành `GenerationContext`, testable với property-based testing
2. **BRD/FSD Generators** là prompt builders trong shared module — xây dựng AI prompt từ `GenerationContext` + template, parse AI response thành Markdown
3. **Document storage** dùng bảng `generated_documents` riêng biệt (không mở rộng `kb_records`) — tách biệt analysis data và generated documents
4. **Frontend Document Preview** dùng full-width modal overlay (`doc-preview-modal`) — rendered Markdown với TOC sidebar, mở từ DocumentPreviewPanel
5. **Markdown rendering** dùng `marked.js` CDN — lazy load khi cần, tương tự pattern Mermaid.js
6. **PDF export** dùng `window.print()` với print CSS — không cần thêm library
7. **Draw.io diagrams** trong BRD/FSD — AI sinh XML trực tiếp hoặc JSON metadata. `DocumentPreviewPanel` scan code blocks cho cả `<mxGraphModel>` XML (render trực tiếp) và JSON metadata format (`"nodes"` + `"connections"` — convert sang XML via `DrawioTemplateEngine.merge()`), render inline via `window.__drawioRenderOne` helper
8. **Requirement Slides** sinh từ BRD content (không gọi AI lại) — parse BRD Markdown, extract sections, condensed thành slide format
9. **Anti-hallucination** enforced qua prompt instructions + section validation + source citation parsing
10. **Progress tracking** dùng cùng pattern `AnalysisStatusTracker` — 3 phases: AGGREGATING_DATA, GENERATING_DOCUMENT, COMPLETE

---

## Architecture

```mermaid
graph TB
    subgraph "Frontend (Kotlin/JS)"
        TI_PAGE[TicketIntelligencePage]
        DOC_GEN_SECTION[DocumentGenerationSection<br>Generate BRD/FSD buttons]
        DOC_PREVIEW[DocumentPreviewPanel<br>rendered Markdown + TOC]
        DOC_EXPORT[DocumentExporter<br>MD download + PDF print]
        SLIDE_PREVIEW[SlidePreviewPanel<br>slide-by-slide navigation]
    end

    subgraph "Backend (Ktor)"
        DOC_ROUTES[DocumentRoutes<br>/api/analysis/{ticketId}/generate-brd<br>/api/analysis/{ticketId}/generate-fsd<br>/api/analysis/{ticketId}/documents]
        DOC_STATUS[DocumentStatusTracker<br>3-phase progress]
    end

    subgraph "Shared Module — Document Generation"
        DOC_AGGREGATOR[DocumentAggregator<br>collect KB + linked + attachments]
        BRD_PROMPT[BrdPromptBuilder<br>7-section Carleton ITS template + anti-hallucination]
        FSD_PROMPT[FsdPromptBuilder<br>10-section template + anti-hallucination]
        BRD_PARSER[BrdResponseParser<br>Markdown validation + section check]
        FSD_PARSER[FsdResponseParser<br>Markdown validation + section check]
        SLIDE_GEN[SlideGenerator<br>BRD → condensed slides]
        DOC_MODELS[DocumentModels<br>GenerationContext, GeneratedDocument]
    end

    subgraph "Shared Module — Existing"
        AI_AGENT[AIAgent<br>analyze prompt]
        KB_REPO[KBRepository<br>findByTicketId]
    end

    subgraph "Server — Existing"
        VECTOR_STORE[VectorStore<br>semantic search]
        EMBED_SVC[EmbeddingService<br>query embedding]
        DOC_REPO[DocumentRepository<br>CRUD generated_documents]
    end

    TI_PAGE --> DOC_GEN_SECTION
    DOC_GEN_SECTION -->|"POST generate-brd/fsd"| DOC_ROUTES
    DOC_ROUTES --> DOC_AGGREGATOR
    DOC_AGGREGATOR --> KB_REPO
    DOC_AGGREGATOR --> VECTOR_STORE
    DOC_AGGREGATOR --> EMBED_SVC
    DOC_ROUTES --> AI_AGENT
    DOC_ROUTES --> BRD_PROMPT
    DOC_ROUTES --> FSD_PROMPT
    DOC_ROUTES --> BRD_PARSER
    DOC_ROUTES --> FSD_PARSER
    DOC_ROUTES --> DOC_REPO
    DOC_ROUTES --> SLIDE_GEN
    DOC_PREVIEW --> DOC_EXPORT
    DOC_GEN_SECTION --> DOC_PREVIEW
    DOC_GEN_SECTION --> SLIDE_PREVIEW
```

---

## Components and Interfaces

### Shared Module — Document Models (`com.assistant.document.models/`)

| Component | File | Trách nhiệm |
|-----------|------|-------------|
| `GenerationContext` | `models/GenerationContext.kt` | Aggregated data: main KBRecord + linked analyses + attachment chunks + sprint metadata |
| `GeneratedDocument` | `models/GeneratedDocument.kt` | Output model: documentType, ticketId, markdownContent, generatedAt, sourceTicketIds, attachmentSources, aiProviderUsed |
| `DocumentType` | `models/DocumentType.kt` | Enum: BRD, FSD, REQUIREMENT_SLIDES |
| `DocumentStatus` | `models/DocumentStatus.kt` | Progress status: phase, progressPercent, documentType |
| `DocumentSection` | `models/DocumentSection.kt` | Parsed section: heading, content, sourceRefs |

### Shared Module — Document Generation (`com.assistant.document/`)

| Component | File | Trách nhiệm |
|-----------|------|-------------|
| `DocumentAggregator` | `DocumentAggregator.kt` | Collects KBRecord + linked tickets (max 20) + attachment chunks (max 10) → `GenerationContext`. Interface for testability |
| `BrdPromptBuilder` | `BrdPromptBuilder.kt` | Object singleton, exposes `BRD_SECTIONS` (7 top-level), `BRD_SUB_SECTIONS`, `BRD_DEEP_SUB_SECTIONS` maps and `buildPrompt(context)`. Delegates prompt section building to `BrdPromptSections.kt` |
| `BrdPromptSections` | `BrdPromptSections.kt` | StringBuilder extension functions for BRD prompt sections: appendRole (Senior BA persona, 15+ years FECredit), appendContextData, appendMainTicketData, appendLinkedTicketsData, appendAttachmentData, appendSprintData, appendBrdTemplate (calls appendDataMappingInstructions), appendBrdSubSections, appendBrdDeepSubSections, appendInstructions (delegates to appendAntiHallucinationRules + appendRequirementFormatRules + appendNfrCoverageRules + appendSectionCompletionRules), appendOutputFormat |
| `BrdPromptMappingInstructions` | `BrdPromptMappingInstructions.kt` | Explicit data mapping instructions: Project Overview ← businessSummary/toBeState, Existing Processes ← asIsState, Project Requirements ← extractedRequirements/acceptanceCriteria/technicalDetails, Appendix ← attachmentChunks/externalIntegrations |
| `BrdPromptDiagramInstructions` | `BrdPromptDiagramInstructions.kt` | Draw.io diagram instructions for BRD: 3 diagrams (Process Flow, Requirements Traceability, Stakeholder Map) as raw `<mxGraphModel>` XML in code blocks |
| `FsdPromptBuilder` | `FsdPromptBuilder.kt` | Object singleton, exposes `FSD_SECTIONS` (11 top-level), `FSD_SUB_SECTIONS` map and `buildPrompt(context)`. Delegates to `FsdPromptSections.kt`, reuses shared context extensions from BrdPromptSections |
| `FsdPromptSections` | `FsdPromptSections.kt` | FSD-specific StringBuilder extensions: appendFsdRole (Senior Architect persona, 15+ years FECredit), appendFsdTemplate (calls appendFsdDataMappingInstructions), appendFsdSubSections, appendFsdInstructions (delegates to appendFsdAntiHallucinationRules + appendFsdUseCaseFormatRules + appendFsdApiContractRules + appendFsdBrdTraceabilityRules + appendFsdSectionCompletionRules), appendFsdDiagramInstructions, appendFsdTechnicalExpansion (API specs, DB changes, integrations) |
| `FsdPromptMappingInstructions` | `FsdPromptMappingInstructions.kt` | Explicit data mapping instructions: Introduction ← businessSummary/dependencies, System Overview ← technicalDetails/externalIntegrations, Functional Specs ← extractedRequirements/acceptanceCriteria, Integration ← apiSpecifications, Data Migration ← databaseChanges |
| `BrdResponseParser` | `BrdResponseParser.kt` | Validates AI response has 7 top-level sections (Carleton ITS template) using case-insensitive + fuzzy heading matching via `findSectionCaseInsensitive()`, fills missing or blank sections with default text, extracts source citations. Provides `serialize()` for Markdown round-trip |
| `FsdResponseParser` | `FsdResponseParser.kt` | Validates AI response has 11 sections (FECredit template) using case-insensitive + fuzzy heading matching via `findSectionCaseInsensitive()`, fills missing or blank sections with default text, extracts source citations. Provides `serialize()` for Markdown round-trip |
| `DocumentParserUtils` | `DocumentParserUtils.kt` | Shared parsing utilities: `parseMarkdownSections()` uses H1/H2 headings as section boundaries (preferred), with H3 fallback when no H1/H2 found. H3 sub-headings within H2 sections are preserved as content. `normalizeHeading()` strips numbering prefixes, bold markers, trailing whitespace. `findSectionCaseInsensitive()` provides case-insensitive + fuzzy heading lookup. `extractSourceCitations()` extracts [Source: ...] refs — used by both BRD and FSD parsers |
| `SlideGenerator` | `SlideGenerator.kt` | Entry point object singleton with `generate(brdMarkdown)`. Delegates to SlideGeneratorHelpers + SlideContentExtractor |
| `SlideGeneratorHelpers` | `SlideGeneratorHelpers.kt` | Builds 7 individual slides from BRD section map. Uses `getContentWithFallback()` to try alternative sections when primary section is empty/warning. Maps to Carleton ITS BRD section names: Project Overview, Project Requirements, Existing Processes, Sign Off, Appendix |
| `SlideContentExtractor` | `SlideContentExtractor.kt` | Specialized content extractors per slide type: extractVisionBullets (business problem/solution/value), extractRequirementBullets (PREQ-NNN items), extractScopeBullets (In-Scope/Out-of-Scope tables), extractRiskBullets (blocking issues/risk indicators), extractTableRows (table → bullets), extractPreqItems (PREQ-NNN titles). Fallback: existing bullets → table rows → sentences. Warning message for empty/insufficient content. |
| `DocumentSectionValidator` | `DocumentSectionValidator.kt` | Validates section headings, checks source citations format, detects missing data warnings |

### Server Module — Routes & Storage (`com.assistant.server.routes/`)

| Component | File | Trách nhiệm |
|-----------|------|-------------|
| `DocumentRoutes` | `routes/DocumentRoutes.kt` | POST generate-brd, POST generate-fsd (async, return 202 Accepted), POST generate-slides, GET documents, GET documents/{type}, GET document-status. RBAC check ANALYZE_AI. Logic delegated to `DocumentRouteHandlers.kt` |
| `DocumentRouteHandlers` | `routes/DocumentRouteHandlers.kt` | Async generation pipeline: aggregateData → buildDocPrompt → resolveAIAgent → callAIWithRetry → parseAIResponse → saveDocument. Handles slides generation. AI agent resolved directly via ProviderConfigRepository (not AIOrchestrator) |
| `DocumentStatusTracker` | `routes/DocumentStatusTracker.kt` | ConcurrentHashMap tracking 3 phases: AGGREGATING_DATA (0-30%), GENERATING_DOCUMENT (30-90%), COMPLETE (90-100%) |
| `DocumentRepository` | `db/DocumentRepository.kt` | Interface: save, findByTicketId, findByTicketIdAndType, listByTicketId |
| `PgDocumentRepository` | `db/pg/PgDocumentRepository.kt` | PostgreSQL implementation with INSERT ON CONFLICT upsert for `generated_documents` table |
| `PgDocumentSql` | `db/pg/PgDocumentSql.kt` | SQL constants for PgDocumentRepository (UPSERT, FIND, LIST queries) |
| `DocumentAggregatorImpl` | `document/DocumentAggregatorImpl.kt` | Server-side implementation — injects KBRepository, VectorStore, EmbeddingService. Logs warning when deep analysis fields (asIsState, toBeState, extractedRequirements, acceptanceCriteria) are all empty after basic analysis passes (Req 2.5 from brd-insufficient-data-fix) |

### Frontend — Document Generation UI (`com.assistant.frontend.pages.ticket/`)

| Component | File | Trách nhiệm |
|-----------|------|-------------|
| `DocumentGenerationSection` | `pages/ticket/DocumentGenerationSection.kt` | Renders "DOCUMENT GENERATION" section with Generate BRD/FSD/Slides buttons, badge timestamps, RBAC disable |
| `DocumentPreviewPanel` | `pages/ticket/DocumentPreviewPanel.kt` | Full-width modal: rendered Markdown via marked.js, metadata bar, TOC sidebar, Close/Escape, Export button. Diagram rendering: collects code blocks, calls `DrawioDiagramRenderer.ensureViewerLoaded()` to load draw.io viewer CDN, then scans for `<mxGraphModel>` XML (direct render) and JSON metadata (convert via `DocumentPreviewDiagramHelper` + `DrawioTemplateEngine`). Fallback: if viewer fails or XML is invalid, shows "📊 Draw.io diagram (viewer unavailable)" with XML preview. |
| `DocumentPreviewDiagramHelper` | `pages/ticket/DocumentPreviewDiagramHelper.kt` | Parses JSON diagram metadata from AI code blocks into `DrawioMetadata`. Handles type mapping (process→server, external→external_api) and template mapping (System Architecture→component, Data Flow→flow). Graceful fallback on parse failure. |
| `DocumentPreviewToc` | `pages/ticket/DocumentPreviewToc.kt` | TOC sidebar: extracts H2 headings from rendered HTML, click-to-scroll |
| `DocumentExporter` | `pages/ticket/DocumentExporter.kt` | Export dropdown: Markdown (.md) download via Blob, PDF via window.print() with print CSS |
| `SlidePreviewPanel` | `pages/ticket/SlidePreviewPanel.kt` | Slide navigation: split by `---`, Previous/Next buttons, slide counter |
| `DocumentGenerationFlow` | `pages/ticket/DocumentGenerationFlow.kt` | Orchestrates: BlockingOverlay → POST generate → poll job status → fetch DRAFT document (`?status=DRAFT`) → show preview. Also provides `fetchDraftAndPreview()` for opening DRAFT documents from badge clicks. |
| `MarkdownRenderer` | `pages/ticket/MarkdownRenderer.kt` | Lazy loads marked.js CDN, renders Markdown → HTML, sanitizes output |

### Frontend — HTML Template Extension

| Resource | Path | Trách nhiệm |
|----------|------|-------------|
| Template update | `resources/templates/ticket-intelligence.html` | Add Document Generation section + Document Preview modal + Slide Preview modal |


### Frontend — Display Models (`com.assistant.frontend.models/`)

| Model | File | Trách nhiệm |
|-------|------|-------------|
| `GeneratedDocumentMeta` | `models/DocumentModels.kt` | Metadata for document list: documentType, generatedAt, aiProviderUsed |
| `GeneratedDocumentFull` | `models/DocumentModels.kt` | Full document: metadata + markdownContent + sourceTicketIds + attachmentSources |
| `DocumentGenerationStatus` | `models/DocumentModels.kt` | Polling status: phase, progressPercent, documentType |

---

## Data Models

### Database Schema — `generated_documents` table (Req 5.1)

```sql
CREATE TABLE generated_documents (
    id BIGSERIAL PRIMARY KEY,
    ticket_id TEXT NOT NULL,
    document_type TEXT NOT NULL,          -- 'BRD', 'FSD', 'REQUIREMENT_SLIDES'
    markdown_content TEXT NOT NULL,
    generated_at TEXT NOT NULL,           -- ISO 8601 timestamp
    source_ticket_ids TEXT NOT NULL DEFAULT '[]',  -- JSON array
    attachment_sources TEXT NOT NULL DEFAULT '[]',  -- JSON array
    ai_provider_used TEXT NOT NULL DEFAULT '',
    UNIQUE(ticket_id, document_type)     -- One document per type per ticket (overwrite on re-generate)
);

CREATE INDEX idx_generated_documents_ticket
    ON generated_documents(ticket_id);
```

Migration file: `server/src/jvmMain/resources/db/migration/V3__add_generated_documents_table.sql`

### GenerationContext — `shared/.../document/models/GenerationContext.kt` (Req 1.4)

```kotlin
@Serializable
data class GenerationContext(
    val mainTicket: KBRecord,
    val linkedTicketAnalyses: List<KBRecord> = emptyList(),
    val attachmentChunks: List<AttachmentChunkInfo> = emptyList(),
    val sprintMetadata: SprintMetadata? = null
)

@Serializable
data class AttachmentChunkInfo(
    val filename: String,
    val content: String,
    val similarityScore: Float = 0f
)

@Serializable
data class SprintMetadata(
    val sprintName: String = "",
    val startDate: String = "",
    val endDate: String = ""
)
```

### GeneratedDocument — `shared/.../document/models/GeneratedDocument.kt` (Req 4.1, 4.2)

```kotlin
@Serializable
data class GeneratedDocument(
    val documentType: String,              // "BRD", "FSD", "REQUIREMENT_SLIDES"
    val ticketId: String,
    val generatedAt: String,               // ISO 8601
    val markdownContent: String,
    val sourceTicketIds: List<String> = emptyList(),
    val attachmentSources: List<String> = emptyList(),
    val aiProviderUsed: String = ""
)
```

### DocumentStatus — `shared/.../document/models/DocumentStatus.kt` (Req 4.3)

```kotlin
@Serializable
data class DocumentStatus(
    val phase: String,          // "AGGREGATING_DATA", "GENERATING_DOCUMENT", "COMPLETE", "FAILED"
    val progressPercent: Int,   // 0-100
    val documentType: String
)
```

### DocumentSection — `shared/.../document/models/DocumentSection.kt`

```kotlin
@Serializable
data class DocumentSection(
    val heading: String,
    val content: String,
    val sourceRefs: List<String> = emptyList()
)
```

### BRD Template Sections (Req 2.1 — Carleton University ITS Business Requirements Template)

| # | Section | Sub-sections | Source trong GenerationContext |
|---|---------|-------------|-------------------------------|
| 1 | Revision History | — | Auto-generated: revision history, ticket metadata |
| 2 | Project Overview | Project Sponsor(s), Project Contributors, In Scope (Deliverables), Out of Scope | mainTicket.businessSummary + toBeState + dependencies |
| 3 | Common Project Acronyms, Names, and Descriptions | — | Auto-generated: domain terms from context |
| 4 | Existing Processes | Summary Process Narrative, Timing, Volume, Screenshots, Problems | mainTicket.asIsState |
| 5 | Project Requirements | Process Overview (Summary Process Narrative, Flow Diagram, Triggering Event, Timing, Volume, Outcomes), Functional Requirements, Non-Functional Requirements (Availability, Compatibility, Extensibility, Maintainability, Scalability, Security, Usability, Performance), Data Requirements (Known Issues/Assumptions/Risks/Dependencies) | mainTicket.extractedRequirements + acceptanceCriteria + technicalDetails + linked tickets |
| 6 | Sign Off | — | Auto-generated: stakeholder sign-off table |
| 7 | Appendix | Mock-ups, Glossary, Business Rules and Procedures, Document References | Attachment references, supplementary data |

### FSD Template Sections (Req 3.1 — FECredit Functional Specification Template)

| # | Section | Sub-sections | Source trong GenerationContext |
|---|---------|-------------|-------------------------------|
| 1 | Introduction | Purpose of the Document, Project Scope, Scope of the Document, Related Documents, Terms/Acronyms and Definitions, Risks and Assumptions | mainTicket.businessSummary + dependencies |
| 2 | System/Solution Overview | Context/Interface/Data Flow Diagrams, System Actors, Dependencies and Change Impacts | mainTicket.diagrams + technicalDetails + dependencies |
| 3 | Functional Specifications | Purpose/Description, Use Cases, Mock-ups, Functional Requirements, Field Level Specifications | mainTicket.extractedRequirements + acceptanceCriteria |
| 4 | System Configurations | — | inferred from technicalDetails |
| 5 | Non-Functional Requirements | — | inferred from technicalDetails + attachments |
| 6 | Reporting Requirements | — | inferred from requirements + business context |
| 7 | Integration Requirements | Exception Handling/Error Reporting | mainTicket.technicalDetails.externalIntegrations |
| 8 | Data Migration/Conversion Requirements | Data Conversion Strategy, Data Conversion Preparation, Data Conversion Specifications | mainTicket.technicalDetails.databaseChanges |
| 9 | References | — | Document references, related materials |
| 10 | Open Issues | — | Known issues, pending decisions |
| 11 | Appendix | — | Supplementary data, attachment references |

### DocumentAggregator Interface (Req 1.1-1.5)

```kotlin
interface DocumentAggregator {
    suspend fun aggregate(ticketId: String): GenerationContext
}
```

Server implementation (`DocumentAggregatorImpl`):
1. Fetch main ticket `KBRecord` from `KBRepository` — error if not found or no deep analysis (Req 1.1)
2. Log warning if deep analysis fields (asIsState, toBeState, extractedRequirements, acceptanceCriteria) are all empty — does not block generation (Req 2.5 from brd-insufficient-data-fix)
3. Fetch linked tickets + sub-tasks KBRecords (max 20) — skip missing, log warning (Req 1.2, 1.5)
4. Semantic search in `VectorStore` with `businessSummary + extractedRequirements` as query (Req 1.3) — via `EmbeddingService.embed()` → `VectorStore.search(embedding, topK=10)`
5. Combine into `GenerationContext` (Req 1.4)

### BrdPromptBuilder / FsdPromptBuilder (Req 2.1-2.8, 3.1-3.9, 9.1-9.3)

Prompt structure:
```
ROLE: You are a Senior Business Analyst / Technical Architect with 15+ years at FECredit.
CONTEXT: [serialized GenerationContext]
TEMPLATE: [7 BRD or 11 FSD section headings with sub-sections]
DATA MAPPING: [explicit field-to-section mapping — which CONTEXT fields → which sections]
INSTRUCTIONS:
  - Use ONLY data from CONTEXT. Do NOT fabricate.
  - Cite sources as [Source: TICKET-ID] or [Source: filename.pdf]
  - NEVER leave a section empty — analyze available context, mark [ASSUMPTION]
  - Requirements: PREQ-NNN format with Priority (Must/Should/Could) and testable Acceptance Criteria
  - NFRs: MUST cover ALL 8 categories (Availability, Compatibility, Extensibility, Maintainability, Scalability, Security, Usability, Performance)
  - FSD: UC-NNN use cases, API contracts (method/path/schema/errors), [Implements: PREQ-NNN] traceability
  - Embed draw.io diagrams as raw <mxGraphModel> XML in ```xml code blocks
OUTPUT FORMAT: Markdown with ## headings for each section
```

### Draw.io Diagrams in BRD/FSD (Req 10.1-10.7)

BRD prompt requests 3 draw.io diagrams:
- Process Flow → section Existing Processes > Summary Process Narrative
- Requirements Traceability → section Project Requirements > Process Overview
- Stakeholder Map → section Project Overview > Project Contributors

FSD prompt requests 4 draw.io diagrams:
- Context/Interface Diagram → section System/Solution Overview
- Data Flow Diagram → section System/Solution Overview > Data Flow
- Integration Architecture → section Integration Requirements
- Data Migration Flow → section Data Migration/Conversion Requirements

AI sinh draw.io XML trực tiếp hoặc JSON metadata trong Markdown response. `DocumentPreviewPanel` collects tất cả diagram code blocks trước, gọi `DrawioDiagramRenderer.ensureViewerLoaded()` để load draw.io viewer CDN, rồi render: (1) `<mxGraphModel>` XML code blocks → render via `window.__drawioRenderOne`, (2) JSON metadata code blocks chứa `"nodes"` + `"connections"` → parse via `DocumentPreviewDiagramHelper` → convert sang XML via `DrawioTemplateEngine.merge()` → render. Sau render, `tryRenderDrawio()` verify bằng cách check `<svg>` element sau 500ms — nếu không có SVG (viewer fail hoặc XML invalid), hiển thị fallback "📊 Draw.io diagram (viewer unavailable)" với XML preview. Cả hai format đều backward-compatible.

### SlideGenerator (Req 11.1-11.3)

Input: BRD Markdown string
Output: Slide-format Markdown with `---` separators

Pipeline:
1. Parse BRD → extract section contents (using Carleton ITS section names: Project Overview, Existing Processes, Project Requirements, Sign Off, Appendix)
2. Generate 7 slides: Vision, Requirements Overview, Data Flow, Scope, Key Stakeholders, Risk Summary, Timeline & Milestones
3. Each slide max 5-7 bullet points
4. No new content — condensed from BRD only
5. Fallback: when primary section empty/warning, tries alternative sections via `getContentWithFallback()`
6. When no data available, shows "⚠️ BRD chưa có đủ nội dung cho slide này. Vui lòng review và bổ sung BRD trước khi sinh slides."


---

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system — essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Aggregation respects capacity limits

*For any* main ticket with N linked tickets (0 ≤ N ≤ 100) and M attachment chunks returned by VectorStore (0 ≤ M ≤ 50), the `GenerationContext` produced by `DocumentAggregator` SHALL contain at most 20 linked ticket analyses AND at most 10 attachment chunks.

**Validates: Requirements 1.2, 1.3**

### Property 2: Aggregation fault tolerance for missing linked tickets

*For any* set of linked ticket IDs where K out of N tickets have KBRecords in the Knowledge Base (0 ≤ K ≤ N), `DocumentAggregator.aggregate()` SHALL succeed and the resulting `GenerationContext.linkedTicketAnalyses` SHALL have exactly min(K, 20) entries — never failing due to missing linked tickets.

**Validates: Requirements 1.5**

### Property 3: BRD prompt contains all required sections and anti-hallucination instructions

*For any* valid `GenerationContext`, the prompt string produced by `BrdPromptBuilder` SHALL contain all 7 BRD section headings (Carleton ITS template: "Revision History", "Project Overview", "Common Project Acronyms, Names, and Descriptions", "Existing Processes", "Project Requirements", "Sign Off", "Appendix") AND SHALL contain anti-hallucination instructions (keywords "do not fabricate" or equivalent) AND SHALL contain source citation format instruction ("[Source:").

**Validates: Requirements 2.1, 2.2, 9.1, 9.2**

### Property 4: FSD prompt contains all required sections and anti-hallucination instructions

*For any* valid `GenerationContext`, the prompt string produced by `FsdPromptBuilder` SHALL contain all 11 FSD section headings ("Introduction", "System/Solution Overview", "Functional Specifications", "System Configurations", "Non-Functional Requirements", "Reporting Requirements", "Integration Requirements", "Data Migration/Conversion Requirements", "References", "Open Issues", "Appendix") AND SHALL contain anti-hallucination instructions AND source citation format instruction.

**Validates: Requirements 3.1, 3.2, 9.1, 9.2**

### Property 5: BRD parser always produces exactly 7 sections

*For any* Markdown string (including empty string, partial sections, malformed Markdown, headings with numbering prefixes, different casing, H1/H2 levels, or bold formatting), `BrdResponseParser.parse()` SHALL return a result containing exactly 7 sections with the correct BRD headings (Carleton ITS template). Headings are normalized (stripping numbering, bold markers, whitespace) and matched case-insensitively with fuzzy fallback. H3 sub-headings are preserved as content within their parent section. Missing sections SHALL be filled with the default "⚠️ Insufficient data" text.

**Validates: Requirements 2.7, 2.8, 9.3**

### Property 6: FSD parser always produces exactly 11 sections

*For any* Markdown string (including empty string, partial sections, malformed Markdown, headings with numbering prefixes, different casing, H1/H2 levels, or bold formatting), `FsdResponseParser.parse()` SHALL return a result containing exactly 11 sections with the correct FSD headings. Headings are normalized and matched case-insensitively with fuzzy fallback, identical to BRD parser behavior. H3 sub-headings are preserved as content within their parent section. Missing sections SHALL be filled with the default "⚠️ Insufficient data" text.

**Validates: Requirements 3.8, 3.9, 9.3**

### Property 7: BRD Markdown round-trip preserves section structure

*For any* valid BRD Markdown string with 7 headings (H1 or H2 level, with or without numbering/bold formatting), parsing into sections then serializing back to Markdown SHALL preserve all 7 section headings in the same order. H3 sub-headings within sections are preserved as content. Note: serialization always outputs `## ` (H2) headings regardless of input heading level.

**Validates: Requirements 9.4**

### Property 8: FSD Markdown round-trip preserves section structure

*For any* valid FSD Markdown string with 11 headings (H1 or H2 level, with or without numbering/bold formatting), parsing into sections then serializing back to Markdown SHALL preserve all 11 section headings in the same order. H3 sub-headings within sections are preserved as content. Note: serialization always outputs `## ` (H2) headings regardless of input heading level.

**Validates: Requirements 9.5**

### Property 9: Document overwrite produces exactly one document per ticket-type pair

*For any* ticketId and documentType, after calling `DocumentRepository.save()` N times (N ≥ 1) with different content, `DocumentRepository.findByTicketIdAndType(ticketId, type)` SHALL return exactly 1 document with the content and timestamp from the most recent save.

**Validates: Requirements 5.3**

### Property 10: Slide generation produces valid slide structure from BRD

*For any* valid BRD Markdown string with at least 3 non-empty sections, `SlideGenerator.generate()` SHALL produce Markdown containing `---` slide separators, with each slide having at most 7 bullet points, and total slides ≤ 7.

**Validates: Requirements 11.1, 11.2, 11.3**

### Property 11: GeneratedDocument serialization round-trip

*For any* valid `GeneratedDocument` with random documentType, ticketId, markdownContent, sourceTicketIds, and attachmentSources, serializing to JSON then deserializing SHALL produce an equivalent object.

**Validates: Requirements 4.1, 4.2**

---

## Error Handling

| Tình huống | Xử lý | Req |
|-----------|-------|-----|
| Ticket chưa có deep analysis | Auto-analyze triggered via `JobExecutor.ensureTicketAnalyzed()` before pipeline execution. If `KBRepository.findByTicketId()` returns null, `AIOrchestrator.analyzeTicket()` is called automatically. Falls back to HTTP 400 only if AIOrchestrator is unavailable | 1.1, 4.4 |
| Linked ticket không có trong KB | Skip, log warning, continue generation | 1.5 |
| VectorStore search thất bại | Continue with empty attachment chunks, log warning | 1.3 |
| EmbeddingService.embed() trả null | Skip attachment search, log warning | 1.3 |
| AI generation thất bại | Retry tối đa 2 lần, sau đó HTTP 500 | 4.5 |
| AI response thiếu sections | Bổ sung sections thiếu với "⚠️ Insufficient data" | 2.8, 3.9, 9.3 |
| AI response có heading variations (numbering, casing, H1/H3, bold) | Headings normalized and matched case-insensitively via `findSectionCaseInsensitive()` | brd-insufficient-data-fix 2.1-2.4 |
| AI response empty hoặc không có headings | Log warning with response length and first 200 chars, then fallback to INSUFFICIENT_DATA for all sections | brd-insufficient-data-fix 2.6 |
| AI response chứa hallucinated content | Prompt instructions enforce source citation; parser cannot fully detect, relies on prompt | 9.1 |
| Draw.io diagram thiếu hoặc sai format | `DocumentPreviewPanel` gọi `DrawioDiagramRenderer.ensureViewerLoaded()` trước khi render. Thử XML first, rồi JSON metadata fallback. Nếu viewer load fail hoặc XML invalid (ví dụ: duplicate attributes) → `tryRenderDrawio()` detect không có `<svg>` sau 500ms → hiển thị fallback "📊 Draw.io diagram (viewer unavailable)" với XML preview | 10.6 |
| Reader role gọi generate | HTTP 403 "Insufficient permissions" | 4.6 |
| Document save thất bại | HTTP 500, log error | 5.2 |
| BRD chưa generate khi request slides | HTTP 400 "BRD must be generated first" | 11.5 |
| marked.js CDN load thất bại | Fallback: hiển thị raw Markdown text | 7.1 |
| PDF export thất bại | Fallback: offer Markdown download | 8.3 |

---

## Testing Strategy

### Dual Testing Approach

Feature này phù hợp cho property-based testing vì:
- Có pure functions với input/output rõ ràng (prompt building, response parsing, aggregation, slide generation, serialization)
- Có universal properties (round-trip, invariants, capacity limits)
- Input space lớn (random KBRecords, random Markdown content, random linked tickets)

### Property-Based Tests

- **Library**: [Kotest Property Testing](https://kotest.io/docs/proptest/property-based-testing.html) — Kotlin multiplatform PBT library
- **Minimum iterations**: 100 per property
- **Tag format**: `Feature: ai-generated-brd-fsd, Property {N}: {title}`

Mỗi correctness property (Property 1-11) sẽ được implement bằng 1 property-based test với custom generators:

| Generator | Mô tả |
|-----------|-------|
| `Arb.kbRecord()` | Random KBRecord with random businessSummary, extractedRequirements, technicalDetails, dependencies, acceptanceCriteria, diagrams |
| `Arb.generationContext()` | Random GenerationContext: 1 main ticket + 0-30 linked analyses + 0-15 attachment chunks |
| `Arb.attachmentChunkInfo()` | Random AttachmentChunkInfo: random filename, content, similarityScore |
| `Arb.brdMarkdown()` | Random valid BRD Markdown with 0-18 `## ` sections and random content per section |
| `Arb.fsdMarkdown()` | Random valid FSD Markdown with 0-10 `## ` sections and random content per section |
| `Arb.generatedDocument()` | Random GeneratedDocument with random fields |

### Unit Tests (Example-Based)

| Test | Mô tả | Req |
|------|-------|-----|
| Aggregator rejects unanalyzed ticket | Verify error when KBRecord has no deep analysis | 1.1 |
| BRD prompt includes businessSummary context | Verify prompt with specific GenerationContext | 2.3 |
| BRD prompt includes dependencies context | Verify prompt with dependency data | 2.4 |
| FSD prompt expands API specifications | Verify prompt with apiSpecifications data | 3.3 |
| FSD prompt expands database changes | Verify prompt with databaseChanges data | 3.4 |
| Parser fills empty BRD with 7 default sections | Edge case: empty string input | 2.8 |
| Parser fills empty FSD with 11 default sections | Edge case: empty string input | 3.9 |
| SlideGenerator rejects when BRD not available | Error handling | 11.5 |
| Export filename format | Verify `{ticketId}-{documentType}.md` pattern | 8.2 |
| Draw.io prompt requests correct diagram types | BRD=3 diagrams, FSD=4 diagrams | 10.1, 10.2 |

### Integration Tests (E2E)

| Test | Mô tả | Req |
|------|-------|-----|
| POST generate-brd returns valid document | Full pipeline test | 4.1 |
| POST generate-fsd returns valid document | Full pipeline test | 4.2 |
| GET document-status returns phase transitions | Progress tracking | 4.3 |
| GET documents lists saved documents | Persistence test | 5.4 |
| Reader role gets 403 on generate | RBAC test | 4.6 |
| Re-generate overwrites previous document | Idempotence | 5.3 |
| Generate BRD button visible only for ANALYZED tickets | UI state test | 6.1, 6.2 |
| Document Preview renders Markdown | UI rendering test | 7.1 |
| Export Markdown triggers download | Browser API test | 8.2 |
| Slide navigation Previous/Next | UI interaction test | 11.6 |
