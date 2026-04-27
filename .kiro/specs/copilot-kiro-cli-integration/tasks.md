# Tasks — Copilot CLI & Kiro CLI Integration

## Task 1: Extend ProviderType Enum

- [x] 1.1 Add `COPILOT_CLI` and `KIRO_CLI` values to `ProviderType` enum in `shared/src/commonMain/kotlin/com/assistant/ai/AIOrchestrator.kt`
- [x] 1.2 Verify serialization produces correct JSON strings by running shared module compile: `./gradlew :shared:compileKotlinJvm`

## Task 2: Implement CopilotCliAgent

- [x] 2.1 Create `server/src/jvmMain/kotlin/com/assistant/server/ai/CopilotCliAgent.kt` following GeminiCliAgent pattern
  - Constructor: `cliPath: String`, `model: String = "copilot"`
  - Implement `AIAgent.analyze(prompt, context): AIResult`
  - Implement `testConnection(): String?` with `--version` flag and 15s timeout
  - Implement `getAgentName()` returning `"Copilot CLI - $model"`
  - Use `ProcessBuilder`, `CompletableFuture.supplyAsync` for stdout/stderr, 240s timeout
  - Handle Windows with `cmd /c` prefix
  - Include `buildPrompt` helper for context formatting

## Task 3: Implement KiroCliAgent

- [x] 3.1 Create `server/src/jvmMain/kotlin/com/assistant/server/ai/KiroCliAgent.kt` following GeminiCliAgent pattern
  - Constructor: `cliPath: String`, `model: String = "kiro"`
  - Implement `AIAgent.analyze(prompt, context): AIResult`
  - Implement `testConnection(): String?` with `--version` flag and 15s timeout
  - Implement `getAgentName()` returning `"Kiro CLI - $model"`
  - Use `ProcessBuilder`, `CompletableFuture.supplyAsync` for stdout/stderr, 240s timeout
  - Handle Windows with `cmd /c` prefix
  - Include `buildPrompt` helper for context formatting

## Task 4: Update ServerModule Agent Resolution

- [x] 4.1 Add `CopilotCliAgent` and `KiroCliAgent` imports to `ServerModule.kt`
- [x] 4.2 Add `ProviderType.COPILOT_CLI` and `ProviderType.KIRO_CLI` cases to `buildAgentMap()` function
- [x] 4.3 Add `ProviderType.COPILOT_CLI` and `ProviderType.KIRO_CLI` to the ChatServiceImpl agent provider filter list

## Task 5: Update JobExecutor Agent Selection

- [x] 5.1 Add `CopilotCliAgent` and `KiroCliAgent` imports to `JobExecutor.kt`
- [x] 5.2 Add `ProviderType.COPILOT_CLI` and `ProviderType.KIRO_CLI` to the `resolveAgent()` filter list
- [x] 5.3 Add `when` cases for `COPILOT_CLI` → `CopilotCliAgent` and `KIRO_CLI` → `KiroCliAgent` in `resolveAgent()`

## Task 6: Update IntegrationRoutes for Test Connection

- [x] 6.1 Add `CopilotCliAgent` and `KiroCliAgent` imports to `IntegrationRoutes.kt`
- [x] 6.2 Add COPILOT_CLI test connection handling in `post("/{providerId}/test")` route (same pattern as GEMINI_CLI)
- [x] 6.3 Add KIRO_CLI test connection handling in `post("/{providerId}/test")` route
- [x] 6.4 Add COPILOT_CLI and KIRO_CLI to the defaults list in `get` handler
- [x] 6.5 Add `"copilot_cli"` and `"kiro_cli"` to the provider type resolution in `put("/{providerId}/config")` route

## Task 7: Update Frontend — Provider Cards

- [x] 7.1 Add Copilot CLI and Kiro CLI to `defaultProviders()` in `IntegrationsPage.kt`
- [x] 7.2 Add `"COPILOT_CLI"` and `"KIRO_CLI"` logo entries in `IntegrationsCardBuilder.kt` `providerLogo()` function

## Task 8: Update Frontend — Config Modal Fields

- [x] 8.1 Add `"COPILOT_CLI" -> buildCopilotCLIFields(provider, disabled)` case to `ProviderConfigFields.kt`
- [x] 8.2 Add `"KIRO_CLI" -> buildKiroCLIFields(provider, disabled)` case to `ProviderConfigFields.kt`
- [x] 8.3 Implement `buildCopilotCLIFields()` with CLI Path input (placeholder `/usr/local/bin/gh`) and Model Name input
- [x] 8.4 Implement `buildKiroCLIFields()` with CLI Path input (placeholder `/usr/local/bin/kiro`) and Model Name input

## Task 9: Update IntegrationsTestLink for CLI Providers

- [x] 9.1 Verify `IntegrationsTestLink` handles COPILOT_CLI and KIRO_CLI test results correctly (existing generic handler should work — verify no type-specific logic blocks new types)

## Task 10: Compile and Verify

- [x] 10.1 Compile shared module: `./gradlew :shared:compileKotlinJvm`
- [x] 10.2 Compile server module: `./gradlew :server:compileKotlinJvm`
- [x] 10.3 Compile frontend module: `./gradlew :frontend:compileKotlinJs`
- [x] 10.4 Run existing tests to verify no regressions: `./gradlew :server:jvmTest`
