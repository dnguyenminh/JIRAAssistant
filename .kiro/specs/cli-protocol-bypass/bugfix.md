# Bugfix Requirements Document

## Introduction

When `SubprocessManagerImpl.sendCommand()` is used with real CLI tools (Gemini CLI, Copilot CLI), the system fails to produce any AI output. The root cause is twofold:

1. **JSON framing incompatibility**: `writeCommandToStdin()` always wraps prompts through `MessageProtocol.formatCommand()`, producing `{"type":"command","content":"<prompt>"}\n---END---\n`. Real CLI tools expect plain text stdin, not JSON-wrapped commands — they pass the raw JSON string to the AI model, which cannot interpret it as a prompt.

2. **Missing response protocol instructions**: The AI running inside the real CLI tool is never told that it must output `---END---` when finished, nor the JSON format for tool calls. Without these instructions, `ToolCallLoopEngine` hangs waiting for a delimiter that never arrives, and tool calls are never recognized.

This is the last blocker for the full pipeline integration test (Test 1) with real Gemini CLI, identified in the `real-mcp-integration-test` spec's "Known Remaining Issues".

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN a real CLI tool (Gemini CLI, Copilot CLI) is used as the subprocess backend THEN the system sends JSON-framed input `{"type":"command","content":"<prompt>"}\n---END---\n` to stdin, which the CLI tool cannot interpret as a valid prompt

1.2 WHEN a real CLI tool receives the JSON-framed input THEN the AI model inside the CLI receives garbled/JSON text instead of a clean prompt, producing no meaningful output or erroring out

1.3 WHEN the AI inside a real CLI tool generates its response THEN it does not output `---END---` on a separate line because it was never instructed to do so, causing `ToolCallLoopEngine` to hang until timeout

1.4 WHEN the AI inside a real CLI tool needs to invoke a tool THEN it does not output the expected `{"toolCall":{...}}` JSON format because it was never given response protocol instructions, causing tool calls to go unrecognized

### Expected Behavior (Correct)

2.1 WHEN a real CLI tool is used as the subprocess backend THEN the system SHALL send plain text (no JSON framing, no `---END---` wrapper) to stdin so the CLI tool receives a clean, readable prompt

2.2 WHEN a real CLI tool receives plain text input THEN the AI model inside the CLI SHALL receive the full prompt content as-is and be able to process it normally

2.3 WHEN the prompt is built for a real CLI tool THEN the system SHALL include response protocol instructions in the prompt content telling the AI to output `---END---` on a separate line when finished

2.4 WHEN the prompt is built for a real CLI tool THEN the system SHALL include response protocol instructions in the prompt content telling the AI to output tool calls as single-line JSON in the `{"toolCall":{...}}` format already defined by `TOOL_CALL_FORMAT_EXAMPLE`

### Unchanged Behavior (Regression Prevention)

3.1 WHEN a custom subprocess protocol (non-real-CLI) is used as the backend THEN the system SHALL CONTINUE TO send JSON-framed commands via `MessageProtocol.formatCommand()` with `---END---` delimiter as before

3.2 WHEN `ToolCallLoopEngine` reads stdout from any subprocess THEN the system SHALL CONTINUE TO parse tool call JSON lines via `MessageProtocol.parseStdoutLine()` and detect end-of-response via `MessageProtocol.isDelimiter()` identically to current behavior

3.3 WHEN `MessageProtocol.formatCommand()` is called directly THEN it SHALL CONTINUE TO produce the same JSON-framed output format for all existing callers

3.4 WHEN `MessageProtocol.formatToolResponse()` is called to send tool results back to the subprocess THEN it SHALL CONTINUE TO produce the same JSON-framed format, as real CLI tools also need structured tool results via stdin

3.5 WHEN existing unit tests for `MessageProtocol`, `SubprocessManagerImpl`, or `ToolCallLoopEngine` are executed THEN they SHALL CONTINUE TO pass without modification

---

## Bug Condition (Formal)

### Bug Condition Function

```pascal
FUNCTION isBugCondition(X)
  INPUT: X of type SendCommandInput  -- (agentType, command, subprocessConfig)
  OUTPUT: boolean

  // Returns true when the subprocess is a real CLI tool (not a custom protocol)
  RETURN X.subprocessConfig.isRealCli = true
END FUNCTION
```

Where `isRealCli` identifies subprocess configurations targeting real CLI tools (Gemini CLI, Copilot CLI) that expect plain text stdin, as opposed to custom subprocess protocols that understand `MessageProtocol` JSON framing.

### Fix Checking Property

```pascal
// Property: Fix Checking — Real CLI receives plain text, with protocol instructions
FOR ALL X WHERE isBugCondition(X) DO
  stdinContent ← captureStdinWritten(X)
  ASSERT NOT containsJsonFraming(stdinContent)       // No {"type":"command",...} wrapper
  ASSERT NOT endsWithDelimiter(stdinContent)          // No ---END--- after prompt
  ASSERT containsProtocolInstructions(stdinContent)   // Has response protocol section
  ASSERT containsEndMarkerInstruction(stdinContent)   // Tells AI to output ---END---
  ASSERT containsToolCallFormatInstruction(stdinContent) // Tells AI the toolCall JSON format
END FOR
```

### Preservation Checking Property

```pascal
// Property: Preservation Checking — Custom protocol subprocesses unchanged
FOR ALL X WHERE NOT isBugCondition(X) DO
  ASSERT F(X) = F'(X)
  // writeCommandToStdin still calls MessageProtocol.formatCommand()
  // ToolCallLoopEngine behavior identical
  // MessageProtocol output identical
END FOR
```
