# BA Agent MCP Tools Fix — Tasks

## Task 1: Implement the Fix

- [x] 1.1 Add `createdAtStart = true` to the BA agent registration `single` block in `BAAgentModule.kt`
  - File: `server/src/jvmMain/kotlin/com/assistant/server/agent/ba/BAAgentModule.kt`
  - Change line ~51: `single {` → `single(createdAtStart = true) {`
  - No other changes to the block's internal logic
  - Add a comment explaining why `createdAtStart` is needed (side-effect-only block)

## Task 2: Write Unit Tests

- [x] 2.1 Write a Koin test verifying BA agent is registered after module startup
  - Create test file: `server/src/jvmTest/kotlin/com/assistant/server/agent/ba/BAAgentModuleRegistrationTest.kt`
  - Test: load `agentModule` + `baAgentModule` with `startKoin`, assert `AgentRegistry.listAgentTypes()` contains `"ba-document"`
  - Test: `agentRegistry.getAgent("ba-document", testConfig)` does NOT throw `AgentNotFoundException`
  - Teardown: call `stopKoin()` after each test

- [x] 2.2 Write a preservation test verifying other beans resolve correctly
  - In the same test file, add tests that verify:
    - `MasterPromptBuilder` resolves successfully
    - `AgentJobExecutorBridge` resolves successfully
    - `CollectionStrategy` factory resolves and returns correct strategy for "BRD", "FSD", "SLIDES"
    - Curation components (TemporalClassifier, CommentSummarizer, AttachmentCurator, BudgetEnforcer, McpToolRegistrar) all resolve

## Task 3: Verify the Fix

- [x] 3.1 Compile the server module to confirm no compilation errors
  - Run: `./gradlew :server:compileKotlinJvm`
  - Verify: build succeeds with no errors

- [x] 3.2 Run the unit tests to verify fix and preservation
  - Run the new test class: `./gradlew :server:jvmTest --tests "com.assistant.server.agent.ba.BAAgentModuleRegistrationTest"`
  - Verify: all tests pass (8 tests)

## Task 4: Update Spec Documents

- [x] 4.1 Verify design.md and tasks.md are consistent with bugfix.md requirements
  - Confirm all requirements from bugfix.md (1.1–1.4, 2.1–2.4, 3.1–3.5) are addressed in design and tasks
  - Confirm correctness properties trace back to requirements
