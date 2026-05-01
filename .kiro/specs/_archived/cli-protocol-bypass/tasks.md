# CLI Protocol Bypass — Tasks

## Tasks

- [x] 1. Add `isRealCli` flag to `SubprocessConfig`
  - [x] 1.1 Add `isRealCli: Boolean = false` property to `SubprocessConfig` data class in `shared/src/commonMain/kotlin/com/assistant/agent/subprocess/SubprocessConfig.kt`
  - [x] 1.2 Verify default value `false` ensures all existing callers are unaffected

- [x] 2. Conditional bypass in `writeCommandToStdin()`
  - [x] 2.1 Modify `writeCommandToStdin()` in `SubprocessManagerHelpers.kt` to accept the `SubprocessConfig` (or `isRealCli` flag) for the managed subprocess
  - [x] 2.2 When `isRealCli = true`, write plain text (`command + "\n"`) directly to stdin — no `MessageProtocol.formatCommand()` wrapping
  - [x] 2.3 When `isRealCli = false`, continue using `MessageProtocol.formatCommand(command)` as before
  - [x] 2.4 Update `SubprocessManagerImpl.sendCommand()` to pass the config/flag to `writeCommandToStdin()`

- [x] 3. Add response protocol instructions to `TaskMessageBuilder`
  - [x] 3.1 Add `RESPONSE_PROTOCOL_SECTION` constant to `TaskMessageBuilder.kt` containing instructions for `---END---` delimiter and `{"toolCall":{...}}` JSON format
  - [x] 3.2 Add `isRealCli: Boolean = false` parameter to `buildTaskMessage()` and `buildMessageContent()`
  - [x] 3.3 When `isRealCli = true`, include `RESPONSE_PROTOCOL_SECTION` in `buildMessageContent()` after tool usage instructions
  - [x] 3.4 When `isRealCli = true`, return plain text content from `buildTaskMessage()` (no `MessageProtocol.formatCommand()` wrapping)
  - [x] 3.5 When `isRealCli = false`, behave exactly as before (no protocol section, JSON-framed output)

- [x] 4. Set `isRealCli = true` in `CliBackendResolver`
  - [x] 4.1 Update `buildConfig()` in `CliBackendResolver.kt` to set `isRealCli = true` for all real CLI backends (gemini, copilot, kiro)
  - [x] 4.2 Verify ollama backend also gets `isRealCli = true` if it expects plain text stdin

- [x] 5. Pass `isRealCli` flag through `BASubprocessOrchestrator`
  - [x] 5.1 In `sendTaskAndRunLoop()`, read the `isRealCli` flag from the registered `SubprocessConfig` for the agent type
  - [x] 5.2 Pass `isRealCli` to `TaskMessageBuilder.buildTaskMessage()` call

- [x] 6. Unit tests for the fix
  - [x] 6.1 Test `writeCommandToStdin()` with `isRealCli = true`: verify plain text output, no JSON framing, no `---END---` wrapper
  - [x] 6.2 Test `writeCommandToStdin()` with `isRealCli = false`: verify JSON-framed output via `MessageProtocol.formatCommand()`
  - [x] 6.3 Test `TaskMessageBuilder.buildTaskMessage()` with `isRealCli = true`: verify includes `RESPONSE_PROTOCOL_SECTION`, returns plain text
  - [x] 6.4 Test `TaskMessageBuilder.buildTaskMessage()` with `isRealCli = false`: verify no protocol section, returns JSON-framed
  - [x] 6.5 Test `CliBackendResolver` resolved configs have `isRealCli = true`
  - [x] 6.6 Test `RESPONSE_PROTOCOL_SECTION` contains `---END---` instruction and `toolCall` format instruction
  - [x] 6.7 Verify existing `MessageProtocol` tests pass without modification
  - [x] 6.8 Verify existing `ToolCallLoopEngine` tests pass without modification
