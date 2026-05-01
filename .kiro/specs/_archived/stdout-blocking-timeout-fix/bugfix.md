# Bugfix Requirements Document

## Introduction

When using real CLI tools (Gemini CLI, Copilot CLI) as subprocess backends, the `ToolCallLoopEngine.runLoop()` hangs indefinitely. The root cause is that `emitStdoutUntilDelimiter()` in `SubprocessManagerHelpers.kt` calls `managed.stdout.readLine()` — a **blocking Java I/O call** on a `BufferedReader`. Although `ToolCallLoopEngine.runLoop()` wraps the collection in `withTimeoutOrNull(timeoutSeconds * 1000L)`, Kotlin coroutine cancellation **cannot interrupt** a thread blocked on `BufferedReader.readLine()`. This causes the entire agent pipeline to freeze when real CLI tools don't output the `---END---` delimiter or stay alive in interactive mode after responding.

**Impact**: Any BA document generation task using a real CLI backend (Gemini CLI, Copilot CLI, Kiro CLI) will hang until the JVM-level test timeout kills the process, making real CLI integration unusable.

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN a real CLI subprocess (`isRealCli = true`) finishes its response without outputting the `---END---` delimiter THEN the system blocks indefinitely on `BufferedReader.readLine()` inside `emitStdoutUntilDelimiter()`, ignoring the configured `withTimeoutOrNull` timeout

1.2 WHEN a real CLI subprocess stays alive in interactive mode after responding (no EOF on stdout) THEN the system blocks indefinitely on `BufferedReader.readLine()` because the stream never returns `null` and no delimiter arrives

1.3 WHEN `withTimeoutOrNull` fires a `CancellationException` after the configured timeout elapses THEN the system fails to cancel the blocking `readLine()` call because Java blocking I/O is not interruptible by coroutine cancellation, and the thread remains blocked

1.4 WHEN the `ToolCallLoopEngine.runLoop()` timeout expires while `readLine()` is blocked THEN the system does not return a partial result with `timedOut = true`; instead it hangs until an external mechanism (JVM test timeout, process kill) terminates execution

### Expected Behavior (Correct)

2.1 WHEN a real CLI subprocess finishes its response without outputting the `---END---` delimiter THEN the system SHALL detect the idle period (no new stdout lines within a configurable idle timeout) and terminate the read loop, returning the accumulated document with `timedOut = true`

2.2 WHEN a real CLI subprocess stays alive in interactive mode after responding THEN the system SHALL break out of the `readLine()` loop when the configured timeout elapses, returning whatever document lines have been accumulated so far

2.3 WHEN `withTimeoutOrNull` fires after the configured timeout elapses THEN the system SHALL ensure the blocking `readLine()` call is actually interrupted or bypassed (e.g., via `runInterruptible`, stream closure, or polling with `InputStream.available()`), allowing the coroutine to complete cancellation

2.4 WHEN the `ToolCallLoopEngine.runLoop()` timeout expires THEN the system SHALL return a `ToolCallLoopResult` with `timedOut = true` and the document accumulated up to that point, rather than hanging indefinitely

### Unchanged Behavior (Regression Prevention)

3.1 WHEN a custom protocol subprocess (`isRealCli = false`) outputs the `---END---` delimiter after its response THEN the system SHALL CONTINUE TO break the read loop immediately and return the complete document with `timedOut = false`

3.2 WHEN a custom protocol subprocess sends a valid tool call request via stdout THEN the system SHALL CONTINUE TO parse the tool call, proxy it through `SubprocessProxy`, and write the response back to stdin

3.3 WHEN a subprocess process crashes (exits unexpectedly) and `readLine()` returns `null` (EOF) THEN the system SHALL CONTINUE TO break the read loop and return the accumulated document

3.4 WHEN `ToolCallLoopEngine.runLoop()` completes within the configured timeout THEN the system SHALL CONTINUE TO return `timedOut = false` with the full document

3.5 WHEN `SubprocessManagerImpl.sendCommand()` is called for a custom protocol subprocess THEN the system SHALL CONTINUE TO use `MessageProtocol.formatCommand()` for JSON framing and `emitStdoutUntilDelimiter()` for reading the response

3.6 WHEN the `commandMutex` is held during a command execution THEN the system SHALL CONTINUE TO ensure sequential command execution per subprocess, releasing the mutex in the `finally` block

---

### Bug Condition (Formal)

```pascal
FUNCTION isBugCondition(X)
  INPUT: X of type StdoutReadContext
  OUTPUT: boolean
  
  // The bug triggers when readLine() blocks AND coroutine cancellation cannot interrupt it
  RETURN X.isRealCli = true
     AND (X.cliOutputsDelimiter = false OR X.cliStaysAliveAfterResponse = true)
     AND X.readLineIsBlocking = true
END FUNCTION
```

### Fix Checking Property

```pascal
// Property: Fix Checking — Timeout must terminate blocked reads
FOR ALL X WHERE isBugCondition(X) DO
  result ← runLoop'(X)
  ASSERT result.timedOut = true
     AND result.document = accumulatedLinesBeforeBlock
     AND executionTime ≤ X.timeoutSeconds + toleranceSeconds
END FOR
```

### Preservation Checking Property

```pascal
// Property: Preservation Checking — Custom protocol behavior unchanged
FOR ALL X WHERE NOT isBugCondition(X) DO
  ASSERT F(X) = F'(X)
  // i.e., for custom protocol subprocesses that output ---END---,
  // the fixed code produces identical results to the original code
END FOR
```
