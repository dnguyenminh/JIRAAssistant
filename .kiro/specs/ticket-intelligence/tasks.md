# Ticket Intelligence — Tasks

Status: ✅ All completed

## From tasks-03-frontend-kotlinjs.md

### Task 32.5: TicketIntelligencePage.kt
- [x] 32.5 `TicketIntelligencePage.kt` — _Requirements: AC 5.1–5.4, AC 5.7, [cross-cutting] AC 13.2, [cross-cutting] AC 13.3_

### Task 12a.6: UI E2E Tests — Ticket Intelligence
- [x] 12a.6 Tạo `007-TicketIntelligence.feature` + `TicketIntelligenceSteps.kt` + `UiTicketIntelligenceRunner.kt`
    - _Requirements: AC 5.1–5.7_

---

## From tasks-07-batch-scan-engine.md

### Task 64: Backend — Ticket Analysis Status Endpoint
- [x] 64.1 Tạo `TicketAnalysisStatus.kt` và `TicketAnalysisState` enum
    - _Requirements: AC 5.11_
- [x] 64.2 Thêm endpoint `GET /api/projects/{key}/tickets/status`
    - _Requirements: AC 5.1, AC 5.11–5.14_

### Task 69: Frontend — Ticket Intelligence Combobox & Dynamic Actions
- [x] 69.1 Cập nhật `ticket-intelligence.html` — combobox, status badge, dynamic action button
    - _Requirements: AC 5.1, AC 5.11, [design-system-ux] AC 17.1_
- [x] 69.2 Cập nhật `components.css` — combobox styles
    - _Requirements: [design-system-ux] AC 17.1, [design-system-ux] AC 17.3_
- [x] 69.3 Cập nhật `TicketIntelligencePage.kt` — loadTicketList, renderCombobox, filterTickets, selectTicket, RBAC
    - _Requirements: AC 5.1, AC 5.11–5.15, AC 5.7_

### Task 71: E2E Tests — Scan API Endpoints (Ticket Status)
- [x] 71.2 `TicketStatusApiTest.kt` — ticket analysis status
    - _Requirements: AC 5.1, AC 5.11_

### Task 71a: UI E2E Tests — Batch Scan Engine (Combobox scenarios)
- [x] 71a.1 Tạo `014-BatchScan.feature` (includes Ticket Intelligence combobox scenarios):
    - Ticket Intelligence combobox: searchable dropdown, status badges, dynamic action button
    - _Requirements: AC 5.11–5.15_
- [x] 71a.2 Tạo `BatchScanSteps.kt` — Step definitions (includes combobox steps)
    - CSS selectors: `.ticket-combobox`, `.status-badge`
    - _Requirements: AC 5.11–5.15_
- [x] 71a.3 Tạo `UiBatchScanRunner.kt` — Serenity Cucumber runner

---

# Deep Analysis — Implementation Tasks

## Overview

Nâng cấp pipeline phân tích AI chung (`AIOrchestrator.analyzeTicket()`) với Deep Analysis. Tuân thủ max 200 dòng/file, max 20 dòng/function, models tách package riêng.

## Tasks

- [x] 80. Tạo Deep Analysis data models
  - [x] 80.1 Tạo package `shared/.../deepanalysis/models/` và các data class core: TechnicalDetails, AcceptanceCriterion, DependencyInfo, AnalysisMetadata
    - _Requirements: 19.1, 19.2, 19.3, 19.4, 19.5_
  - [x] 80.2 Tạo StructuredTicketContent, ClassifiedContent, và model phụ trợ (SubTaskInfo, IssueLinkInfo, AttachmentInfo, CommentInfo, ChangelogEntry)
    - _Requirements: 16.8, 17.1-17.6_
  - [x] 80.3 Tạo CascadeModels (CascadeLogEntry, CascadeLogStatus, CascadeResult, CascadeStatus)
    - _Requirements: 26.8, 26.9_
  - [x] 80.4 Mở rộng AnalysisResult và RequirementSummary (backward compatible)
    - _Requirements: 19.1, 19.6_
  - [x] 80.5 Mở rộng KBRecord (backward compatible)
    - _Requirements: 20.1, 20.4_

- [x] 81. Implement Jira Content Extractor + Section Classifier
  - [x] 81.1 Tạo SectionClassifier interface và implementation
    - _Requirements: 17.1-17.6_
  - [x] 81.2 Tạo JiraContentExtractor interface và implementation
    - _Requirements: 16.1-16.8_
  - [x] 81.3 Viết unit tests cho SectionClassifier
  - [x] 81.4 Viết unit tests cho JiraContentExtractor

- [x] 82. Implement Deep Analysis Prompt Builder
  - [x] 82.1 Tạo DeepAnalysisPromptBuilder interface và implementation
    - _Requirements: 18.1-18.6_
  - [x] 82.2 Viết unit tests cho DeepAnalysisPromptBuilder

- [x] 83. Implement Deep Analysis Response Parser
  - [x] 83.1 Tạo DeepAnalysisResponseParser interface và implementation
    - _Requirements: 25.1-25.6_
  - [x] 83.2 Viết unit tests cho DeepAnalysisResponseParser

- [x] 84. Checkpoint — Ensure all tests pass

- [x] 85. Nâng cấp AIOrchestrator pipeline
  - [x] 85.1 Nâng cấp AIOrchestratorImpl.analyzeTicket() sử dụng Deep Analysis components
    - _Requirements: 21.1, 21.2, 21.6_
  - [x] 85.2 Nâng cấp KB save/load với expanded KBRecord
    - _Requirements: 20.1-20.4_
  - [x] 85.3 Cập nhật BatchScanTicketProcessor.fetchTicketContent() sử dụng JiraContentExtractor
    - _Requirements: 21.2, 21.3_
  - [x] 85.4 Cập nhật AnalysisStatusTracker với 4 giai đoạn mới
    - _Requirements: 21.4_
  - [x] 85.5 Viết unit tests cho upgraded AIOrchestrator

- [x] 86. Implement Cascading Analysis Engine
  - [x] 86.1 Tạo CascadingAnalysisEngine interface và implementation (BFS, recursive, safety limit)
    - _Requirements: 26.1-26.7_
  - [x] 86.2 Tạo CascadeRoutes trong server
    - _Requirements: 26.8, 26.10_
  - [x] 86.3 Viết unit tests cho CascadingAnalysisEngine

- [x] 87. Checkpoint — Ensure all tests pass

- [x] 88. Mở rộng Frontend models và state management
  - [x] 88.1 Mở rộng TicketIntelligenceModels.kt với deep analysis fields
    - _Requirements: 19.1-19.6_
  - [x] 88.2 Tạo TicketStateManager cho state persistence
    - _Requirements: 23.1, 23.2_

- [x] 89. Mở rộng Frontend tabs hiển thị Deep Analysis
  - [x] 89.1 Tạo ContextTabRenderer — tab Context mở rộng
    - _Requirements: 22.1_
  - [x] 89.2 Tạo EvolutionTabRenderer — tab Evolution mở rộng
    - _Requirements: 22.2_
  - [x] 89.3 Tạo ComplexityTabRenderer — tab Complexity mở rộng
    - _Requirements: 22.3_
  - [x] 89.4 Tạo ConfidenceBadge component
    - _Requirements: 22.4, 22.5_

- [x] 90. Tích hợp state persistence và auto-load vào TicketIntelligencePage
  - [x] 90.1 Cập nhật TicketIntelligencePage.kt tích hợp state persistence
    - _Requirements: 23.1, 23.2_
  - [x] 90.2 Implement auto-load kết quả đã phân tích
    - _Requirements: 23.3-23.6_
  - [x] 90.3 Cập nhật TicketResultTabs.kt delegate render sang tab renderers mới
    - _Requirements: 22.1-22.3_

- [x] 91. Implement Cascade Log UI trên Frontend
  - [x] 91.1 Tạo CascadeLogPanel component
    - _Requirements: 26.8-26.10_
  - [x] 91.2 Tích hợp CascadeLogPanel vào TicketIntelligencePage
    - _Requirements: 26.11_

- [x] 92. Nâng cấp AI Chat Sidebar integration
  - [x] 92.1 Mở rộng ChatServiceImpl.buildKBContext() với deep analysis data
    - _Requirements: 24.1-24.4_

- [x] 93. Cập nhật CSS styles cho Deep Analysis UI
  - [x] 93.1 Styles cho expanded tabs, ConfidenceBadge, CascadeLogPanel
    - _Requirements: 22.1-22.5, 26.8, 26.10_

- [x] 94. Final checkpoint — Ensure all tests pass

---

## Linked Tickets Context (Req 27) & Mermaid Diagrams (Req 28)

- [x] 95. Implement linked ticket content fetching
  - [x] 95.1 Thêm `LinkedTicketContent` model vào `TicketContentModels.kt`
    - _Requirements: 27.2_
  - [x] 95.2 Thêm `linkedTicketContents` field vào `StructuredTicketContent`
    - _Requirements: 27.2_
  - [x] 95.3 Cập nhật `JiraContentExtractorImpl.extract()` — fetch content chi tiết linked tickets (max 5, ưu tiên blocking > linked > sub-tasks, 500 chars description)
    - _Requirements: 27.1, 27.3, 27.5_

- [x] 96. Inject linked tickets context vào AI prompt
  - [x] 96.1 Cập nhật `PromptSectionTicketData` — thêm section "RELATED TICKETS CONTEXT"
    - _Requirements: 27.3, 27.4_

- [x] 97. Implement Mermaid diagram generation
  - [x] 97.1 Tạo `DiagramData` model
    - _Requirements: 28.2_
  - [x] 97.2 Thêm `diagrams` field vào `DeepAnalysisResult`
    - _Requirements: 28.2_
  - [x] 97.3 Cập nhật `PromptSectionTicketData` — thêm section "DIAGRAM GENERATION" yêu cầu AI sinh Mermaid code
    - _Requirements: 28.1, 28.6_
  - [x] 97.4 Cập nhật `PromptJsonSchema` — thêm `diagrams` array vào JSON output schema
    - _Requirements: 28.1_
  - [x] 97.5 Cập nhật `ResponseJsonModels` và `ResponseToResultMapper` — parse diagrams từ AI response
    - _Requirements: 28.2_

- [x] 98. Frontend Mermaid diagram rendering
  - [x] 98.1 Tạo `DiagramRenderer.kt` — load Mermaid.js CDN, render diagrams trong tab CONTEXT
    - _Requirements: 28.3, 28.4, 28.5_
  - [x] 98.2 Tích hợp `DiagramRenderer` vào `ContextTabRenderer`
    - _Requirements: 28.3_

- [x] 99. Final checkpoint — Ensure all tests pass

- [x] 100. Context tab enrichment — Dependencies, Acceptance Criteria, Analysis Info
  - [x] 100.1 Tạo `ContextTabEnrichment.kt` — 3 enrichment sections cho Context tab
    - `renderDependenciesOverview()` — blocking issues + risk badges + related count
    - `renderAcceptanceCriteriaPreview()` — top 3 criteria + "(+N more in Complexity tab)" link
    - `renderAnalysisInfo()` — analyzedAt, aiProviderUsed, extractionConfidence badge
    - _Requirements: 22.1_
  - [x] 100.2 Tích hợp `ContextTabEnrichment` vào `ContextTabRenderer.render()`
    - Gọi 3 enrichment methods sau DiagramRenderer.render()
    - _Requirements: 22.1_

- [x] 101. Cross-project ticket support trong Ticket Intelligence combobox
  - [x] 101.1 Thêm `isValidTicketId()`, `getTypedTicketId()`, `acceptCrossProjectTicket()` vào `TicketCombobox.kt`
    - Validate regex `^[A-Z][A-Z0-9]+-\d+$`, accept cross-project ticket IDs
    - _Requirements: 1, 15.1, 15.2_
  - [x] 101.2 Cập nhật `TicketIntelligencePage.kt` — fallback chain cho ANALYZE button + navigation context support
    - `selectedTicket ?: tryAcceptTypedTicketId() ?: return`
    - _Requirements: 1, 15.1_
