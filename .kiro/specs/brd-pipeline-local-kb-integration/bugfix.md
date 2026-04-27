# Bugfix Requirements Document

## Introduction

BRD pipeline multi-phase mode (spec `multi-phase-brd-pipeline`) được thiết kế để chạy multi-phase khi KB tools khả dụng. Tuy nhiên, pipeline **luôn luôn** fallback về single-phase mode vì `PipelineOrchestrator` không thể phát hiện Local Knowledge Base tools.

**Root cause**: `AiBackendPipelineStrategy.collectToolDescriptors()` chỉ thu thập tools từ 2 nguồn:
1. `InternalMcpBridge.getAggregatedTools()` → 30 internal tools (navigate, scan, analyze, chat, settings...) — KHÔNG có KB tools
2. `McpProcessManager.getActiveTools()` → external MCP server tools (markitdown, playwright, jira, drawio) — KHÔNG có KB tools

Trong khi đó, Local Knowledge Base tồn tại dưới dạng `LocalKBToolExecutor` với 4 tools (`search_knowledge`, `get_ticket_info`, `search_relationships`, `ingest_knowledge` trên server `local-knowledge-base`), nhưng chỉ được inject vào `ChatServiceImpl` cho AI Chat. BRD pipeline không có cách nào discover hoặc sử dụng chúng.

Ngoài ra, `BAAgentModule.kt` (Koin DI module) không truyền `localKBToolExecutor` vào `BASubprocessOrchestrator` — ngay cả khi code fix đúng ở strategy và bridge, executor luôn `null` tại runtime cho đến khi DI wiring được cập nhật.

**Impact**: Requirement 1.1 và 8.1 của spec `multi-phase-brd-pipeline` bị vi phạm — multi-phase mode không bao giờ được kích hoạt, dẫn đến BRD quality thấp do "lost in the middle" problem với prompt ~84K ký tự.

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN Local Knowledge Base is enabled (`local_kb_tool_enabled` != "false") AND `PipelineOrchestrator.executePipeline()` is called THEN the system always runs single-phase mode because `PhaseToolFilter.hasKbTools()` returns `false` — Local KB tools (`search_knowledge`, `get_ticket_info`, `search_relationships`) are not present in the tool list passed to the orchestrator

1.2 WHEN `AiBackendPipelineStrategy.collectToolDescriptors()` collects tools from `InternalMcpBridge` and `McpProcessManager` THEN the system returns a tool list that does NOT include any Local KB tools — the 4 Local KB tools are only injected into `ChatServiceImpl`, not into the pipeline's tool collection

1.3 WHEN multi-phase mode is triggered (hypothetically) AND the AI agent calls a Local KB tool (e.g., `mcp_local-knowledge-base_search_knowledge`) THEN `ToolExecutionBridge.executeMcpTool()` fails with "MCP tool not found" because it only routes to `InternalMcpBridge` or `McpProcessManager` — it has no knowledge of `LocalKBToolExecutor`

1.4 WHEN the server logs pipeline execution THEN the log shows `"Pipeline mode: single-phase, tools=57"` — even with 57 tools available, none match KB patterns (`kb_search`, `kb_ingest`, `kb_write`), confirming Local KB tools are absent from the tool list

### Expected Behavior (Correct)

2.1 WHEN Local Knowledge Base is enabled (`local_kb_tool_enabled` != "false") AND `PipelineOrchestrator.executePipeline()` is called THEN the system SHALL include Local KB tools in the tool list, causing `PhaseToolFilter.hasKbTools()` to return `true` and triggering multi-phase mode

2.2 WHEN `AiBackendPipelineStrategy.collectToolDescriptors()` collects tools THEN the system SHALL also include Local KB tool descriptors (with names matching KB patterns like `kb_search`, `kb_write`, `kb_ingest` or equivalent Local KB tool names) alongside internal and external MCP tools, so that `hasKbTools()` can detect them

2.3 WHEN multi-phase mode is active AND the AI agent calls a Local KB tool THEN `ToolExecutionBridge` SHALL route the call to `LocalKBToolExecutor.execute()` and return the result — the same routing logic used by `McpAgenticLoop` in Chat SHALL be available to the BRD pipeline

2.4 WHEN Local Knowledge Base is disabled (`local_kb_tool_enabled` == "false") THEN the system SHALL NOT include Local KB tools in the tool list, and the pipeline SHALL continue to run in single-phase mode as before

### Unchanged Behavior (Regression Prevention)

3.1 WHEN Local Knowledge Base is disabled (`local_kb_tool_enabled` == "false") THEN the system SHALL CONTINUE TO run single-phase mode with the same behavior as the current implementation — no change to fallback logic

3.2 WHEN external MCP KB tools (e.g., from Obsidian KB server with names containing `kb_search`, `kb_ingest`, `kb_write`) are available THEN the system SHALL CONTINUE TO detect them via `PhaseToolFilter.hasKbTools()` and trigger multi-phase mode — existing external KB detection logic is unchanged

3.3 WHEN `ToolExecutionBridge` receives a tool call for an internal MCP tool (from `InternalMcpBridge`) THEN the system SHALL CONTINUE TO route it through `InternalMcpBridge.callTool()` as before — internal tool routing is unchanged

3.4 WHEN `ToolExecutionBridge` receives a tool call for an external MCP tool (from `McpProcessManager`) THEN the system SHALL CONTINUE TO route it through `McpProcessManager.getClient().callTool()` as before — external tool routing is unchanged

3.5 WHEN `ChatServiceImpl` uses `LocalKBToolExecutor` for AI Chat THEN the system SHALL CONTINUE TO work identically — Chat's Local KB integration is not affected by this fix

3.6 WHEN `AgenticLoopRunner`, `AgenticPromptBuilder`, `PhaseToolFilter`, `PhasePromptBuilder`, and `BrdAssembler` are used THEN the system SHALL CONTINUE TO behave identically — these components are NOT modified by this fix
