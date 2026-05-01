# AI Agent Framework — Master Requirements

## Tổng quan

Domain AI Agent Framework cung cấp nền tảng generic cho việc xây dựng các AI agents chuyên biệt (BA Agent, Architect Agent, QA Agent, PM Agent). Framework theo công thức Agent = LLM + Planning + Memory + Tools, sử dụng kiến trúc Agent-as-Subprocess: Orchestrator quản lý long-lived CLI subprocess (Gemini CLI, Copilot CLI, Kiro CLI, Ollama), giao tiếp qua stdin/stdout streaming với JSON protocol. AI subprocess tự quyết định data collection qua tool calls, Orchestrator proxy tool calls qua ToolRegistry (MCP tools từ Integrations page).

Hệ thống đã trải qua nhiều giai đoạn: từ agent-based document generation (JiraContextMemory, thinking loop), sang subprocess orchestration (tool call loop engine), CLI interactive mode, multi-turn orchestration, POC replacement (AiBackend hierarchy), và cuối cùng xóa native tools để chỉ dùng MCP tools động. MCP Tool Bridge đang được phát triển để route agent tool calls qua MCP servers.

## Specs gốc

| Spec | Loại | Trạng thái | Mô tả |
|------|------|------------|-------|
| `generic-agent-framework` | Feature | ✅ Archived | Core framework: interfaces, memory, tools, subprocess, ReAct loop |
| `agent-document-generation` | Feature | ✅ Archived | BA Document Agent: JiraContextMemory, thinking loop, Master Prompt |
| `agent-subprocess-orchestration` | Feature | ✅ Archived | Subprocess management, stdin/stdout, tool call loop |
| `agent-mcp-tool-bridge` | Feature | ~55% Active | MCP tool bridge — route agent tool calls qua MCP servers |
| `cli-interactive-ba-agent` | Feature | ✅ Archived | CLI interactive mode, JSON tool call protocol |
| `copilot-kiro-cli-integration` | Feature | ✅ Archived | Copilot CLI + Kiro CLI provider integration |
| `multi-turn-ba-orchestration` | Feature | ✅ Archived | Multi-turn orchestration thay single-shot prompt |
| `poc-agent-replacement` | Feature | ✅ Archived | AiBackend hierarchy, stateless/persistent modes |
| `native-tool-removal` | Feature | ✅ Archived | Xóa 6 native BA tools, chỉ dùng MCP tools |
| `cli-protocol-bypass` | Bugfix | ✅ Archived | Fix JSON framing cho real CLI tools (plain text stdin) |
| `stdout-blocking-timeout-fix` | Bugfix | ✅ Archived | Fix BufferedReader.readLine() blocking — idle timeout detection |
| `ba-agent-mcp-tools-fix` | Bugfix | ✅ Archived | Fix BA agent không register trong AgentRegistry |

## Requirements tổng hợp

### Generic Agent Framework

- `GenericAgent` interface: `execute(input) → output`, lifecycle hooks, state access
- Agent Home Directory: skills (markdown), workflows, rules, memory, MCP configs
- Structured Memory: named semantic slots, completeness tracking, JSON serialization
- Tool Registry: registration, discovery, validation, invocation, rate limiting
- Thinking Loop Engine: ReAct (Reason + Act + Observe), phase definitions, exit conditions
- Parallel Tool Executor: coroutine-based concurrent tool calls
- Agent State: serializable snapshot (phase, memory, tool history, timing)
- Progress Reporter: integration với frontend progress UI
- Error Strategy: RETRY, SKIP, ABORT, FALLBACK

### Subprocess Management

- SubprocessManager: singleton per agent type, spawn/reuse/crash detection
- stdin/stdout streaming, Command_Mutex (1 command at a time)
- MessageProtocol: JSON framing + `---END---` delimiter
- Real CLI bypass: plain text stdin cho Gemini/Copilot/Kiro CLI
- Idle timeout detection: break readLine() loop khi no output

### BA Subprocess Orchestrator

- `executeTask(BATaskConfig): BATaskResult` — start subprocess, send task, run tool call loop
- TaskMessageBuilder: role instructions, document template, available tools (dynamic từ MCP)
- Tool Call Loop: read stdout → detect ToolCallRequest → proxy qua ToolRegistry → send response
- Progress milestones: subprocess started (5%), task sent (10%), tool loop (15-80%), response (85%)
- CLI config resolution từ Integrations page (ProviderConfigRepository)

### Agent-Based Document Generation

- JiraContextMemory: typed slots (summary, comments, attachments, linked tickets, KB records)
- Collection Strategy per document type: BRD (business goals), FSD (technical), Slides (executive)
- Thinking Loop: Collect → Expand → Visualize → Synthesize
- Master Prompt: focused, document-type-specific (thay vì 200K char dump)
- Sufficiency Check: đánh giá memory đủ chưa để sinh document

### CLI Interactive Mode

- CLI_Interactive_Engine: spawn process, interactive stdin/stdout
- Tool Call Protocol: `{"toolCall":{"name":"...","arguments":{...}}}` / `{"toolResult":{"name":"...","success":true,"data":"..."}}`
- End delimiter: `---END---` signal completion

### Multi-Turn Orchestration

- Orchestrator thu thập data từ MCP tools TRƯỚC khi gọi AI
- Multi-turn: Step 1 "phân tích ticket data", Step 2 "mở rộng requirements", Step 3 "viết BRD"
- AI chỉ phân tích và viết, không gọi tool trực tiếp

### POC Replacement (AiBackend Hierarchy)

- AiBackend: transport-agnostic interface (CLI + API), `sendPrompt()` và `sendMessage()` là `suspend fun` — cho phép coroutine cancellation propagate tự nhiên qua tất cả backends
- AiCliClient: process mode STATELESS/PERSISTENT, CLI type
- AiApiClient: baseUrl, model (Ollama native tool calling)
- BaseNodeCliClient: Node.js CLI process spawning, path resolution
- CliBackendResolver: map backend names → SubprocessConfig

### Copilot & Kiro CLI Integration

- CopilotCliAgent: spawn `gh copilot`, stdin/stdout, timeout 240s
- KiroCliAgent: spawn Kiro CLI, stdin/stdout, timeout 240s
- Pattern giống GeminiCliAgent: implement AIAgent interface
- Provider cards trên Integrations page, config trong provider_configs

### Native Tool Removal

- Xóa 6 native BA tools: FetchJiraDetails, GetLinkedIssues, FetchComments, LookupKBRecord, SearchKB, ProcessAttachment
- ToolRegistry chỉ chứa MCP tools (dynamic từ Integrations page)
- SubprocessProxy.getAvailableToolDescriptors() = single source of truth
- TaskMessageBuilder: tool instructions từ dynamically available tools

### MCP Tool Bridge (Active)

- Route agent tool calls qua MCP servers
- Name mapping, collision detection, type conversion
- parameterNames, listToolsWithSource

## Resolved Issues

| Bugfix Spec | Tóm tắt |
|-------------|---------|
| `cli-protocol-bypass` | Fix JSON framing cho real CLI tools — plain text stdin thay vì JSON-wrapped, inject response protocol instructions |
| `stdout-blocking-timeout-fix` | Fix BufferedReader.readLine() blocking indefinitely — idle timeout detection, partial result return |
| `ba-agent-mcp-tools-fix` | Fix BA agent không register trong AgentRegistry — eager initialization thay vì lazy Koin single |
