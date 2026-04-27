# CLI Protocol Bypass — Bugfix Design

## Overview

`SubprocessManagerImpl.sendCommand()` always wraps prompts via `MessageProtocol.formatCommand()`, producing JSON framing (`{"type":"command","content":"..."}\n---END---\n`) that real CLI tools (Gemini CLI, Copilot CLI) cannot understand. Additionally, the AI inside the CLI is never told the response protocol (`---END---` delimiter, `{"toolCall":{...}}` JSON format). This fix adds an `isRealCli` flag to `SubprocessConfig` and conditionally bypasses JSON framing in `writeCommandToStdin()`, while injecting response protocol instructions into the prompt via `TaskMessageBuilder`.

## Glossary

- **Bug_Condition (C)**: The subprocess config has `isRealCli = true` — indicating a real CLI tool that expects plain text stdin
- **Property (P)**: Real CLI tools receive plain text prompts with embedded response protocol instructions; they know to output `---END---` and use `{"toolCall":{...}}` format
- **Preservation**: Custom protocol subprocesses (`isRealCli = false`) continue to receive JSON-framed commands via `MessageProtocol.formatCommand()` exactly as before
- **writeCommandToStdin()**: Helper function in `SubprocessManagerHelpers.kt` that writes formatted commands to the subprocess's stdin
- **MessageProtocol**: Object in `MessageProtocol.kt` that handles JSON wire-format framing for stdin/stdout communication
- **TaskMessageBuilder**: Object in `TaskMessageBuilder.kt` that constructs the initial task message sent to the AI subprocess
- **CliBackendResolver**: Class in `CliBackendResolver.kt` that resolves CLI backend names to `SubprocessConfig` instances
- **SubprocessConfig**: Data class in shared module defining subprocess spawn configuration (command, args, environment, timeouts)

## Bug Details

### Bug Condition

The bug manifests when a real CLI tool (Gemini CLI, Copilot CLI) is used as the subprocess backend. The `writeCommandToStdin()` function unconditionally calls `MessageProtocol.formatCommand(command)` which wraps the prompt in JSON framing. Real CLI tools pass this raw JSON string to their internal AI model, which cannot interpret it as a prompt. Additionally, `TaskMessageBuilder.buildTaskMessage()` unconditionally calls `MessageProtocol.formatCommand(content)` on the assembled prompt, adding a second layer of JSON framing.

**Formal Specification:**
```
FUNCTION isBugCondition(input)
  INPUT: input of type SendCommandInput  -- (agentType, command, subprocessConfig)
  OUTPUT: boolean

  RETURN input.subprocessConfig.isRealCli = true
END FUNCTION
```

### Examples

- **Gemini CLI receives JSON**: User configures Gemini CLI as backend → `writeCommandToStdin()` sends `{"type":"command","content":"## ROLE INSTRUCTION\n..."}\n---END---\n` → Gemini CLI passes raw JSON to Gemini model → model outputs garbage or nothing → `ToolCallLoopEngine` hangs until timeout
- **Copilot CLI receives JSON**: Same as above but with Copilot CLI → same failure mode
- **AI never outputs ---END---**: Even if the AI could parse the JSON, it was never told to output `---END---` when finished → `ToolCallLoopEngine.collectLines()` never terminates → timeout after 180s
- **AI never outputs toolCall JSON**: AI inside CLI doesn't know the `{"toolCall":{...}}` format → tool calls go unrecognized → document generated without any tool data

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- Custom protocol subprocesses (`isRealCli = false`) must continue to receive JSON-framed commands via `MessageProtocol.formatCommand()` with `---END---` delimiter
- `ToolCallLoopEngine` must continue to parse stdout lines via `MessageProtocol.parseStdoutLine()` and detect end-of-response via `MessageProtocol.isDelimiter()` identically
- `MessageProtocol.formatCommand()` must continue to produce the same JSON-framed output for all direct callers
- `MessageProtocol.formatToolResponse()` must continue to produce the same JSON-framed format for sending tool results back
- All existing unit tests for `MessageProtocol`, `SubprocessManagerImpl`, and `ToolCallLoopEngine` must pass without modification

**Scope:**
All subprocess configurations where `isRealCli = false` (the default) should be completely unaffected by this fix. This includes:
- Custom protocol subprocesses that understand `MessageProtocol` JSON framing
- Tool list injection via `MessageProtocol.formatToolList()`
- Tool response formatting via `MessageProtocol.formatToolResponse()`
- Stderr capture and process lifecycle management

## Hypothesized Root Cause

Based on the bug description and code analysis, the root causes are:

1. **Unconditional JSON framing in `writeCommandToStdin()`**: In `SubprocessManagerHelpers.kt` line ~107, `writeCommandToStdin()` always calls `MessageProtocol.formatCommand(command)` regardless of whether the subprocess is a real CLI tool or a custom protocol subprocess. There is no conditional path for plain text output.

2. **Unconditional JSON framing in `TaskMessageBuilder.buildTaskMessage()`**: In `TaskMessageBuilder.kt` line ~33, `buildTaskMessage()` wraps the assembled content via `MessageProtocol.formatCommand(content)`. For real CLI tools, this should return plain text content instead.

3. **Missing response protocol instructions**: `TaskMessageBuilder.buildMessageContent()` assembles role instructions, template structure, tool usage instructions, and strategy hints — but never includes instructions telling the AI to output `---END---` when finished or to use the `{"toolCall":{...}}` JSON format for tool calls. The AI inside a real CLI tool has no way to know the expected response protocol.

4. **No `isRealCli` flag in `SubprocessConfig`**: The `SubprocessConfig` data class has no field to distinguish real CLI tools from custom protocol subprocesses. Without this flag, `writeCommandToStdin()` and `TaskMessageBuilder` cannot make conditional decisions.

## Correctness Properties

Property 1: Bug Condition — Real CLI Receives Plain Text with Protocol Instructions

_For any_ subprocess configuration where `isRealCli = true`, the `writeCommandToStdin()` function SHALL write plain text directly to stdin (no JSON framing, no `---END---` wrapper), and the prompt content SHALL include response protocol instructions telling the AI to output `---END---` when finished and to use `{"toolCall":{...}}` format for tool calls.

**Validates: Requirements 2.1, 2.3, 2.4**

Property 2: Preservation — Custom Protocol Subprocesses Unchanged

_For any_ subprocess configuration where `isRealCli = false` (the default), the `writeCommandToStdin()` function SHALL produce exactly the same JSON-framed output as the original function via `MessageProtocol.formatCommand()`, preserving all existing stdin/stdout communication behavior.

**Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5**

## Fix Implementation

### Changes Required

Assuming our root cause analysis is correct:

**File**: `shared/src/commonMain/kotlin/com/assistant/agent/subprocess/SubprocessConfig.kt`

**Change**: Add `isRealCli` flag

**Specific Changes**:
1. **Add `isRealCli: Boolean = false`** property to the `SubprocessConfig` data class. Default `false` ensures all existing callers are unaffected. Real CLI backends (Gemini CLI, Copilot CLI) will set this to `true`.

---

**File**: `server/src/jvmMain/kotlin/com/assistant/server/agent/subprocess/SubprocessManagerHelpers.kt`

**Function**: `writeCommandToStdin()`

**Specific Changes**:
2. **Conditional bypass in `writeCommandToStdin()`**: Add an `isRealCli: Boolean = false` parameter to `writeCommandToStdin()`. If `isRealCli` is `true`, write the command as plain text (`command + "\n"`) directly to stdin. Otherwise, use `MessageProtocol.formatCommand(command)` as before. The caller (`SubprocessManagerImpl.sendCommand()`) looks up `configs[agentType]?.isRealCli ?: false` and passes it to `writeCommandToStdin()`.

---

**File**: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/subprocess/TaskMessageBuilder.kt`

**Function**: `buildTaskMessage()`

**Specific Changes**:
3. **Add `RESPONSE_PROTOCOL_SECTION` constant**: A string containing response protocol instructions that tell the AI to output `---END---` on a separate line when finished, and to use `{"toolCall":{...}}` JSON format for tool calls on a single line.
4. **Add `isRealCli` parameter to `buildTaskMessage()`**: When `isRealCli = true`, include `RESPONSE_PROTOCOL_SECTION` in the prompt content (via `buildMessageContent()`) and return plain text (no `MessageProtocol.formatCommand()` wrapping). When `isRealCli = false`, behave exactly as before.
5. **Include protocol section in `buildMessageContent()`**: When `isRealCli = true`, append the `RESPONSE_PROTOCOL_SECTION` after the tool usage instructions and before the strategy hint.

---

**File**: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/subprocess/CliBackendResolver.kt`

**Function**: `buildConfig()`

**Specific Changes**:
6. **Set `isRealCli = true`** in the `buildConfig()` helper. Since all four backends (gemini, copilot, kiro, ollama) share this helper, they all get `isRealCli = true`. This is correct because ollama also expects plain text stdin (`ollama run <model>`).

---

**File**: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/subprocess/BASubprocessOrchestrator.kt`

**Function**: `sendTaskAndRunLoop()`

**Specific Changes**:
7. **Pass `isRealCli` to `TaskMessageBuilder.buildTaskMessage()`**: Read the `isRealCli` flag from the registered `SubprocessConfig` and pass it to `buildTaskMessage()` so the prompt is built correctly for the subprocess type.

## Testing Strategy

### Validation Approach

The testing strategy follows a two-phase approach: first, surface counterexamples that demonstrate the bug on unfixed code, then verify the fix works correctly and preserves existing behavior.

### Exploratory Bug Condition Checking

**Goal**: Surface counterexamples that demonstrate the bug BEFORE implementing the fix. Confirm or refute the root cause analysis. If we refute, we will need to re-hypothesize.

**Test Plan**: Write tests that create a `SubprocessConfig` with `isRealCli = true` (once the flag exists) and verify that `writeCommandToStdin()` still produces JSON-framed output (demonstrating the bug). Also verify that `TaskMessageBuilder.buildTaskMessage()` output does not contain response protocol instructions.

**Test Cases**:
1. **JSON Framing Test**: Create a `ManagedSubprocess` with a real CLI config, call `writeCommandToStdin()`, capture stdin bytes → verify they contain `{"type":"command"` JSON framing (will demonstrate bug on unfixed code)
2. **Missing Protocol Instructions Test**: Call `TaskMessageBuilder.buildTaskMessage()` with a standard config → verify output does NOT contain `---END---` instruction text or `RESPONSE PROTOCOL` section (demonstrates the missing instructions)
3. **Timeout Simulation Test**: Verify that without `---END---` in AI output, `ToolCallLoopEngine` times out (demonstrates the hang)

**Expected Counterexamples**:
- `writeCommandToStdin()` always produces JSON-framed output regardless of config
- `TaskMessageBuilder` output never contains response protocol instructions
- Possible causes: unconditional `MessageProtocol.formatCommand()` call, no `isRealCli` flag

### Fix Checking

**Goal**: Verify that for all inputs where the bug condition holds (`isRealCli = true`), the fixed functions produce the expected behavior.

**Pseudocode:**
```
FOR ALL input WHERE isBugCondition(input) DO
  stdinContent := writeCommandToStdin_fixed(input)
  ASSERT NOT containsJsonFraming(stdinContent)
  ASSERT NOT endsWithDelimiter(stdinContent)

  promptContent := buildTaskMessage_fixed(input)
  ASSERT containsResponseProtocolSection(promptContent)
  ASSERT containsEndMarkerInstruction(promptContent)
  ASSERT containsToolCallFormatInstruction(promptContent)
END FOR
```

### Preservation Checking

**Goal**: Verify that for all inputs where the bug condition does NOT hold (`isRealCli = false`), the fixed functions produce the same result as the original functions.

**Pseudocode:**
```
FOR ALL input WHERE NOT isBugCondition(input) DO
  ASSERT writeCommandToStdin_original(input) = writeCommandToStdin_fixed(input)
  ASSERT buildTaskMessage_original(input) = buildTaskMessage_fixed(input)
END FOR
```

**Testing Approach**: Property-based testing is recommended for preservation checking because:
- It generates many random command strings and verifies JSON framing is always applied for `isRealCli = false`
- It catches edge cases (empty strings, strings containing JSON, strings containing `---END---`)
- It provides strong guarantees that behavior is unchanged for all non-real-CLI configs

**Test Plan**: Observe behavior on UNFIXED code first for `isRealCli = false` configs, then write property-based tests capturing that behavior.

**Test Cases**:
1. **JSON Framing Preservation**: For any command string with `isRealCli = false`, verify `writeCommandToStdin()` produces `MessageProtocol.formatCommand(command)` output
2. **TaskMessage Preservation**: For any `BATaskConfig` with `isRealCli = false`, verify `buildTaskMessage()` wraps content via `MessageProtocol.formatCommand()`
3. **MessageProtocol Unchanged**: Verify `MessageProtocol.formatCommand()`, `formatToolResponse()`, `parseStdoutLine()`, `isDelimiter()` all produce identical output to before
4. **ToolCallLoopEngine Unchanged**: Verify existing tool call loop tests pass without modification

### Unit Tests

- Test `SubprocessConfig` with `isRealCli = true` and `isRealCli = false` (default)
- Test `writeCommandToStdin()` conditional behavior: plain text for real CLI, JSON-framed for custom protocol
- Test `TaskMessageBuilder.buildTaskMessage()` with `isRealCli = true`: includes protocol section, returns plain text
- Test `TaskMessageBuilder.buildTaskMessage()` with `isRealCli = false`: no protocol section, returns JSON-framed
- Test `CliBackendResolver.buildConfig()` sets `isRealCli = true` for gemini/copilot/kiro backends
- Test `RESPONSE_PROTOCOL_SECTION` contains `---END---` instruction and `toolCall` format instruction

### Property-Based Tests

- Generate random command strings and verify: for `isRealCli = false`, output always matches `MessageProtocol.formatCommand(command)`
- Generate random command strings and verify: for `isRealCli = true`, output never contains `{"type":"command"` and never ends with `---END---\n`
- Generate random `BATaskConfig` variations and verify: for `isRealCli = true`, prompt always contains `RESPONSE PROTOCOL` section

### Integration Tests

- Full pipeline test with real Gemini CLI (`isRealCli = true`): verify AI receives clean prompt, outputs `---END---`, and tool calls are recognized
- Full pipeline test with fake subprocess (`isRealCli = false`): verify existing behavior unchanged
- Test `BASubprocessOrchestrator` passes `isRealCli` flag correctly from resolved config to `TaskMessageBuilder`
