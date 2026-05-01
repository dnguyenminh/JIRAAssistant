# Session Notes — Real MCP Integration Test

## Vấn đề còn lại (tiếp tục ở hộp thoại mới)

**Test 1 không có BRD output** — `SubprocessManagerImpl` spawn Gemini CLI nhưng AI không trả response vì:
- Prompt wrapped trong `MessageProtocol.formatCommand()` JSON framing: `{"type":"command","content":"..."}`
- Gemini CLI không hiểu JSON framing — nó expect plain text stdin
- AI không biết phải output `---END---` khi xong
- Cần thêm protocol instructions vào prompt hoặc sửa `MessageProtocol` cho Gemini CLI

**Approach gợi ý**: Thêm vào prompt (trước ROOT TICKET section):
```
## RESPONSE PROTOCOL
- When you want to call a tool, output ONLY the JSON on a single line
- When you finish the document, output ---END--- on a separate line
- Each line of your response is streamed — write naturally
```

## Files đã thay đổi

### Production
- `AgentMcpManager.kt` — real MCP client (start process, initialize, listTools, callTool)
- `AgentMcpManagerHelpers.kt` (new) — McpProcessStarter (retry 2x), McpToolDiscovery, McpToolNameResolver
- `BASubprocessOrchestrator.kt` — logging (prompt, tools, document)
- `ToolCallLoopEngine.kt` — logging (tool call request/response)

### Test
- `BADocumentAgentIntegrationTest.kt` — test 1 uses real SubprocessManagerImpl
- `BADocumentAgentIntegrationTestDoubles.kt` — PropertiesHomeDirectory, reads MCP from test.properties
- `BADocumentAgentMcpExplorationTest.kt` — KB tools + Jira tools tests
- `BADocumentAgentPreservationTest.kt` — preservation tests
- `BADocumentAgentTestConfig.kt` (new) — shared test config helpers
- `test.properties` — MCP configs, CLI paths, test ticket ID
- `test.properties.example` — template
- `logback-test.xml` (new) — DEBUG logging to file

## Key Lessons
1. `mcp-atlassian` (not `atlassian-mcp-server`) for API token auth
2. uvx first-run downloads ~30s → retry mechanism essential
3. Smart prefix: `jira_get_issue` → `mcp_jira_get_issue` (not `mcp_jira_jira_get_issue`)
4. stripPrefix must strip only `mcp_` to match buildToolName logic
5. Builtin servers use `toolDescriptions`, external use `listTools()`
6. Windows needs `.cmd` extension for CLI paths
7. MessageProtocol JSON framing incompatible with plain Gemini CLI — **remaining blocker**
