# Bugfix Requirements Document

## Introduction

The `ba-document` agent is not registered in the `AgentRegistry` at application startup, causing BRD/FSD document generation to silently fall back to the curation pipeline. This happens because the agent registration code in `BAAgentModule.kt` is inside a Koin `single` block that is lazy-initialized, produces `Unit` (no explicit return type), and is never requested by any other component. As a result, the `AgentRegistry` remains empty, `AgentJobExecutorBridge.generate()` throws `AgentNotFoundException`, and `JobExecutor` catches the exception and falls back to the curation pipeline — which does not use MCP tools (e.g., markitdown for attachment parsing). This degrades document quality, with sections showing "Insufficient data" warnings.

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN the application starts up THEN the system does not execute the BA agent registration code in `BAAgentModule.kt` because the Koin `single` block is lazy-initialized, has no return type, and nothing requests the produced `Unit` bean

1.2 WHEN `JobExecutor` calls `agentBridge.generate()` to produce a BRD or FSD document with the agent pipeline enabled THEN the system throws `AgentNotFoundException` with message "Agent type 'ba-document' not found. Available: []" because the registry is empty

1.3 WHEN the agent pipeline fails with `AgentNotFoundException` THEN the system silently falls back to the curation pipeline, which does not use MCP tools (markitdown, etc.) for attachment parsing

1.4 WHEN documents are generated via the curation fallback pipeline THEN the system produces lower-quality documents with "Insufficient data" warnings in sections like "Existing Processes" and "Project Requirements" because attachment content is not properly extracted

### Expected Behavior (Correct)

2.1 WHEN the application starts up THEN the system SHALL eagerly execute the BA agent registration code so that `BADocumentAgent` is registered in the `AgentRegistry` before any document generation job runs

2.2 WHEN `JobExecutor` calls `agentBridge.generate()` to produce a BRD or FSD document with the agent pipeline enabled THEN the system SHALL successfully resolve the `ba-document` agent from the registry and execute the agent pipeline without throwing `AgentNotFoundException`

2.3 WHEN the agent pipeline is enabled and the `ba-document` agent is registered THEN the system SHALL use the agent pipeline (not the curation fallback) to generate documents, leveraging MCP tools registered on the Integrations page

2.4 WHEN documents are generated via the agent pipeline with MCP tools available THEN the system SHALL produce documents with properly extracted attachment content, without "Insufficient data" warnings caused by missing attachment parsing

### Unchanged Behavior (Regression Prevention)

3.1 WHEN the agent pipeline feature flag is disabled (`AGENT_PIPELINE_ENABLED` = "false") THEN the system SHALL CONTINUE TO use the curation pipeline or legacy prompt pipeline as before, without attempting agent resolution

3.2 WHEN the curation pipeline is enabled and the agent pipeline is disabled THEN the system SHALL CONTINUE TO generate documents via the curation pipeline with its existing behavior (temporal classification, comment summarization, budget enforcement)

3.3 WHEN the agent pipeline is enabled but the agent execution itself fails (not a registration error) THEN the system SHALL CONTINUE TO fall back to the curation pipeline as the existing fallback mechanism

3.4 WHEN other Koin module beans (MasterPromptBuilder, AgentJobExecutorBridge, CollectionStrategy factory, curation components) are resolved THEN the system SHALL CONTINUE TO provide them with the same behavior and dependencies as before

3.5 WHEN `AgentRegistryImpl.register()` is called for the `ba-document` agent type THEN the system SHALL CONTINUE TO log "Registered agent type: ba-document" and store the factory function correctly

---

### Bug Condition (Formal)

```pascal
FUNCTION isBugCondition(X)
  INPUT: X of type KoinModuleInitialization
  OUTPUT: boolean
  
  // The bug triggers when the BA agent registration single block
  // is never executed because nothing requests the Unit bean it produces
  RETURN X.baAgentRegistrationBlock.isLazyInitialized
     AND X.baAgentRegistrationBlock.returnType = Unit
     AND X.baAgentRegistrationBlock.neverRequested = true
END FUNCTION
```

### Fix Checking Property

```pascal
// Property: Fix Checking — BA Agent Registration at Startup
FOR ALL X WHERE isBugCondition(X) DO
  registry ← AgentRegistry after application startup
  ASSERT registry.listAgentTypes().contains("ba-document")
  ASSERT agentBridge.generate(ticketId, docType, tracker) does NOT throw AgentNotFoundException
END FOR
```

### Preservation Checking Property

```pascal
// Property: Preservation Checking — Non-agent-pipeline paths unchanged
FOR ALL X WHERE NOT isBugCondition(X) DO
  // When agent pipeline is disabled, behavior is identical to before the fix
  ASSERT F(X) = F'(X)
END FOR
```
