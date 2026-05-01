# Tasks — Stdout Blocking Timeout Fix

## Task 1: Make `emitStdoutUntilDelimiter()` cancellable with `runInterruptible`

- [x] 1.1 Add `idleTimeoutMs: Long = Long.MAX_VALUE` parameter to `emitStdoutUntilDelimiter()` function signature
- [x] 1.2 Wrap `managed.stdout.readLine()` call inside `runInterruptible { ... }` block (import `kotlinx.coroutines.runInterruptible`)
- [x] 1.3 Wrap the `runInterruptible { readLine() }` call inside `withTimeoutOrNull(idleTimeoutMs)` to detect per-line idle timeout
- [x] 1.4 Handle `null` result from `withTimeoutOrNull` (idle timeout) by logging a warning and breaking the loop
- [x] 1.5 Add `try/catch` for `CancellationException` around the read loop body to break cleanly when parent coroutine is cancelled
- [x] 1.6 Verify file stays within 200-line limit; extract helper if needed

## Task 2: Pass idle timeout from `sendCommand()` based on `isRealCli` flag

- [x] 2.1 In `SubprocessManagerImpl.sendCommand()`, compute `idleTimeoutMs` value: use `config.unresponsiveTimeoutMs` (default 60s) when `isRealCli = true`, use `Long.MAX_VALUE` when `isRealCli = false`
- [x] 2.2 Pass `idleTimeoutMs` to `emitStdoutUntilDelimiter(managed, this, idleTimeoutMs)` call inside the flow builder
- [x] 2.3 Add logging in `sendCommand()` when `isRealCli = true` to indicate interruptible read mode is active

## Task 3: Write unit tests for the fix

- [x] 3.1 Create test file `server/src/jvmTest/kotlin/com/assistant/server/agent/subprocess/EmitStdoutInterruptibleTest.kt`
- [x] 3.2 Write test: blocking stream with `runInterruptible` terminates within idle timeout — create `PipedInputStream`/`PipedOutputStream`, write lines but never delimiter, verify `emitStdoutUntilDelimiter()` returns within `idleTimeoutMs + 2s` tolerance
- [x] 3.3 Write test: delimiter-terminated stream returns all lines correctly — write lines + `---END---`, verify all lines emitted and function returns promptly
- [x] 3.4 Write test: EOF-terminated stream returns all lines correctly — write lines then close stream, verify all lines emitted and function returns
- [x] 3.5 Write test: parent coroutine cancellation interrupts blocked `readLine()` — launch `emitStdoutUntilDelimiter()` in a coroutine, cancel it after 1s, verify it terminates within 2s
- [x] 3.6 Write test: `isRealCli = false` path uses `Long.MAX_VALUE` idle timeout (preservation) — verify no per-line timeout applied for custom protocol

## Task 4: Write integration test for full pipeline timeout

- [x] 4.1 Create test file `server/src/jvmTest/kotlin/com/assistant/server/agent/subprocess/SubprocessTimeoutIntegrationTest.kt`
- [x] 4.2 Write test: `sendCommand()` with mock blocking subprocess returns flow that terminates on cancellation — create a real `Process` (e.g., `cat` or `cmd /c pause`) that blocks, call `sendCommand()`, collect flow with `withTimeoutOrNull(5s)`, verify it terminates
- [x] 4.3 Write test: `sendCommand()` with mock custom protocol subprocess preserves existing behavior — create subprocess that outputs lines + `---END---`, verify flow collects all lines and completes normally
- [x] 4.4 Write test: `commandMutex` is released after timeout-triggered exit — verify mutex is unlocked after flow collection times out, allowing subsequent commands

## Task 5: Verify compilation and run tests

- [x] 5.1 Run `./gradlew :server:compileKotlinJvm` to verify no compilation errors
- [x] 5.2 Run the new unit tests: `./gradlew :server:jvmTest --tests "*.EmitStdoutInterruptibleTest"`
- [x] 5.3 Run the new integration tests: `./gradlew :server:jvmTest --tests "*.SubprocessTimeoutIntegrationTest"`
- [x] 5.4 Run existing subprocess-related tests to verify no regressions: `./gradlew :server:jvmTest --tests "*.subprocess.*"`
