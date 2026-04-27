# Stdout Blocking Timeout Fix — Bugfix Design

## Overview

`emitStdoutUntilDelimiter()` in `SubprocessManagerHelpers.kt` uses `BufferedReader.readLine()` — a blocking Java I/O call that cannot be interrupted by Kotlin coroutine cancellation. When real CLI tools (Gemini CLI, Copilot CLI) don't output the `---END---` delimiter or stay alive in interactive mode, `withTimeoutOrNull` in `ToolCallLoopEngine.runLoop()` fires but cannot cancel the blocked thread, causing indefinite hangs. The fix wraps the blocking `readLine()` call in `kotlinx.coroutines.runInterruptible`, making it cancellable by coroutine timeout, and adds per-line idle timeout detection as a secondary safety net.

## Glossary

- **Bug_Condition (C)**: The condition that triggers the bug — `isRealCli = true` AND the CLI subprocess does not output `---END---` delimiter or stays alive in interactive mode after responding, causing `readLine()` to block indefinitely
- **Property (P)**: The desired behavior — the system SHALL detect the idle/blocked state within the configured timeout and return accumulated output with `timedOut = true`
- **Preservation**: Existing custom protocol subprocess behavior (`isRealCli = false`) that outputs `---END---` delimiter must remain completely unchanged
- **`emitStdoutUntilDelimiter()`**: The function in `SubprocessManagerHelpers.kt` that reads stdout lines in a `while(true)` loop using `BufferedReader.readLine()` until delimiter or EOF
- **`runLoop()`**: The function in `ToolCallLoopEngine.kt` that wraps Flow collection in `withTimeoutOrNull` — currently ineffective against blocking I/O
- **`runInterruptible`**: A `kotlinx.coroutines` function that runs a blocking call on a thread that can be interrupted when the coroutine is cancelled
- **`isRealCli`**: Flag in `SubprocessConfig` indicating whether the subprocess is a real CLI tool (plain text I/O) vs custom protocol (JSON framing with `---END---`)

## Bug Details

### Bug Condition

The bug manifests when a real CLI subprocess (`isRealCli = true`) finishes its response without outputting the `---END---` delimiter, or stays alive in interactive mode. The `emitStdoutUntilDelimiter()` function blocks on `managed.stdout.readLine()` — a Java blocking I/O call. When `withTimeoutOrNull` in `ToolCallLoopEngine.runLoop()` fires a `CancellationException`, it cannot interrupt the thread blocked on `readLine()`, so the coroutine never actually cancels.

**Formal Specification:**
```
FUNCTION isBugCondition(input)
  INPUT: input of type StdoutReadContext
  OUTPUT: boolean

  RETURN input.isRealCli = true
         AND (input.cliOutputsDelimiter = false
              OR input.cliStaysAliveAfterResponse = true)
         AND input.readLineIsBlocking = true
END FUNCTION
```

### Examples

- **Gemini CLI no delimiter**: `isRealCli=true`, Gemini CLI outputs response text then waits for next input without `---END---` → `readLine()` blocks forever → `withTimeoutOrNull(180s)` fires but cannot cancel → system hangs indefinitely
- **Copilot CLI interactive mode**: `isRealCli=true`, Copilot CLI outputs response then stays alive waiting for next prompt → `readLine()` blocks on empty stream → timeout ineffective → hang
- **Custom protocol (not buggy)**: `isRealCli=false`, subprocess outputs `{"type":"response",...}\n---END---\n` → `readLine()` reads delimiter → loop breaks normally → no hang
- **Real CLI with EOF**: `isRealCli=true`, CLI outputs response then exits (EOF) → `readLine()` returns `null` → loop breaks → no hang (edge case that works correctly today)

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- Custom protocol subprocesses (`isRealCli = false`) that output `---END---` delimiter must continue to break the read loop immediately and return the complete document
- Tool call parsing via `MessageProtocol.parseStdoutLine()` and proxying through `SubprocessProxy` must continue to work identically
- EOF handling (`readLine()` returns `null`) must continue to break the read loop
- `commandMutex` sequential execution guarantee must be preserved
- `MessageProtocol.formatCommand()` JSON framing for custom protocol must be unchanged
- `ToolCallLoopEngine.runLoop()` returning `timedOut = false` for successful completions must be preserved

**Scope:**
All inputs where `isRealCli = false` (custom protocol subprocesses) should be completely unaffected by this fix. The fix only changes behavior inside `emitStdoutUntilDelimiter()` for the blocking `readLine()` call path. No changes to `MessageProtocol`, `ToolCallLoopEngine`, or `ManagedSubprocess`.

## Hypothesized Root Cause

The root cause is **confirmed** (not hypothesized) through code analysis:

1. **Blocking I/O immune to coroutine cancellation**: `BufferedReader.readLine()` in `emitStdoutUntilDelimiter()` is a Java blocking I/O call. When a Kotlin coroutine is cancelled (e.g., by `withTimeoutOrNull`), it sets a cancellation flag and throws `CancellationException` at the next suspension point. But `readLine()` is not a suspension point — it's a JVM thread-level block. The thread remains stuck in `InputStream.read()` inside `readLine()`, and the coroutine framework has no way to interrupt it.

2. **Flow runs on `Dispatchers.IO` via `flowOn`**: In `SubprocessManagerImpl.sendCommand()`, the flow is created with `.flowOn(Dispatchers.IO)`. This means the `readLine()` call runs on an IO dispatcher thread. When `withTimeoutOrNull` cancels the flow collection in `ToolCallLoopEngine`, the cancellation propagates to the flow's coroutine, but the IO thread blocked on `readLine()` is not interrupted.

3. **No idle detection mechanism**: There is no secondary timeout that monitors "time since last line received." Even if `readLine()` were interruptible, there's no per-line idle timeout to detect when a CLI has stopped producing output but hasn't closed the stream.

## Correctness Properties

Property 1: Bug Condition — Blocked readLine Terminates Within Timeout

_For any_ input where `isRealCli = true` AND the subprocess does not output `---END---` AND `readLine()` would block indefinitely, the fixed `emitStdoutUntilDelimiter()` function SHALL terminate the read loop within the configured timeout period (tolerance of a few seconds), and `ToolCallLoopEngine.runLoop()` SHALL return a `ToolCallLoopResult` with `timedOut = true` and the document accumulated up to the blocking point.

**Validates: Requirements 2.1, 2.2, 2.3, 2.4**

Property 2: Preservation — Custom Protocol Behavior Unchanged

_For any_ input where `isRealCli = false` (custom protocol subprocess that outputs `---END---` delimiter), the fixed code SHALL produce exactly the same result as the original code: the read loop breaks on delimiter, the document is returned complete, and `timedOut = false`.

**Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6**

## Fix Implementation

### Changes Required

**File**: `server/src/jvmMain/kotlin/com/assistant/server/agent/subprocess/SubprocessManagerHelpers.kt`

**Function**: `emitStdoutUntilDelimiter()`

**Specific Changes**:

1. **Wrap `readLine()` in `runInterruptible`**: Replace the direct `managed.stdout.readLine()` call with `runInterruptible { managed.stdout.readLine() }`. This makes the blocking call cancellable — when the coroutine is cancelled (by `withTimeoutOrNull`), `runInterruptible` interrupts the thread, causing `readLine()` to throw `InterruptedException`, which is converted to `CancellationException`.

2. **Add per-line idle timeout**: Wrap each `runInterruptible { readLine() }` call in its own `withTimeoutOrNull(idleTimeoutMs)` to detect when no new output arrives within a configurable period (e.g., 30 seconds). This provides a secondary safety net: even if the overall `runLoop` timeout hasn't fired yet, the system detects that the CLI has gone silent and breaks the loop.

3. **Handle `InterruptedIOException` gracefully**: Catch `java.io.InterruptedIOException` (thrown by `PipedInputStream`/`BufferedReader` when the thread is interrupted during I/O) and break the read loop cleanly, allowing accumulated lines to be returned. Note: this is `InterruptedIOException` (subclass of `IOException`), not `InterruptedException` — Java blocking I/O throws the IO variant.

4. **Re-throw `CancellationException`**: When the parent coroutine is cancelled, catch `CancellationException`, log it, and re-throw it (not just break). This ensures proper coroutine cancellation propagation — swallowing `CancellationException` would violate structured concurrency.

5. **Add `idleTimeoutMs` parameter**: Add an optional `idleTimeoutMs: Long` parameter to `emitStdoutUntilDelimiter()` (default: `Long.MAX_VALUE`). The caller (`sendCommand()`) passes the actual timeout value based on `isRealCli`. This keeps the function signature backward-compatible — existing callers without the parameter get no per-line timeout.

6. **Pass `isRealCli`-derived timeout from `sendCommand()`**: The `sendCommand()` function in `SubprocessManagerImpl` computes `idleTimeoutMs` from `config.unresponsiveTimeoutMs` (default 60s) when `isRealCli = true`, or `Long.MAX_VALUE` when `isRealCli = false`, and passes it to `emitStdoutUntilDelimiter()`.

7. **Distinguish EOF from idle timeout in logging**: When `line == null`, only log a warning if `idleTimeoutMs < Long.MAX_VALUE` (indicating an actual idle timeout occurred). EOF (readLine returning null) breaks silently as before.

**Pseudocode for fixed `emitStdoutUntilDelimiter()` (matches implementation):**
```
FUNCTION emitStdoutUntilDelimiter(managed, collector, idleTimeoutMs = MAX_VALUE)
  WHILE true DO
    TRY
      line ← withTimeoutOrNull(idleTimeoutMs) {
        runInterruptible { managed.stdout.readLine() }
      }
    CATCH CancellationException →
      // Parent coroutine cancelled — re-throw for structured concurrency
      LOG info "Read loop cancelled by parent coroutine"
      THROW CancellationException
    CATCH InterruptedIOException →
      // Thread interrupted during blocking I/O — break cleanly
      LOG warn "readLine() interrupted — breaking read loop"
      BREAK
    END TRY
    IF line = null THEN
      IF idleTimeoutMs < MAX_VALUE THEN
        LOG warn "Idle timeout — breaking read loop"
      END IF
      BREAK
    END IF
    IF isDelimiter(line) THEN BREAK END IF
    collector.emit(line)
  END WHILE
END FUNCTION
```

**File**: `server/src/jvmMain/kotlin/com/assistant/server/agent/subprocess/SubprocessManagerImpl.kt`

**Function**: `sendCommand()`

**Specific Changes**:
1. Compute `idleTimeoutMs` from config: extract `config` from `configs[agentType]`, then use `config.unresponsiveTimeoutMs` (default 60s) when `isRealCli = true`, or `Long.MAX_VALUE` when `isRealCli = false`.
2. Pass `idleTimeoutMs` to `emitStdoutUntilDelimiter(managed, this, idleTimeoutMs)` inside the flow builder.
3. Log when interruptible read mode is active (`isRealCli = true`) with the computed idle timeout value.

## Testing Strategy

### Validation Approach

The testing strategy follows a two-phase approach: first, surface counterexamples that demonstrate the bug on unfixed code, then verify the fix works correctly and preserves existing behavior.

### Exploratory Bug Condition Checking

**Goal**: Surface counterexamples that demonstrate the bug BEFORE implementing the fix. Confirm the root cause by observing that `withTimeoutOrNull` fails to cancel `readLine()`.

**Test Plan**: Write tests that create a mock subprocess whose stdout blocks indefinitely (never sends delimiter, never closes stream). Run `emitStdoutUntilDelimiter()` with a short timeout. On unfixed code, the test will hang past the timeout, confirming the bug.

**Test Cases**:
1. **Blocking readLine Test**: Create a `PipedInputStream`/`PipedOutputStream` pair, write some lines but never write delimiter or close stream → `emitStdoutUntilDelimiter()` blocks forever (will hang on unfixed code)
2. **Real CLI Simulation**: Create a mock process that outputs text then goes silent → `sendCommand()` flow never completes (will hang on unfixed code)
3. **Timeout Ineffective Test**: Wrap the blocking call in `withTimeoutOrNull(2000)` → timeout fires but coroutine doesn't cancel (will hang on unfixed code)

**Expected Counterexamples**:
- `emitStdoutUntilDelimiter()` does not return within the timeout period
- `withTimeoutOrNull` returns but the underlying thread remains blocked on `readLine()`
- Possible cause confirmed: `readLine()` is not a coroutine suspension point

### Fix Checking

**Goal**: Verify that for all inputs where the bug condition holds, the fixed function terminates within the timeout and returns accumulated output.

**Pseudocode:**
```
FOR ALL input WHERE isBugCondition(input) DO
  result := emitStdoutUntilDelimiter_fixed(input)
  ASSERT result terminates within (idleTimeoutMs + tolerance)
  ASSERT accumulated lines = all lines emitted before block
  ASSERT no thread leak (blocked thread is interrupted)
END FOR
```

### Preservation Checking

**Goal**: Verify that for all inputs where the bug condition does NOT hold, the fixed function produces the same result as the original function.

**Pseudocode:**
```
FOR ALL input WHERE NOT isBugCondition(input) DO
  ASSERT emitStdoutUntilDelimiter_original(input) = emitStdoutUntilDelimiter_fixed(input)
END FOR
```

**Testing Approach**: Property-based testing is recommended for preservation checking because:
- It generates many test cases automatically across the input domain (varying line counts, line content, delimiter positions)
- It catches edge cases that manual unit tests might miss (empty lines, very long lines, rapid succession of lines)
- It provides strong guarantees that behavior is unchanged for all non-buggy inputs

**Test Plan**: Observe behavior on UNFIXED code first for custom protocol subprocesses (delimiter-terminated streams), then write property-based tests capturing that behavior.

**Test Cases**:
1. **Delimiter Preservation**: Verify that streams ending with `---END---` return all lines before delimiter, with `timedOut = false`
2. **EOF Preservation**: Verify that streams ending with EOF (null from readLine) return all lines, loop breaks cleanly
3. **Tool Call Preservation**: Verify that tool call JSON lines are still emitted correctly through the flow
4. **Empty Stream Preservation**: Verify that a stream with only `---END---` returns empty document

### Unit Tests

- Test `emitStdoutUntilDelimiter()` with `runInterruptible` wrapping — verify it terminates when stream blocks
- Test idle timeout detection — verify loop breaks after N seconds of no output
- Test `InterruptedException` handling — verify clean loop exit
- Test EOF handling still works with `runInterruptible` wrapping
- Test delimiter detection still works with `runInterruptible` wrapping
- Test `sendCommand()` passes correct idle timeout based on `isRealCli` flag

### Property-Based Tests

- Generate random sequences of stdout lines (varying count 0–100, content, with/without delimiter at end) → verify fixed function produces identical output to original for delimiter-terminated streams
- Generate random idle timeout values and line emission delays → verify timeout triggers correctly when delay exceeds idle timeout
- Generate random `isRealCli` flag values → verify custom protocol path is completely unchanged

### Integration Tests

- Test full `sendCommand()` → `ToolCallLoopEngine.runLoop()` pipeline with a mock blocking subprocess → verify `timedOut = true` returned within timeout
- Test full pipeline with a mock custom protocol subprocess → verify identical behavior to unfixed code
- Test that `commandMutex` is properly released after timeout-triggered loop exit
