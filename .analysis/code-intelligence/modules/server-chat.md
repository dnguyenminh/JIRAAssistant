# Module Analysis — server-chat

**Last Updated:** 2026-04-30T10:44:26.984Z
**Language:** javascript | **Framework:** —

## Package Structure

```
server-chat/
├── com.assistant.server.chat/     # Application logic
├── com.assistant.server.chat.models/     # Domain model
├── com.assistant.server.di/     # Application logic
└── com.assistant.server.routes/     # Application logic
```

## Key Classes

| Class | Package | Responsibility | Visibility |
|-------|---------|---------------|------------|
| ChatDeepAnalysisContext | com.assistant.server.chat | Application component | public |
| ChatGraphStateContext | com.assistant.server.chat | Application component | internal |
| ChatLocalKBContext | com.assistant.server.chat | Application component | internal |
| ChatMcpToolsContext | com.assistant.server.chat | Application component | internal |
| ChatPersonalization | com.assistant.server.chat | Application component | internal |
| ChatPromptBuilder | com.assistant.server.chat | Application component | internal |
| ChatResponseParser | com.assistant.server.chat | Application component | internal |
| ChatServiceImpl | com.assistant.server.chat | Business logic | public |
| ChatTicketStateContext | com.assistant.server.chat | Application component | internal |
| ConfluenceMcpSyncHandler | com.assistant.server.chat | HTTP request handling | public |
| ConfluencePage | com.assistant.server.chat | Application component | data |
| JiraMcpSyncHandler | com.assistant.server.chat | HTTP request handling | public |
| LocalKBToolExecutor | com.assistant.server.chat | Application component | public |
| McpAgenticLoop | com.assistant.server.chat | Application component | public |
| McpLoopSyncHelpers | com.assistant.server.chat | Application component | internal |
| McpToolCallFallback | com.assistant.server.chat | Application component | internal |
| McpToolCallParser | com.assistant.server.chat | Application component | internal |
| starting | com.assistant.server.chat | Application component | public |
| AIModelContext | com.assistant.server.chat.models | Application component | data |
| SyncResult | com.assistant.server.chat | Application component | data |
| SyncType | com.assistant.server.chat | Application component | public |
| NativeToolCallHandler | com.assistant.server.chat | HTTP request handling | public |
| OllamaToolConverter | com.assistant.server.chat | Data mapping | public |
| ResponseFormatDetector | com.assistant.server.chat | Application component | internal |
| DetectedReply | com.assistant.server.chat | Application component | data |
| ToolCallFormatDetector | com.assistant.server.chat | Application component | internal |
| Formats | com.assistant.server.chat | Application component | public |
| UserToolPermissionService | com.assistant.server.chat | Business logic | public |
| ModelInfo | com.assistant.server.chat.models | Application component | data |
| ToolInfo | com.assistant.server.chat | Application component | data |
| ConversationRenameRequest | com.assistant.server.chat | Data transfer object | data |
| UploadResult | com.assistant.server.chat | Application component | data |
| AgenticLoopPermissionPropertyTest | com.assistant.server.chat | Test class | public |
| StubMcpProcessManager | com.assistant.server.chat | Application component | private |
| AttachmentContextPropertyTest | com.assistant.server.chat | Test class | public |
| AutoDetectPropertyTest | com.assistant.server.chat | Test class | public |
| InMemorySettingsRepo | com.assistant.server.chat | Application component | private |
| BuildGraphStateContextPropertyTest | com.assistant.server.chat | Test class | public |
| BuildGraphStateContextTest | com.assistant.server.chat | Test class | public |
| BulkUpdatePropertyTest | com.assistant.server.chat | Test class | public |
| ChatDeepAnalysisContextTest | com.assistant.server.chat | Test class | public |
| ChatEmptyReplyPropertyTest | com.assistant.server.chat | Test class | internal |
| ChatResponseParserEmptyReplyTest | com.assistant.server.chat | Test class | internal |
| ChatResponseParserRegressionTest | com.assistant.server.chat | Test class | public |
| ChatServiceAttachmentIntegrationTest | com.assistant.server.chat | Test class | public |
| ChatServiceImplTest | com.assistant.server.chat | Test class | public |
| FakeAIAgent | com.assistant.server.chat | Application component | private |
| FakeKBRepository | com.assistant.server.chat | Data access | private |
| FakeGraphEngine | com.assistant.server.chat | Application component | private |
| ChatServiceLocalKBPropertyTest | com.assistant.server.chat | Test class | public |
| FakeSettings | com.assistant.server.chat | Application component | private |
| EmptyMcpManager | com.assistant.server.chat | Application component | private |
| CapturingAIAgent | com.assistant.server.chat | Application component | public |
| FakeEmbeddingService | com.assistant.server.chat | Business logic | public |
| FakeVectorStore | com.assistant.server.chat | Application component | public |
| StubKBRepository | com.assistant.server.chat | Data access | public |
| StubGraphEngine | com.assistant.server.chat | Application component | public |
| ChatTicketStateContextTest | com.assistant.server.chat | Test class | public |
| ConfluenceMcpSyncHandlerTest | com.assistant.server.chat | Test class | public |
| DefaultAllEnabledPropertyTest | com.assistant.server.chat | Test class | public |
| JiraMcpSyncHandlerTest | com.assistant.server.chat | Test class | public |
| InMemoryKBRepository | com.assistant.server.chat | Data access | private |
| KnowledgeContextPropertyTest | com.assistant.server.chat | Test class | public |
| LocalKBToolExecutorTest | com.assistant.server.chat | Test class | public |
| MapKBRepository | com.assistant.server.chat | Data access | private |
| McpAgenticLoopEmptyReplyTest | com.assistant.server.chat | Test class | internal |
| StubMcpManager | com.assistant.server.chat | Application component | private |
| McpAgenticLoopInternalToolBugTest | com.assistant.server.chat | Test class | public |
| NullClientMcpManager | com.assistant.server.chat | Application component | private |
| StubBridge | com.assistant.server.chat | Application component | private |
| McpAgenticLoopLocalKBTest | com.assistant.server.chat | Test class | public |
| TrackingMcpManager | com.assistant.server.chat | Application component | private |
| McpAgenticLoopPreservationTest | com.assistant.server.chat | Test class | public |
| McpAgenticLoopTest | com.assistant.server.chat | Test class | public |
| PermissionRoundTripPropertyTest | com.assistant.server.chat | Test class | public |
| InMemoryPermissionRepo | com.assistant.server.chat | Application component | public |
| InMemoryMcpServerRepo | com.assistant.server.chat | Application component | public |
| PerUserIsolationPropertyTest | com.assistant.server.chat | Test class | public |
| TrackingClientManager | com.assistant.server.chat | Application component | public |
| StubProtocolClient | com.assistant.server.chat | External service client | public |
| PreservationStubEmbedding | com.assistant.server.chat | Application component | public |
| PreservationStubVectorStore | com.assistant.server.chat | Application component | public |
| PreservationStubKBRepo | com.assistant.server.chat | Application component | public |
| ResponseFormatDetectorPropertyTest | com.assistant.server.chat | Test class | public |
| and | com.assistant.server.chat | Application component | public |
| InMemorySettingsRepo | com.assistant.server.chat | Application component | private |
| ResponseFormatDetectorTest | com.assistant.server.chat | Test class | public |
| FakeSettingsRepo | com.assistant.server.chat | Application component | private |
| SystemPromptFilterPropertyTest | com.assistant.server.chat | Test class | public |
| ToolListManager | com.assistant.server.chat | Application component | private |
| ToolCallFormatDetectorTest | com.assistant.server.chat | Test class | public |
| FakeSettingsRepo | com.assistant.server.chat | Application component | private |
| ValidationPropertyTest | com.assistant.server.chat | Test class | public |
| ValidationRejectsInvalidPropertyTest | com.assistant.server.chat | Test class | public |
| ChatConfigRoutesToolsTest | com.assistant.server.chat | Test class | public |
| FakeSettingsRepository | com.assistant.server.chat | Data access | private |

## Public API Surface

- `hasDeepAnalysis(record: KBRecord): Boolean`
- `buildContext(record: KBRecord): String`
- `buildSuggestAnalyzeHint(ticketId: String): String`
- `build(gc: GraphChatContext?): String`
- `isEnabled(settingsRepository: SettingsRepository?): Boolean`
- `buildToolsContext(enabled: Boolean): List`
- `buildPriorityHint(enabled: Boolean): String`
- `buildBasePrompt(ctx: ChatContext): String`
- `parse(raw: String, usage: Int): ChatResponse`
- `build(tc: TicketChatContext?): String`
- `formatConfluenceText(page: ConfluencePage): String`
- `isConfluenceTool(toolName: String): Boolean`
- `isJiraTool(toolName: String): Boolean`
- `detectSyncType(toolName: String): SyncType`
- `parseJsonToolName(response: String): McpToolCallRequest`
- `parseTextPattern(response: String): McpToolCallRequest`
- `parseNaturalLanguage(response: String): McpToolCallRequest`
- `mapToolName(raw: String): String`
- `mapArgName(raw: String): String`
- `extractJsonObject(text: String, start: Int): String`
- `extractByPath(obj: JsonObject, path: String): String`
- `validate(permissions: Map<String, String>: Any): Result`
- `setUp(): Unit`
- `setup(): Unit`
- `setup(): Unit`
- `setup(): Unit`
- `setup(): Unit`
- `setUp(): Unit`
- `setup(): Unit`
- `setUp(): Unit`
- `setUp(): Unit`
- `clear(): Unit`
- `setUp(): Unit`
- `setUp(): Unit`
- `setUp(): Unit`
- `setUp(): Unit`

## Dependencies

| Imports From | Classes Used |
|-------------|-------------|
| server | — |

## Detected Patterns

- **DI Style**: none
- **Error Handling**: Result type
- **Naming**: *Service, *Repository
- **Logging**: SLF4J
- **Testing**: JUnit

## Annotations

| Target | Author Agent | Type | Content | Timestamp |
|--------|-------------|------|---------|-----------|
