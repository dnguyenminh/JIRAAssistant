# Requirements Document

## Introduction

The Code Intelligence System provides a shared, structured understanding of the codebase and database schema to all agents (BA, SA, QA, DEV, DevOps) in the multi-agent software development pipeline. Currently, agents produce documents (BRD, FSD, TDD, STP/STC, DPG/RLN) and code without analyzing the existing codebase — the SA agent creates TDD without knowing the actual tech stack, the DEV agent implements code without understanding existing conventions, and when code changes, no agent is aware. This system solves these problems by indexing source code, enriching it with semantic context, storing the results in a queryable Knowledge Base and version-controlled analysis files, and keeping everything up to date through automatic hooks and on-demand scans.

## Glossary

- **Code_Index**: A structured representation of the project's source code containing file paths, class signatures, function signatures, import dependencies, and last-modified timestamps for every indexed file.
- **Index_Metadata**: A JSON file (`.analysis/code-intelligence/index-metadata.json`) that stores per-file timestamps and content hashes used to determine whether a file needs re-indexing.
- **Semantic_Annotation**: A piece of contextual information (business context, design decision, requirement traceability, implementation note, known issue, or TODO) attached to a Code_Index entry by an agent.
- **Incremental_Indexer**: The component responsible for re-indexing only the files that have changed since the last indexing run, as determined by comparing file timestamps and content hashes against Index_Metadata.
- **Full_Indexer**: The component responsible for scanning and indexing every source file in the project, rebuilding the entire Code_Index from scratch.
- **Analysis_File**: A human-readable Markdown file stored under `.analysis/code-intelligence/` that presents code intelligence data in a version-controlled, reviewable format.
- **Project_Structure_File**: The Analysis_File at `.analysis/code-intelligence/project-structure.md` that provides a high-level overview of all modules, their purposes, tech stacks, and inter-module dependencies.
- **Module_Analysis_File**: An Analysis_File at `.analysis/code-intelligence/modules/{module-name}.md` that provides detailed analysis of a single module including its packages, key classes, patterns, and conventions.
- **Knowledge_Base**: The existing MCP-based knowledge store (mcp_knowledge_base) used for cross-agent searchable access to code intelligence data.
- **Hook**: A Kiro framework event trigger (fileEdited, fileCreated, fileDeleted, userTriggered) that initiates an automated agent action.
- **Agent**: One of the five specialized agents in the pipeline: BA_Agent, SA_Agent, QA_Agent, DEV_Agent, DevOps_Agent.
- **Source_File**: Any file in the project with an extension matching the configured indexable types (`.kt`, `.java`, `.ts`, `.tsx`, `.gradle.kts`, `.yml`, `.properties`, `.xml`, `.sql`).
- **Module**: A top-level Gradle subproject in the workspace (e.g., core, gateway, portal, auth, workflow, antifraud, shared, flowable-adapter, feol-mock-api).

## Requirements

### Requirement 1: Source Code Indexing

**User Story:** As an agent in the pipeline, I want the system to maintain a structured index of all source files, so that I can query the codebase without performing a full scan each time.

#### Acceptance Criteria

1. WHEN the Full_Indexer is triggered, THE Code_Index SHALL scan all directories under each Module's `src/` folder and record an entry for every Source_File containing: file path, language, module name, package name, class signatures (name, visibility, superclass, interfaces), function signatures (name, visibility, parameters, return type), import statements, and last-modified timestamp.
2. WHEN the Full_Indexer completes, THE Code_Index SHALL write the Index_Metadata file at `.analysis/code-intelligence/index-metadata.json` containing for each indexed file: the relative file path, a SHA-256 content hash, and the last-indexed UTC timestamp.
3. WHEN a Source_File is created or modified, THE Incremental_Indexer SHALL compare the file's current SHA-256 content hash against the hash stored in Index_Metadata and re-index only the files whose hashes differ.
4. WHEN a Source_File is deleted, THE Incremental_Indexer SHALL remove the corresponding entry from the Code_Index and from Index_Metadata.
5. THE Code_Index SHALL complete a full index of a project containing up to 5,000 Source_Files within 120 seconds.
6. THE Incremental_Indexer SHALL complete re-indexing of a single changed file within 5 seconds.
7. IF a Source_File contains syntax errors that prevent parsing, THEN THE Code_Index SHALL record the file path with an `indexing_status` of `"parse_error"` and a human-readable error message, and SHALL continue indexing remaining files without interruption.

### Requirement 2: Knowledge Base Storage

**User Story:** As an agent in the pipeline, I want code intelligence data stored in the Knowledge Base, so that I can search and retrieve codebase information using natural language queries.

#### Acceptance Criteria

1. WHEN the Code_Index completes indexing (full or incremental), THE Code_Index SHALL ingest a structured document into the Knowledge_Base for each indexed Module using `mcp_knowledge_base_kb_ingest`, containing the module name, its package structure, class list with signatures, and dependency summary.
2. WHEN a Module's index data is ingested into the Knowledge_Base, THE Code_Index SHALL tag the document with `project` set to the workspace name and `tags` including `"code-index"`, the module name, and the programming language.
3. WHEN an agent queries the Knowledge_Base using `mcp_knowledge_base_kb_search_smart` with a code-related query (e.g., "how is authentication implemented"), THE Knowledge_Base SHALL return relevant Code_Index entries ranked by semantic similarity.
4. WHEN the Incremental_Indexer updates a file's index, THE Code_Index SHALL update the corresponding Knowledge_Base document rather than creating a duplicate entry.
5. IF the Knowledge_Base MCP is unavailable during indexing, THEN THE Code_Index SHALL log a warning, complete the indexing to Analysis_Files, and retry Knowledge_Base ingestion on the next indexing run.

### Requirement 3: Analysis File Generation

**User Story:** As a developer reviewing the project, I want human-readable analysis files committed to version control, so that code intelligence is visible, reviewable, and available even without the Knowledge Base.

#### Acceptance Criteria

1. WHEN the Full_Indexer completes, THE Code_Index SHALL generate the Project_Structure_File at `.analysis/code-intelligence/project-structure.md` containing: a table of all Modules with their purpose, primary language, framework, key dependencies, and source file count.
2. WHEN the Full_Indexer completes, THE Code_Index SHALL generate a Module_Analysis_File at `.analysis/code-intelligence/modules/{module-name}.md` for each Module containing: package structure tree, list of key classes with their responsibilities, public API surface (public functions and their signatures), dependency graph (what the module imports from other modules), and detected patterns (naming conventions, DI style, error handling approach).
3. WHEN the Incremental_Indexer re-indexes a file belonging to a specific Module, THE Code_Index SHALL regenerate only that Module's Module_Analysis_File and update the relevant row in the Project_Structure_File, leaving other Module_Analysis_Files unchanged.
4. THE Project_Structure_File SHALL include a "Last Updated" UTC timestamp at the top of the document.
5. WHEN a Module is added to the project (a new directory with a `build.gradle.kts` appears), THE Code_Index SHALL create a new Module_Analysis_File for the Module and add a new row to the Project_Structure_File.
6. WHEN a Module is removed from the project, THE Code_Index SHALL delete the corresponding Module_Analysis_File and remove the row from the Project_Structure_File.

### Requirement 4: Semantic Enrichment by Agents

**User Story:** As an SA or DEV agent, I want to attach business context, design decisions, and implementation notes to indexed code entries, so that other agents can understand not just the structure but the purpose and rationale behind the code.

#### Acceptance Criteria

1. WHEN the SA_Agent creates or updates a TDD, THE SA_Agent SHALL write Semantic_Annotations to the relevant Module_Analysis_File linking classes and functions to requirement IDs (e.g., "Implements UC-1, BR-3") and recording design decisions (e.g., "Strategy pattern chosen for payment processing to support multiple providers").
2. WHEN the DEV_Agent completes an implementation, THE DEV_Agent SHALL write Semantic_Annotations to the relevant Module_Analysis_File recording: function purposes, known issues, TODOs, and implementation notes for newly created or modified classes.
3. THE Semantic_Annotation SHALL be stored in a dedicated `## Annotations` section within each Module_Analysis_File, formatted as a Markdown table with columns: Target (class or function name), Author_Agent, Annotation_Type (requirement-link, design-decision, implementation-note, known-issue, todo), Content, and Timestamp.
4. WHEN the Incremental_Indexer regenerates a Module_Analysis_File, THE Incremental_Indexer SHALL preserve all existing Semantic_Annotations in the `## Annotations` section.
5. WHEN a class or function referenced by a Semantic_Annotation is deleted from the codebase, THE Incremental_Indexer SHALL mark the annotation's Target as `"[DELETED]"` rather than removing the annotation.
6. WHEN a Semantic_Annotation is written, THE Code_Index SHALL also ingest the annotation into the Knowledge_Base with tags `"semantic-annotation"`, the module name, and the annotation type, so that agents can search annotations via `mcp_knowledge_base_kb_search_smart`.

### Requirement 5: Automatic Hook-Based Updates

**User Story:** As a developer, I want the code index to update automatically when I save, create, or delete files, so that the index stays current without manual intervention and without slowing down my workflow.

#### Acceptance Criteria

1. WHEN a fileEdited event fires for a Source_File, THE Hook SHALL trigger the Incremental_Indexer for that specific file.
2. WHEN a fileCreated event fires for a Source_File, THE Hook SHALL trigger the Incremental_Indexer to add the new file to the Code_Index.
3. WHEN a fileDeleted event fires for a Source_File, THE Hook SHALL trigger the Incremental_Indexer to remove the file from the Code_Index.
4. THE Hook SHALL filter events by file extension, triggering only for Source_Files matching the configured indexable extensions (`.kt`, `.java`, `.ts`, `.tsx`, `.gradle.kts`, `.yml`, `.properties`, `.xml`, `.sql`).
5. THE Hook-triggered Incremental_Indexer SHALL complete its work within 10 seconds of the triggering event to avoid blocking the developer's workflow.
6. IF the Hook-triggered Incremental_Indexer encounters an error, THEN THE Hook SHALL log the error to the console and SHALL NOT interrupt the developer's current editing session.
7. THE Hook SHALL NOT trigger for files inside `build/`, `.gradle/`, `.git/`, `node_modules/`, or other generated directories.

### Requirement 6: On-Demand Scanning by Agents

**User Story:** As an SA or DEV agent starting work on a new ticket, I want to trigger a fresh scan of the codebase, so that I have the most up-to-date code intelligence before creating a TDD or implementing code.

#### Acceptance Criteria

1. WHEN the SA_Agent begins creating a TDD (Step 1.5 of the SA workflow), THE SA_Agent SHALL read the Index_Metadata file and, if any Source_File's last-modified timestamp is newer than its last-indexed timestamp, trigger the Incremental_Indexer before proceeding with code analysis.
2. WHEN the DEV_Agent begins implementation (Step 1 of the DEV workflow), THE DEV_Agent SHALL read the Index_Metadata file and, if any Source_File's last-modified timestamp is newer than its last-indexed timestamp, trigger the Incremental_Indexer before proceeding with code analysis.
3. WHEN an agent triggers an on-demand scan, THE Incremental_Indexer SHALL update both the Analysis_Files and the Knowledge_Base before returning control to the agent.
4. THE on-demand scan SHALL complete within 30 seconds for a project with up to 500 changed files since the last index.

### Requirement 7: Manual Full Re-Index

**User Story:** As a developer, I want to manually trigger a complete re-index of the entire project, so that I can rebuild the code intelligence from scratch after major changes like branch switches or large merges.

#### Acceptance Criteria

1. WHEN the user triggers a manual re-index via a userTriggered Hook, THE Full_Indexer SHALL delete the existing Index_Metadata, re-scan all Source_Files, rebuild the entire Code_Index, regenerate all Analysis_Files, and re-ingest all data into the Knowledge_Base.
2. WHEN the Full_Indexer starts a manual re-index, THE Full_Indexer SHALL display a progress indicator showing the number of files indexed out of the total file count.
3. WHEN the Full_Indexer completes a manual re-index, THE Full_Indexer SHALL report a summary including: total files indexed, total modules found, total classes found, total functions found, number of parse errors, and elapsed time.
4. IF the manual re-index is interrupted (e.g., agent session ends), THEN THE Full_Indexer SHALL leave the Index_Metadata in a consistent state so that the next run can resume or restart cleanly.

### Requirement 8: Agent Consumption of Code Intelligence

**User Story:** As any agent in the pipeline, I want a consistent way to access code intelligence data, so that I can make informed decisions based on the actual codebase.

#### Acceptance Criteria

1. WHEN the SA_Agent reads code intelligence before creating a TDD, THE SA_Agent SHALL retrieve from the Code_Index: the actual tech stack (languages, frameworks, library versions), existing API patterns (URL format, error response format, DTO conventions), existing database access patterns (ORM, repository style), and existing naming conventions.
2. WHEN the DEV_Agent reads code intelligence before implementing code, THE DEV_Agent SHALL retrieve from the Code_Index: existing package structure, naming conventions, dependency injection style, error handling patterns, logging patterns, and test patterns used in the target Module.
3. WHEN the QA_Agent reads code intelligence before creating test plans, THE QA_Agent SHALL retrieve from the Code_Index: the list of testable components (controllers, services, repositories), existing test frameworks and patterns, and public API surface for the target Module.
4. THE Code_Index SHALL provide data through two access paths: (a) reading Analysis_Files directly via `readFile`, and (b) querying the Knowledge_Base via `mcp_knowledge_base_kb_search_smart`.
5. WHEN an agent queries the Knowledge_Base for code intelligence, THE Knowledge_Base SHALL return results that include the module name, file path, and relevant code signatures so that the agent can locate the actual source code for deeper inspection.

### Requirement 9: Database Schema Indexing

**User Story:** As an SA agent, I want the code intelligence system to also index the database schema, so that I can design accurate data models without manually querying the database each time.

#### Acceptance Criteria

1. WHEN the Full_Indexer is triggered and the database MCP (`mcp_database_mcp`) is available, THE Code_Index SHALL query the database to retrieve: all schemas, all tables per schema, column definitions (name, type, nullable, default), primary keys, foreign keys, indexes, and approximate row counts.
2. WHEN the Full_Indexer completes database indexing, THE Code_Index SHALL generate a database analysis file at `.analysis/code-intelligence/database-schema.md` containing: a table of all schemas, a table of all tables per schema with column counts and row counts, and detailed column definitions for each table.
3. WHEN the Full_Indexer completes database indexing, THE Code_Index SHALL ingest the database schema into the Knowledge_Base with tags `"code-index"`, `"database"`, and the schema name.
4. IF the database MCP is unavailable during indexing, THEN THE Code_Index SHALL log a warning, skip database indexing, and note in the database analysis file that the schema data may be stale.
5. WHEN an agent queries the Knowledge_Base for database-related information (e.g., "what columns does the customer table have"), THE Knowledge_Base SHALL return the indexed database schema data.

### Requirement 10: Configuration and Extensibility

**User Story:** As a developer, I want to configure which files and directories are indexed, so that I can exclude irrelevant files and include project-specific file types.

#### Acceptance Criteria

1. THE Code_Index SHALL read its configuration from a file at `.analysis/code-intelligence/index-config.json` containing: `includedExtensions` (array of file extensions to index), `excludedDirectories` (array of directory paths to skip), and `excludedFilePatterns` (array of glob patterns for files to skip).
2. WHEN `index-config.json` does not exist, THE Code_Index SHALL use default values: `includedExtensions` of `[".kt", ".java", ".ts", ".tsx", ".gradle.kts", ".yml", ".properties", ".xml", ".sql"]`, `excludedDirectories` of `["build", ".gradle", ".git", "node_modules", ".idea", ".kiro"]`, and `excludedFilePatterns` of `["*.generated.*", "*.min.*"]`.
3. WHEN `index-config.json` is modified, THE next indexing run SHALL use the updated configuration.
4. IF `index-config.json` contains invalid JSON, THEN THE Code_Index SHALL log an error with the parse failure details and fall back to default configuration values.
