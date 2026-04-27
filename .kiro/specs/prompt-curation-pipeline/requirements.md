# Requirements Document

## Introduction

The Prompt Curation Pipeline is a pre-processing layer that transforms raw `EnrichedContext` data into a compact, classified, and deduplicated prompt suitable for BRD/FSD document generation. The current pipeline dumps all collected data verbatim (200K+ chars), causing Gemini CLI timeouts and producing noisy prompts with outdated/superseded information. This feature introduces intelligent curation that uses KB analysis records as the primary source, classifies information into AS-IS/TO-BE/OUTDATED categories, summarizes comments, and produces prompts within an 80K–120K character budget.

## Glossary

- **Curation_Pipeline**: The processing component that transforms raw EnrichedContext into a curated, classified, and size-constrained CuratedContext before prompt assembly
- **EnrichedContext**: The existing data model containing all tickets, comments, attachments, and KB analyses collected during deep ticket traversal
- **CuratedContext**: The output data model produced by the Curation_Pipeline, containing classified and deduplicated information ready for prompt assembly
- **KB_Record**: An AI-analyzed knowledge base record containing structured fields (businessSummary, asIsState, toBeState, extractedRequirements, acceptanceCriteria, technicalDetails)
- **AS-IS**: Classification for functionality already implemented from older tickets — provides historical context
- **TO-BE**: Classification for new requirements from the current ticket — the main focus of generated documents
- **OUTDATED**: Classification for old requirements that have been superseded by newer tickets — excluded from the final prompt
- **Temporal_Classifier**: The component that determines the temporal relationship (older/newer/concurrent) between linked tickets and the root ticket
- **Comment_Summarizer**: The component that deduplicates and summarizes raw comments into concise discussion summaries
- **Prompt_Budget**: The maximum character limit for the assembled prompt (target: 80K–120K chars)
- **Gemini_CLI**: The AI agent that processes prompts with a configurable timeout (240s, increased from the original 120s)
- **Root_Ticket**: The primary ticket being analyzed for document generation
- **Linked_Ticket**: Any ticket discovered during BFS traversal that is related to the Root_Ticket
- **MCP_KB_Lookup**: On-demand knowledge base query mechanism via MCP (Model Context Protocol) tools — allows the AI agent to request additional ticket details during generation instead of pre-loading everything into the prompt

## Requirements

### Requirement 1: KB-First Data Strategy

**User Story:** As a document generation system, I want to use KB analysis records as the primary information source, so that the prompt contains pre-analyzed, structured data instead of raw dumps.

#### Acceptance Criteria

1. WHEN building a curated prompt, THE Curation_Pipeline SHALL use KB_Record fields (businessSummary, asIsState, toBeState, extractedRequirements, acceptanceCriteria, technicalDetails) as the primary data source for each ticket
2. WHEN a KB_Record exists for a ticket, THE Curation_Pipeline SHALL NOT include raw ticket description or raw comment dumps for that ticket
3. WHEN a KB_Record does not exist for a ticket, THE Curation_Pipeline SHALL fall back to a summarized version of the raw ticket data (description + top 5 most recent comments)
4. THE Curation_Pipeline SHALL preserve all KB_Record fields for the Root_Ticket without truncation
5. WHEN processing Linked_Tickets with KB_Records, THE Curation_Pipeline SHALL include only businessSummary, asIsState, toBeState, and extractedRequirements fields (omitting verbose fields like technicalDetails unless budget permits)

### Requirement 2: Temporal Classification of Linked Tickets

**User Story:** As a document generation system, I want to classify linked tickets by their temporal relationship to the root ticket, so that the prompt clearly distinguishes between existing functionality and new requirements.

#### Acceptance Criteria

1. WHEN processing a Linked_Ticket, THE Temporal_Classifier SHALL determine its temporal relationship to the Root_Ticket as one of: OLDER, NEWER, or CONCURRENT
2. THE Temporal_Classifier SHALL use ticket creation date, resolution date, and last-updated date to determine temporal ordering
3. WHEN a Linked_Ticket is classified as OLDER and its requirements conflict with a NEWER ticket's requirements, THE Temporal_Classifier SHALL mark the conflicting requirements as OUTDATED
4. WHEN a Linked_Ticket is classified as OLDER and its requirements do not conflict with newer tickets, THE Temporal_Classifier SHALL mark those requirements as AS-IS (existing functionality)
5. WHEN a Linked_Ticket is classified as NEWER or CONCURRENT, THE Temporal_Classifier SHALL mark its requirements as TO-BE (new requirements)
6. THE Temporal_Classifier SHALL use the ticket status field (Closed, Done, In Progress, Open) as a secondary signal — closed tickets with resolved requirements are more likely AS-IS

### Requirement 3: AS-IS / TO-BE / OUTDATED Section Building

**User Story:** As a BRD/FSD author (AI), I want the prompt to clearly separate AS-IS context from TO-BE requirements, so that the generated document accurately distinguishes existing functionality from new development needs.

#### Acceptance Criteria

1. THE Curation_Pipeline SHALL produce a CuratedContext with three distinct sections: AS-IS, TO-BE, and a metadata section listing OUTDATED items
2. WHEN building the AS-IS section, THE Curation_Pipeline SHALL include summarized existing functionality from older resolved tickets, labeled with source ticket IDs
3. WHEN building the TO-BE section, THE Curation_Pipeline SHALL include all requirements from the Root_Ticket and any NEWER/CONCURRENT linked tickets
4. WHEN an item is classified as OUTDATED, THE Curation_Pipeline SHALL exclude it from the prompt body and include only a one-line reference in the metadata section (ticket ID + superseded-by ticket ID)
5. THE Curation_Pipeline SHALL place the TO-BE section before the AS-IS section in the assembled prompt, giving higher priority to new requirements
6. IF a requirement appears in both an older ticket and the Root_Ticket with modifications, THEN THE Curation_Pipeline SHALL include only the Root_Ticket version in TO-BE and note the evolution in a brief annotation

### Requirement 4: Comment Summarization and Deduplication

**User Story:** As a document generation system, I want to summarize and deduplicate comments instead of dumping them verbatim, so that the prompt contains only actionable discussion points within a reasonable size.

#### Acceptance Criteria

1. WHEN a ticket has more than 10 comments, THE Comment_Summarizer SHALL produce a condensed summary instead of including all comments verbatim
2. THE Comment_Summarizer SHALL identify and extract: decisions made, requirements clarified, blockers identified, and stakeholder approvals from comment threads
3. THE Comment_Summarizer SHALL deduplicate automated/bot comments (ScriptRunner reminders, status update bots) by keeping only the count and date range
4. WHEN summarizing comments, THE Comment_Summarizer SHALL preserve the most recent 3 substantive comments (non-bot, non-duplicate) in their original form as evidence
5. THE Comment_Summarizer SHALL produce output no larger than 2000 characters per ticket regardless of original comment volume
6. WHEN a KB_Record already contains the relevant information from comments, THE Comment_Summarizer SHALL skip comment processing for that ticket entirely

### Requirement 5: Attachment Content Curation

**User Story:** As a document generation system, I want to include only relevant attachment excerpts rather than full PDF dumps, so that attachment data doesn't dominate the prompt budget.

#### Acceptance Criteria

1. WHEN processing attachment chunks, THE Curation_Pipeline SHALL include only the first 3000 characters of each attachment as a preview
2. WHEN the KB_Record for a ticket already references attachment content in its fields, THE Curation_Pipeline SHALL exclude the raw attachment chunks for that ticket
3. THE Curation_Pipeline SHALL prioritize attachments from the Root_Ticket over attachments from Linked_Tickets
4. WHEN total attachment content exceeds 30000 characters, THE Curation_Pipeline SHALL truncate from lowest-priority attachments (deepest tickets first)
5. IF an attachment filename contains keywords "BRD", "FRD", "FSD", or "requirement", THEN THE Curation_Pipeline SHALL give it higher priority and allocate up to 8000 characters for its preview

### Requirement 6: Prompt Budget Enforcement

**User Story:** As a system operator, I want the curated prompt to stay within 80K–120K characters, so that Gemini CLI can process it within the timeout window without failures.

#### Acceptance Criteria

1. THE Curation_Pipeline SHALL produce a final prompt no larger than 120000 characters
2. THE Curation_Pipeline SHALL target a prompt size between 80000 and 120000 characters for optimal quality-to-size ratio
3. WHEN the curated content exceeds 120000 characters, THE Curation_Pipeline SHALL apply progressive truncation in this order: (1) deeper ticket details, (2) AS-IS section details, (3) attachment previews, (4) comment summaries
4. THE Curation_Pipeline SHALL NEVER truncate: the Root_Ticket KB_Record, the TO-BE section, the prompt skeleton (role, template, instructions), or diagram instructions
5. WHEN truncation occurs, THE Curation_Pipeline SHALL append a truncation annotation listing what was removed and the original vs final size
6. THE Curation_Pipeline SHALL log the final prompt size in characters to the application log for monitoring

### Requirement 7: Gemini CLI Timeout and Retry Configuration

**User Story:** As a system operator, I want to increase the Gemini CLI timeout and reduce retries, so that document generation jobs complete within the 5-minute job timeout.

#### Acceptance Criteria

1. THE Gemini_CLI SHALL use a timeout of 240000 milliseconds (4 minutes) per request instead of the current 120000 milliseconds
2. THE JobExecutor SHALL retry failed AI calls a maximum of 1 time (2 total attempts) instead of the current 2 retries (3 total attempts)
3. WHEN a Gemini_CLI call times out, THE JobExecutor SHALL log the prompt size, elapsed time, and attempt number before retrying
4. THE Gemini_CLI SHALL log the response time (in milliseconds) for every successful call for performance monitoring
5. WHEN the total job execution time approaches 280000 milliseconds (4 min 40 sec), THE JobExecutor SHALL skip further retries and fail fast with a descriptive error message

### Requirement 8: Curation Pipeline Integration

**User Story:** As a developer, I want the curation pipeline to integrate seamlessly into the existing document generation flow, so that it can be enabled/disabled via feature flag without breaking existing behavior.

#### Acceptance Criteria

1. THE Curation_Pipeline SHALL be invoked between the data aggregation step and the prompt assembly step in JobExecutor
2. WHEN the curation feature flag is disabled, THE JobExecutor SHALL use the existing PromptAssembler with the current 200K budget (backward compatibility)
3. WHEN the curation feature flag is enabled, THE JobExecutor SHALL pass EnrichedContext through the Curation_Pipeline before prompt assembly
4. THE Curation_Pipeline SHALL accept an EnrichedContext as input and produce a CuratedContext that is compatible with the existing PromptAssembler interface
5. THE Curation_Pipeline SHALL be stateless — given the same EnrichedContext input, it SHALL produce the same CuratedContext output (deterministic)
6. IF the Curation_Pipeline encounters an error during processing, THEN THE JobExecutor SHALL fall back to the existing uncurated prompt assembly and log a warning

### Requirement 9: Observability and Monitoring

**User Story:** As a system operator, I want to monitor prompt sizes and AI response times, so that I can detect performance regressions and validate the curation pipeline's effectiveness.

#### Acceptance Criteria

1. THE JobExecutor SHALL log the following metrics for every document generation: prompt size (chars), AI response time (ms), number of retries, and whether curation was applied
2. WHEN curation is applied, THE Curation_Pipeline SHALL log: original context size, curated context size, number of tickets classified as AS-IS/TO-BE/OUTDATED, and number of comments summarized
3. THE Curation_Pipeline SHALL log the time taken for the curation step itself (in milliseconds)
4. WHEN prompt size exceeds 120000 characters after curation (indicating a curation failure), THE Curation_Pipeline SHALL log a warning with the breakdown of section sizes

### Requirement 10: On-Demand Knowledge Base Lookup via MCP

**User Story:** As a document generation system, I want to query the knowledge base on-demand via MCP tools when additional context is needed, instead of including all reference data in the prompt upfront, so that the prompt stays compact and only contains information actually needed for the document.

#### Acceptance Criteria

1. WHEN the Curation_Pipeline determines that a Linked_Ticket's KB_Record is sufficient for context, THE Curation_Pipeline SHALL include only a brief reference (ticket ID + one-line summary) in the prompt instead of full details
2. THE prompt SHALL include an instruction block telling the AI that it can request additional ticket details via MCP tool calls (kb_search, kb_read) if the provided summary is insufficient for a specific BRD section
3. WHEN the AI agent supports tool use (e.g., Gemini with function calling), THE Curation_Pipeline SHALL register KB lookup as an available tool with parameters: ticketId, fieldName (businessSummary, asIsState, toBeState, extractedRequirements, technicalDetails)
4. WHEN the AI agent does NOT support tool use (e.g., Ollama), THE Curation_Pipeline SHALL fall back to including the full curated context in the prompt (no MCP lookup)
5. THE Curation_Pipeline SHALL track which tickets are included as "reference-only" (brief summary) vs "full-detail" (complete KB fields) and log the counts for monitoring
6. THE on-demand lookup SHALL be bounded — the AI agent SHALL NOT make more than 20 KB lookup calls per document generation to prevent infinite loops
