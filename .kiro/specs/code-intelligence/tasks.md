# Implementation Plan: Code Intelligence System

## Overview

The Code Intelligence System is implemented as a set of **TypeScript scripts** under `.analysis/code-intelligence/scripts/`, a **slim steering file** at `.kiro/steering/code-intelligence.md`, and **Kiro hooks** that trigger scripts automatically. Core indexing logic (file scanning, SHA-256 hashing, AST parsing, JSON I/O, Markdown generation, incremental change detection) lives in TypeScript scripts. Agents handle MCP-only operations (KB ingestion, DB schema querying) since MCP tools are only accessible from agent context.

All output files are stored under `.analysis/code-intelligence/`. TypeScript scripts are under `.analysis/code-intelligence/scripts/`. Property-based tests use Vitest + fast-check under `.analysis/code-intelligence/scripts/tests/properties/`.

## Tasks

### Phase 1: Project Setup & Foundation

- [x] 1. Initialize TypeScript project and create foundational types, config loader, and default configuration
  - [x] 1.1 Initialize TypeScript project under `.analysis/code-intelligence/scripts/`
    - Create `package.json` with project name `code-intelligence-scripts`, type `module`
    - Install dependencies: `typescript`, `vitest`, `fast-check`, `ts-node`
    - Create `tsconfig.json` with strict mode, ES2022 target, Node module resolution
    - Create `src/` and `tests/properties/` directory structure
    - Verify the project compiles with `npx tsc --noEmit`
    - _Requirements: N/A (infrastructure)_

  - [x] 1.2 Create shared types (`src/types.ts`)
    - Define `IndexConfig` interface: `includedExtensions: string[]`, `excludedDirectories: string[]`, `excludedFilePatterns: string[]`
    - Define `FileEntry` interface: `contentHash: string`, `lastIndexedTimestamp: string`, `language: string`, `moduleName: string`, `indexingStatus: "success" | "parse_error" | "read_error"`
    - Define `IndexMetadata` interface: `version: string`, `lastFullIndexTimestamp: string | null`, `projectName: string | null`, `projectType: string | null`, `totalFiles: number`, `files: Record<string, FileEntry>`
    - Define `DetectionResult` interface: `projectType: string`, `primaryLanguage: string`, `framework: string | null`, `buildFile: string`
    - Define `Module` interface: `name: string`, `path: string`, `sourceDirectories: string[]`, `buildFile: string | null`, `language: string | null`
    - Define `ParseResult` interface: `filePath: string`, `language: string`, `moduleName: string`, `packageName: string`, `classes: ClassInfo[]`, `functions: FunctionInfo[]`, `imports: string[]`, `indexingStatus: "success" | "parse_error"`, `errorMessage: string | undefined`
    - Define `ClassInfo` interface: `name: string`, `visibility: string`, `superclass: string | undefined`, `interfaces: string[]`, `annotations: string[]`
    - Define `FunctionInfo` interface: `name: string`, `visibility: string`, `parameters: ParameterInfo[]`, `returnType: string`, `annotations: string[]`
    - Define `ParameterInfo` interface: `name: string`, `type: string`
    - Define `AnnotationRow` interface: `target: string`, `authorAgent: string`, `annotationType: string`, `content: string`, `timestamp: string`
    - Define `ModuleData` interface: module name, path, language, framework, dependencies, source file count, packages, classes, functions, patterns
    - Define `ProjectInfo` interface: `projectName: string`, `projectType: string`, `primaryLanguage: string`, `framework: string | null`
    - Define `ScannedFile` interface: `filePath: string`, `contentHash: string`, `language: string`
    - Define `IndexResult` interface: `totalFiles: number`, `totalModules: number`, `totalClasses: number`, `totalFunctions: number`, `parseErrors: number`, `elapsedMs: number`
    - Export all types
    - _Requirements: 1.1, 1.2, 3.1, 3.2, 4.3, 9.2_

  - [x] 1.3 Create `src/config-loader.ts` — `loadConfig()` with default fallback
    - Implement `loadConfig(): IndexConfig` function
    - Read `.analysis/code-intelligence/index-config.json` from the file system using Node.js `fs` module
    - If file doesn't exist → return default config (silent, no error log)
    - If JSON is invalid → log error in `[Code-Index] ERROR` format, return default config
    - Default config includes all extensions from design: `.kt`, `.java`, `.ts`, `.tsx`, `.js`, `.jsx`, `.py`, `.go`, `.rs`, `.cs`, `.gradle.kts`, `.gradle`, `.yml`, `.yaml`, `.properties`, `.xml`, `.json`, `.sql`, `.toml`, `.cfg`, `.ini`
    - Default excluded directories: `build`, `dist`, `out`, `target`, `.gradle`, `.git`, `node_modules`, `.idea`, `.kiro`, `.vscode`, `__pycache__`, `.mypy_cache`, `vendor`, `bin`, `obj`
    - Default excluded patterns: `*.generated.*`, `*.min.*`, `*.map`, `*.lock`, `*.sum`
    - Export `loadConfig` and `DEFAULT_CONFIG`
    - _Requirements: 10.1, 10.2, 10.3, 10.4_

  - [x] 1.4 Create `index-config.json` with default configuration
    - Write `.analysis/code-intelligence/index-config.json` with the default configuration values matching the design document
    - Ensure JSON is valid and properly formatted (2-space indentation)
    - _Requirements: 10.1, 10.2_

  - [x] 1.5 Write property tests for config (P16: Round-Trip, P17: Invalid Fallback)
    - Create `.analysis/code-intelligence/scripts/tests/properties/config.property.test.ts`
    - **Property 16: Configuration Round-Trip** — Generate random valid configs (arrays of extensions, directories, patterns), serialize to JSON, write to file, read back via `loadConfig()`, verify equality
      - **Validates: Requirements 10.1**
    - **Property 17: Invalid Configuration Fallback** — Generate random non-JSON strings, write to config file, call `loadConfig()`, verify default config returned without error
      - **Validates: Requirements 10.4**
    - Use fast-check with minimum 100 iterations per property
    - Tag format: `Feature: code-intelligence, Property {N}: {property-text}`
    - _Requirements: 10.1, 10.4_


### Phase 2: Project Detection & Module Discovery

- [x] 2. Implement project auto-detection and module discovery as TypeScript scripts
  - [x] 2.1 Create `src/project-detector.ts` — `detectProjectType()` with build file scanning, language detection, framework detection
    - Implement `detectProjectType(rootDir: string): DetectionResult` function
    - Scan root directory for build files in priority order: `build.gradle.kts`, `build.gradle`, `pom.xml`, `package.json`, `Cargo.toml`, `go.mod`, `pyproject.toml`, `setup.py`, `*.sln`, `*.csproj`
    - Map detected build file to project type: `gradle-kotlin`, `gradle-java`, `maven-java`, `npm-typescript`, `npm-javascript`, `cargo-rust`, `go-module`, `python`, `dotnet`, `generic`
    - For `package.json`: check for `tsconfig.json` or `.ts`/`.tsx` files to distinguish TypeScript vs JavaScript
    - Detect primary language by counting source code file extensions (`.kt`, `.java`, `.ts`, `.tsx`, `.js`, `.jsx`, `.py`, `.go`, `.rs`, `.cs`)
    - Detect framework from dependency declarations in build files using the framework detection rules from the design (Spring Boot, React, Express.js, etc.)
    - Log detection result: `[Code-Index] INFO: Project detected — type={projectType}, language={primaryLanguage}, framework={framework}, buildFile={buildFile}`
    - Return `DetectionResult` object
    - _Requirements: 1.1_

  - [x] 2.2 Create `src/module-discovery.ts` — `discoverModules()` with build-system-specific parsing
    - Implement `discoverModules(rootDir: string, detectionResult: DetectionResult): Module[]` function
    - **Gradle**: Parse `settings.gradle.kts` / `settings.gradle` for `include()` statements, extract subproject names, validate directories exist
    - **Maven**: Parse root `pom.xml` for `<modules>` section, extract child module names
    - **npm**: Parse `package.json` for `workspaces` field (array or object form), resolve glob patterns to directories
    - **Cargo**: Parse `Cargo.toml` for `[workspace]` members array, resolve glob patterns
    - **Go**: Single module per `go.mod`, check for `go.work` for workspace support
    - **.NET**: Parse `*.sln` for project references, fallback to scanning for `*.csproj` files
    - **Python**: Check for monorepo indicators (`packages/`, `libs/`), fallback to single module
    - **Generic**: Return single `root` module with detected source directories
    - For each module, detect source directories using the source directory detection rules from the design (Gradle: `src/main/kotlin`, `src/main/java`, etc.; npm: `src`, `lib`, `app`; etc.)
    - Flat project fallback: if no modules detected, create single `root` module
    - Log discovery results: `[Code-Index] INFO: Module discovery — found {count} module(s) for {projectType}`
    - _Requirements: 1.1, 3.1_

  - [x] 2.3 Write unit tests for project detection and module discovery
    - Create `.analysis/code-intelligence/scripts/tests/project-detector.test.ts`
    - Test detection of each project type (Gradle, Maven, npm, Cargo, Go, Python, .NET, generic)
    - Test TypeScript vs JavaScript distinction for npm projects
    - Test framework detection for major frameworks (Spring Boot, React, Express.js, etc.)
    - Test primary language detection from file extension counts
    - Create `.analysis/code-intelligence/scripts/tests/module-discovery.test.ts`
    - Test Gradle module discovery with `include()` statements (single, multi-argument, colon-prefixed)
    - Test Maven module discovery with `<modules>` section
    - Test npm workspace discovery (array form, object form, glob patterns)
    - Test flat project fallback when no modules detected
    - Test source directory detection for each build system
    - _Requirements: 1.1, 3.1_


### Phase 3: File Scanning & Parsing

- [x] 3. Implement file scanning, hashing, and parsing as TypeScript scripts
  - [x] 3.1 Create `src/file-scanner.ts` — `scanFiles()`, `computeHash()`, `filterFile()`
    - Implement `scanFiles(config: IndexConfig, sourceDirectories: string[]): ScannedFile[]`
    - Recursively walk source directories using Node.js `fs` module
    - Implement `filterFile(filePath: string, config: IndexConfig): boolean` — returns `true` if file should be indexed
    - Filter by `includedExtensions` (file extension must be in the list)
    - Filter by `excludedDirectories` (file path must not traverse any excluded directory)
    - Filter by `excludedFilePatterns` (file name must not match any glob pattern)
    - Implement `computeHash(filePath: string): string` — returns `"sha256:..."` prefixed hash using Node.js `crypto` module
    - Map file extension to language using the extension → language mapping from the design
    - Return array of `ScannedFile` objects with `filePath`, `contentHash`, `language`
    - _Requirements: 1.1, 5.4, 5.7, 10.1_

  - [x] 3.2 Create `src/file-parser.ts` — `parseFile()` with TS Compiler API for TS/JS, regex for other languages
    - Implement `parseFile(filePath: string, language: string): ParseResult` function
    - **TypeScript/JavaScript** (`.ts`, `.tsx`, `.js`, `.jsx`): Use TypeScript Compiler API for AST-based extraction of class signatures, function signatures, imports, package/module name
    - **Kotlin** (`.kt`): Regex-based extraction of `class`, `object`, `fun`, `import`, `package` declarations
    - **Java** (`.java`): Regex-based extraction of `class`, `interface`, `enum`, method signatures, `import`, `package` declarations
    - **Python** (`.py`): Regex-based extraction of `class`, `def`, `import`/`from...import` statements
    - **Go** (`.go`): Regex-based extraction of `type`, `func`, `import`, `package` declarations
    - **Rust** (`.rs`): Regex-based extraction of `struct`, `enum`, `fn`, `impl`, `use`, `mod` declarations
    - **C#** (`.cs`): Regex-based extraction of `class`, `interface`, method signatures, `using`, `namespace` declarations
    - **Configuration files** (`.yml`, `.yaml`, `.xml`, `.json`, `.properties`, `.cfg`, `.ini`): Regex-based extraction of top-level keys/structure
    - **SQL** (`.sql`): Extract table/view definitions and stored procedure signatures
    - **Unsupported languages**: Fall back to basic line-counting and import/require extraction
    - Handle parse errors gracefully: catch all errors per file, record `indexingStatus: "parse_error"` with human-readable error message
    - Auto-detect module name from file path relative to project structure
    - Return structured `ParseResult` with all extracted data
    - _Requirements: 1.1, 1.7_

  - [x] 3.3 Write property test for File Path Filtering (P13)
    - Create or update `.analysis/code-intelligence/scripts/tests/properties/filter.property.test.ts`
    - **Property 13: File Path Filtering** — Generate random file paths with various extensions and directory structures, verify `filterFile()` includes/excludes correctly based on config: (a) extension in `includedExtensions`, AND (b) path does not traverse `excludedDirectories`, AND (c) name does not match `excludedFilePatterns`
    - Use fast-check with minimum 100 iterations
    - **Validates: Requirements 5.4, 5.7, 10.1**

  - [x] 3.4 Write property test for Parse Error Isolation (P4)
    - Create or update `.analysis/code-intelligence/scripts/tests/properties/indexer.property.test.ts`
    - **Property 4: Parse Error Isolation** — Generate file sets with random mix of valid/invalid syntax, run `parseFile()` on each, verify error files get `parse_error` status while valid files get `success`, and counts add up
    - Use fast-check with minimum 100 iterations
    - **Validates: Requirements 1.7**


### Phase 4: Full Indexer & Analysis Generation

- [x] 4. Implement analysis generation, full indexer orchestration, and metadata helpers
  - [x] 4.1 Create `src/analysis-generator.ts` — `generateProjectStructure()`, `generateModuleAnalysis()`
    - Implement `generateProjectStructure(modules: ModuleData[], projectInfo: ProjectInfo): void`
    - Generate `.analysis/code-intelligence/project-structure.md` with "Last Updated" UTC timestamp, project name, project type
    - Generate Modules table with columns: Module, Purpose, Language, Framework, Key Dependencies, Source Files count
    - Generate Inter-Module Dependencies table showing which modules depend on which (detected from imports/build file dependencies)
    - Infer module purpose from README files, package names, or class names
    - Implement `generateModuleAnalysis(module: ModuleData, annotations: AnnotationRow[]): void`
    - Generate per-module files at `.analysis/code-intelligence/modules/{module-name}.md`
    - Include "Last Updated" timestamp, language, and framework
    - Generate Package Structure section as a tree
    - Generate Key Classes table with columns: Class, Package, Responsibility, Visibility
    - Generate Public API Surface section listing controller/handler endpoints with function signatures
    - Generate Dependencies section showing imports from other modules
    - Generate Detected Patterns section: DI style, error handling, naming conventions, logging, testing
    - Include empty `## Annotations` section with table header (Target, Author Agent, Type, Content, Timestamp)
    - Write files atomically (temp file + rename)
    - _Requirements: 3.1, 3.2, 3.4_

  - [x] 4.2 Create `src/full-indexer.ts` — `runFullIndex()` orchestrating all components
    - Implement `runFullIndex(config: IndexConfig): IndexResult` function
    - Delete existing `index-metadata.json` before starting
    - Run project type auto-detection via `project-detector.ts`
    - Run module discovery via `module-discovery.ts`
    - For each module, scan source files via `file-scanner.ts` and parse each one via `file-parser.ts`
    - Collect all parse results into the index
    - Write `index-metadata.json` atomically (write to `.tmp`, then rename)
    - Generate `project-structure.md` and per-module `modules/{module-name}.md` via `analysis-generator.ts`
    - Output structured JSON for KB ingestion (stdout or file) for agents to ingest via MCP tools
    - Display progress indicator showing files indexed out of total
    - Return summary: total files, modules, classes, functions, parse errors, elapsed time
    - _Requirements: 1.1, 1.2, 1.5, 7.1, 7.2, 7.3, 7.4_

  - [x] 4.3 Create index-metadata read/write helpers with atomic write strategy
    - Implement `readMetadata(): IndexMetadata | null` in a shared utility (e.g., `src/metadata-helpers.ts`)
    - Handle missing file → return `null` (triggers full re-index)
    - Handle corrupted JSON → log error, delete corrupted file, return `null`
    - Validate structure: check `version` and `files` fields
    - Implement `writeMetadata(metadata: IndexMetadata): void`
    - Serialize to formatted JSON (2-space indentation)
    - Write to `index-metadata.json.tmp` first, then rename to final path (atomic write)
    - Ensure `totalFiles` matches the number of keys in the `files` map
    - _Requirements: 1.2, 7.4_

  - [x] 4.4 Write property test for Full Index Completeness (P1)
    - Create or update `.analysis/code-intelligence/scripts/tests/properties/indexer.property.test.ts`
    - **Property 1: Full Index Completeness** — Generate random file trees (1–100 files, various extensions, multiple modules), run full index, verify every matching file has complete index entry (file path, language, module name, package name, class signatures, function signatures, imports, timestamp) AND a corresponding entry in `index-metadata.json` (relative file path, valid SHA-256 content hash, last-indexed UTC timestamp)
    - Use fast-check with minimum 100 iterations
    - **Validates: Requirements 1.1, 1.2**

  - [x] 4.5 Write property test for Project Structure Completeness (P7)
    - Create or update `.analysis/code-intelligence/scripts/tests/properties/analysis.property.test.ts`
    - **Property 7: Project Structure Completeness** — Generate random module sets (1–15 modules), run `generateProjectStructure()`, verify `project-structure.md` has exactly one row per module with all required columns filled (purpose, language, framework, key dependencies, source file count). No module missing, no extra rows.
    - Use fast-check with minimum 100 iterations
    - **Validates: Requirements 3.1**

  - [x] 4.6 Write property test for Module Analysis Completeness (P8)
    - Create or update `.analysis/code-intelligence/scripts/tests/properties/analysis.property.test.ts`
    - **Property 8: Module Analysis Completeness** — Generate random module with varying classes/functions/packages, run `generateModuleAnalysis()`, verify all required sections present and non-empty: package structure tree, key classes, public API surface, dependencies, detected patterns, annotations section
    - Use fast-check with minimum 100 iterations
    - **Validates: Requirements 3.2**

  - [x] 4.7 Write property test for Index Summary Completeness (P14)
    - Create or update `.analysis/code-intelligence/scripts/tests/properties/indexer.property.test.ts`
    - **Property 14: Index Summary Completeness** — Generate random indexing results, verify summary includes all required fields: total files indexed, total modules found, total classes found, total functions found, number of parse errors, and elapsed time. No field missing or null.
    - Use fast-check with minimum 100 iterations
    - **Validates: Requirements 7.3**


### Phase 5: Checkpoint — Verify full indexing pipeline

- [x] 5. Run full indexer on current workspace, verify outputs
  - Run the full indexer script on the current workspace as a real-world test
  - Verify `index-metadata.json` is generated with correct structure and all indexed files
  - Verify `project-structure.md` is generated with correct module table
  - Verify per-module `modules/{module-name}.md` files are generated with all required sections
  - Verify all property tests pass (`npx vitest run`)
  - Ask the user if questions arise

### Phase 6: Incremental Indexer

- [ ] 6. Implement incremental indexer with change detection as a TypeScript script
  - [-] 6.1 Create `src/incremental-indexer.ts` — `runIncrementalIndex()` with change detection
    - Implement `runIncrementalIndex(config: IndexConfig, changedFiles?: string[]): IndexResult` function
    - Read existing `index-metadata.json` via `readMetadata()`
    - If `changedFiles` provided (hook-triggered): process only those files
    - If `changedFiles` is undefined (agent on-demand): scan all files via `file-scanner.ts`, compute current hashes, compare against stored hashes in metadata
    - Identify files that are: modified (hash differs), new (not in metadata), or deleted (in metadata but not on disk)
    - For modified files: re-parse via `file-parser.ts` and update index entry and metadata hash
    - For new files: parse and add new index entry and metadata entry
    - For deleted files: remove index entry and metadata entry, mark annotations as `[DELETED]` via `annotation-manager.ts`
    - Regenerate only the affected module's Analysis File via `analysis-generator.ts` (not all modules)
    - Update only the affected row in `project-structure.md`
    - Output structured JSON for KB update (agents update via MCP tools, not creating duplicates)
    - Write updated `index-metadata.json` atomically
    - _Requirements: 1.3, 1.4, 1.6, 3.3_

  - [~] 6.2 Write property test for Incremental Change Detection (P2)
    - Create or update `.analysis/code-intelligence/scripts/tests/properties/indexer.property.test.ts`
    - **Property 2: Incremental Change Detection Accuracy** — Generate indexed file set, randomly modify subset (change content → different hash), run `runIncrementalIndex()`, verify only modified files are re-indexed (no more, no less). Files with unchanged content retain existing index entries unmodified.
    - Use fast-check with minimum 100 iterations
    - **Validates: Requirements 1.3**

  - [~] 6.3 Write property test for Incremental Deletion Cleanup (P3)
    - Create or update `.analysis/code-intelligence/scripts/tests/properties/indexer.property.test.ts`
    - **Property 3: Incremental Deletion Cleanup** — Generate indexed file set, randomly delete subset, run `runIncrementalIndex()`, verify deleted files absent from both Code Index and Index Metadata. All non-deleted files retain their existing index entries.
    - Use fast-check with minimum 100 iterations
    - **Validates: Requirements 1.4**

  - [~] 6.4 Write property test for Incremental Scope Isolation (P9)
    - Create or update `.analysis/code-intelligence/scripts/tests/properties/indexer.property.test.ts`
    - **Property 9: Incremental Scope Isolation** — Generate multi-module project, change files in one random module, run `runIncrementalIndex()`, verify other modules' Analysis Files remain byte-identical to their state before the incremental run.
    - Use fast-check with minimum 100 iterations
    - **Validates: Requirements 3.3**


### Phase 7: Semantic Enrichment

- [ ] 7. Implement annotation manager as a TypeScript script
  - [~] 7.1 Create `src/annotation-manager.ts` — `addAnnotation()`, `preserveAnnotations()`
    - Implement `addAnnotation(moduleName: string, target: string, authorAgent: string, annotationType: string, content: string): void`
    - Accept annotation types: `requirement-link`, `design-decision`, `implementation-note`, `known-issue`, `todo`
    - Read the target module's Analysis File at `.analysis/code-intelligence/modules/{moduleName}.md`
    - Append a new row to the `## Annotations` Markdown table with: Target, Author Agent, Annotation Type, Content, UTC Timestamp
    - Output structured JSON for KB ingestion with tags `semantic-annotation`, module name, annotation type
    - Implement `preserveAnnotations(existingAnalysisFile: string, currentTargets: string[]): AnnotationRow[]`
    - Read existing annotations from the `## Annotations` section of the analysis file
    - Return all annotation rows for inclusion in regenerated output
    - If a referenced class/function no longer exists in `currentTargets`, update the Target column to `[DELETED] {original-target-name}`
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6_

  - [~] 7.2 Write property test for Annotation Format (P10)
    - Create `.analysis/code-intelligence/scripts/tests/properties/annotation.property.test.ts`
    - **Property 10: Annotation Format Correctness** — Generate random annotations (all types, various content lengths, special characters), run `addAnnotation()`, verify Markdown table row has exactly five columns: Target, Author Agent, Annotation Type, Content, Timestamp. The row is parseable back into its constituent fields.
    - Use fast-check with minimum 100 iterations
    - **Validates: Requirements 4.3**

  - [~] 7.3 Write property test for Annotation Preservation (P11)
    - Create or update `.analysis/code-intelligence/scripts/tests/properties/annotation.property.test.ts`
    - **Property 11: Annotation Preservation on Regeneration** — Generate random annotations, regenerate analysis file due to code change via `preserveAnnotations()`, verify all annotations preserved exactly: same target, same author, same type, same content, same timestamp. No annotation lost or modified.
    - Use fast-check with minimum 100 iterations
    - **Validates: Requirements 4.4**

  - [~] 7.4 Write property test for Deleted Target Marking (P12)
    - Create or update `.analysis/code-intelligence/scripts/tests/properties/annotation.property.test.ts`
    - **Property 12: Deleted Target Annotation Marking** — Generate annotations referencing targets, delete random targets from `currentTargets`, run `preserveAnnotations()`, verify `[DELETED] {original-target-name}` marking applied without removing annotations. Annotations for existing targets remain unchanged.
    - Use fast-check with minimum 100 iterations
    - **Validates: Requirements 4.5**


### Phase 8: Database Schema Indexer

- [ ] 8. Implement database schema indexer as a TypeScript script
  - [~] 8.1 Create `src/db-schema-indexer.ts` — `generateDatabaseSchemaMarkdown()`, `generateKbIngestJson()`
    - Implement `generateDatabaseSchemaMarkdown(schemaData: DatabaseSchema): void`
    - Accept schema data (provided by agent after querying via MCP tools: `mcp_database_mcp_list_schemas`, `mcp_database_mcp_list_objects`, `mcp_database_mcp_get_object_details`)
    - Generate `.analysis/code-intelligence/database-schema.md` with "Last Updated" timestamp
    - Generate Data Sources table, Schemas table (schema name, table count, description), Tables table per schema (table name, column count, approximate row count, description)
    - Generate detailed Table Details sections with column definitions (name, type, nullable, default, description)
    - If schema data is empty or stale → note staleness in the analysis file
    - Implement `generateKbIngestJson(schemaData: DatabaseSchema): object` — output structured JSON for KB ingestion with tags `code-index`, `database`, schema name
    - _Requirements: 9.1, 9.2, 9.3, 9.4_

  - [~] 8.2 Write property test for Database Analysis Completeness (P15)
    - Create `.analysis/code-intelligence/scripts/tests/properties/db-schema.property.test.ts`
    - **Property 15: Database Analysis Completeness** — Generate random schema structures (schemas, tables, columns with name/type/nullable/default), run `generateDatabaseSchemaMarkdown()`, verify analysis file contains: a row in schemas table for each schema, a row in tables table for each table (with column count and approximate row count), and a detailed column definition section for each table.
    - Use fast-check with minimum 100 iterations
    - **Validates: Requirements 9.2**

### Phase 9: Knowledge Base Integration

- [ ] 9. Implement KB ingestion helpers and tagging/deduplication logic
  - [~] 9.1 Write KB ingestion helper functions (output JSON for agent to ingest)
    - Create `src/kb-helpers.ts` with functions to generate structured JSON for KB ingestion
    - Implement `generateModuleKbPayload(module: ModuleData, projectName: string): KbIngestPayload` — title: `Code Index — {module-name}`, content: module summary, tags: `code-index, {module-name}, {language}`, project: `{projectName}`
    - Implement `generateAnnotationKbPayload(annotation: AnnotationRow, moduleName: string, projectName: string): KbIngestPayload` — title: `Annotation — {target} — {annotation-type}`, tags: `semantic-annotation, {module-name}, {annotation-type}`, project: `{projectName}`
    - Implement `generateSchemaKbPayload(schemaName: string, schemaContent: string, projectName: string): KbIngestPayload` — title: `Database Schema — {schema-name}`, tags: `code-index, database, {schema-name}`, project: `{projectName}`
    - Output JSON to stdout or file for agents to read and ingest via MCP tools
    - _Requirements: 2.1, 2.2, 2.5, 4.6, 9.3_

  - [~] 9.2 Write property test for KB Tagging Correctness (P5)
    - Create `.analysis/code-intelligence/scripts/tests/properties/kb.property.test.ts`
    - **Property 5: Knowledge Base Tagging Correctness** — Generate random module names, languages, annotation types, schema names, run KB payload generators, verify tag arrays match the required format: modules get `["code-index", {module-name}, {language}]`, annotations get `["semantic-annotation", {module-name}, {annotation-type}]`, database schemas get `["code-index", "database", {schema-name}]`. The `project` field is always set to the workspace name.
    - Use fast-check with minimum 100 iterations
    - **Validates: Requirements 2.2, 4.6, 9.3**

  - [~] 9.3 Write property test for KB Deduplication (P6)
    - Create or update `.analysis/code-intelligence/scripts/tests/properties/kb.property.test.ts`
    - **Property 6: Knowledge Base Deduplication on Update** — Generate random module, produce KB payload twice with different content, verify the payload uses the same title/key (module name) so that agents can search-then-update rather than creating duplicates. Verify single KB entry would exist (not duplicated) when using module name as unique key.
    - Use fast-check with minimum 100 iterations
    - **Validates: Requirements 2.4**


### Phase 10: Checkpoint — Verify incremental indexing, DB schema, annotations

- [~] 10. Test incremental indexing, DB schema indexer, and annotation manager
  - Test incremental indexing by modifying a file and verifying only the affected module is updated
  - Test database schema indexer generates correct Markdown from sample schema data
  - Test annotation creation and preservation through re-indexing
  - Ensure all property tests pass (`npx vitest run`)
  - Ask the user if questions arise

### Phase 11: Hooks & Steering

- [ ] 11. Create Kiro hooks and slim steering file
  - [~] 11.1 Create `code-index-edit` hook (fileEdited → runCommand: incremental indexer)
    - Event: `fileEdited`
    - File patterns: `**/*.kt, **/*.java, **/*.ts, **/*.tsx, **/*.js, **/*.jsx, **/*.py, **/*.go, **/*.rs, **/*.cs, **/*.gradle.kts, **/*.gradle, **/*.yml, **/*.yaml, **/*.properties, **/*.xml, **/*.sql, **/*.json, **/*.toml`
    - Action: `runCommand` — Execute `npx ts-node .analysis/code-intelligence/scripts/src/incremental-indexer.ts --files {changedFile}` (or equivalent compiled JS entry point)
    - The script reads config, checks file against exclusions, runs incremental index for the specific file
    - _Requirements: 5.1, 5.4, 5.5, 5.6, 5.7_

  - [~] 11.2 Create `code-index-create` hook (fileCreated → runCommand: incremental indexer)
    - Event: `fileCreated`
    - File patterns: same as 11.1
    - Action: `runCommand` — Execute incremental indexer script to add the new file to the index
    - _Requirements: 5.2, 5.4, 5.7_

  - [~] 11.3 Create `code-index-delete` hook (fileDeleted → runCommand: incremental indexer)
    - Event: `fileDeleted`
    - File patterns: same as 11.1
    - Action: `runCommand` — Execute incremental indexer script to remove the file from the index, update affected module analysis, mark annotations as `[DELETED]`
    - _Requirements: 5.3, 5.4, 5.7_

  - [~] 11.4 Create `code-index-full` hook (userTriggered → askAgent: full indexer + KB ingestion)
    - Event: `userTriggered`
    - Action: `askAgent` — Instruct agent to run full indexer script via `executePwsh`, read the script's JSON output, then ingest into KB via MCP tools (`mcp_knowledge_base_kb_ingest`). Report summary when complete.
    - Uses `askAgent` instead of `runCommand` because full index requires KB ingestion via MCP tools (only accessible from agent context)
    - _Requirements: 7.1, 7.2, 7.3_

  - [~] 11.5 Write slim steering file (`.kiro/steering/code-intelligence.md`)
    - Document WHEN to run which script and HOW to read script output
    - Include: how to execute scripts via `executePwsh` (e.g., `npx ts-node .analysis/code-intelligence/scripts/src/full-indexer.ts`)
    - Include: how to read script JSON output for KB ingestion
    - Include: KB ingestion instructions — use `mcp_knowledge_base_kb_search` to check for existing document, then `mcp_knowledge_base_kb_ingest` to create/update
    - Include: DB schema querying instructions — use `mcp_database_mcp_list_schemas`, `mcp_database_mcp_list_objects`, `mcp_database_mcp_get_object_details`, then pass results to `db-schema-indexer.ts`
    - Include: how to read Analysis Files directly via `readFile` for code intelligence consumption
    - Include: how to query KB via `mcp_knowledge_base_kb_search_smart` for semantic search
    - Include: error handling rules (parse errors, KB unavailability, DB MCP unavailability, corrupted metadata)
    - Include: logging format (`[Code-Index] ERROR/WARN/INFO`)
    - Keep it slim — all deterministic logic is in the TypeScript scripts, steering only tells agents WHEN and HOW
    - _Requirements: 1.1–1.7, 2.1–2.5, 3.1–3.6, 5.1–5.7, 7.1–7.4, 9.1–9.5, 10.1–10.4_


### Phase 12: Agent Integration

- [ ] 12. Integrate code intelligence into all agents
  - [~] 12.1 Update SA agent to consume code intelligence
    - Update `.kiro/agents/sa-agent.md` (or equivalent agent config)
    - Add instruction: before creating a TDD, run incremental indexer script if metadata is stale, then read `.analysis/code-intelligence/project-structure.md` and relevant module analysis files
    - Add instruction: retrieve from code intelligence — actual tech stack, existing API patterns, database access patterns, naming conventions
    - Add instruction: query KB with `mcp_knowledge_base_kb_search_smart` for code-related context
    - Add instruction: after creating TDD, write semantic annotations (requirement-links, design-decisions) to relevant module analysis files via `annotation-manager.ts`
    - _Requirements: 4.1, 6.1, 8.1, 8.4_

  - [~] 12.2 Update DEV agent to consume code intelligence
    - Update `.kiro/agents/dev-agent.md` (or equivalent agent config)
    - Add instruction: before implementing code, run incremental indexer script if metadata is stale, then read relevant module analysis files for package structure, naming conventions, DI style, error handling patterns, logging patterns, test patterns
    - Add instruction: query KB for existing patterns in the target module
    - Add instruction: after completing implementation, write semantic annotations (implementation-notes, known-issues, TODOs) to relevant module analysis files via `annotation-manager.ts`
    - _Requirements: 4.2, 6.2, 8.2, 8.4_

  - [~] 12.3 Update QA agent to consume code intelligence
    - Update `.kiro/agents/qa-agent.md` (or equivalent agent config)
    - Add instruction: before creating test plans, read relevant module analysis files for testable components (controllers, services, repositories), existing test frameworks and patterns, public API surface
    - Add instruction: query KB for code intelligence related to the target module
    - _Requirements: 8.3, 8.4_

  - [~] 12.4 Update BA and DevOps agents to consume code intelligence
    - Update `.kiro/agents/ba-agent.md`: add instruction to read `project-structure.md` for high-level project understanding, query KB for code-related context when analyzing requirements
    - Update `.kiro/agents/devops-agent.md`: add instruction to read `project-structure.md` and module analysis files for deployment context, read `database-schema.md` for database migration context
    - _Requirements: 8.4_

### Phase 13: Module Lifecycle

- [ ] 13. Implement new module detection and removed module cleanup
  - [~] 13.1 Implement new module detection and indexing
    - During full or incremental index, detect new directories containing build files (e.g., new `build.gradle.kts` not previously in metadata)
    - Create a new Module Analysis File for the new module via `analysis-generator.ts`
    - Add a new row to `project-structure.md`
    - Output KB ingestion JSON for the new module
    - _Requirements: 3.5_

  - [~] 13.2 Implement removed module cleanup
    - During full index, detect modules present in metadata but absent from disk (directory no longer exists or build file removed)
    - Delete the corresponding Module Analysis File at `.analysis/code-intelligence/modules/{module-name}.md`
    - Remove the row from `project-structure.md`
    - Output KB cleanup JSON to mark/remove KB entries for the removed module
    - _Requirements: 3.6_


### Phase 14: Final Integration & E2E Validation

- [ ] 14. Run final integration tests and end-to-end validation
  - [~] 14.1 Run full E2E indexing on current workspace
    - Trigger the full indexer script on the current workspace
    - Verify all outputs: `index-metadata.json`, `project-structure.md`, all module analysis files, `database-schema.md`
    - Verify KB ingestion JSON output contains all expected documents with correct tags
    - Verify summary report includes all required fields (total files, modules, classes, functions, parse errors, elapsed time)
    - _Requirements: 1.1–1.7, 2.1–2.2, 3.1–3.2, 7.1–7.3, 9.1–9.3_

  - [~] 14.2 Test incremental indexing E2E
    - Modify a source file → run incremental indexer → verify only affected module re-indexed
    - Create a new source file → run incremental indexer → verify it's added to index
    - Delete a source file → run incremental indexer → verify it's removed from index and annotations marked `[DELETED]`
    - Verify KB update JSON output (not duplicated) after each incremental run
    - _Requirements: 1.3, 1.4, 2.4, 3.3, 4.4, 4.5_

  - [~] 14.3 Test agent consumption E2E
    - Verify SA agent can read code intelligence (project-structure.md, module analysis files) and use it in TDD creation
    - Verify DEV agent can read code intelligence and use it during implementation
    - Verify QA agent can read code intelligence and use it in test planning
    - Verify KB search returns relevant code intelligence results via `mcp_knowledge_base_kb_search_smart`
    - _Requirements: 8.1–8.5_

  - [~] 14.4 Final checkpoint — all tests pass
    - Run all property tests: `npx vitest run` in `.analysis/code-intelligence/scripts/`
    - Verify all 17 property tests pass
    - Verify all unit tests pass
    - Verify all hooks fire correctly for their respective events
    - Verify all requirements are covered by implementation
    - Confirm all Analysis Files, KB ingestion JSON, and hooks are functioning correctly
    - Ask the user if questions arise

## Notes

- All TypeScript scripts live under `.analysis/code-intelligence/scripts/src/`
- All property-based tests live under `.analysis/code-intelligence/scripts/tests/properties/`
- Scripts use Node.js built-in modules (`fs`, `path`, `crypto`) — no external runtime dependencies beyond TypeScript tooling
- Testing uses Vitest + fast-check with minimum 100 iterations per property
- Hooks use `runCommand` to execute scripts directly for file events (edit/create/delete)
- The `code-index-full` hook uses `askAgent` because full index requires KB ingestion via MCP tools (agent context only)
- Agents handle MCP-only operations: KB ingestion (`mcp_knowledge_base_kb_ingest`), KB search (`mcp_knowledge_base_kb_search_smart`), DB schema querying (`mcp_database_mcp_*`)
- The slim steering file tells agents WHEN to run scripts and HOW to read output — all deterministic logic is in TypeScript
- Each task references specific requirements for traceability
- Checkpoints (Phase 5, 10, 14) ensure incremental validation at key milestones
- Property tests validate universal correctness properties from the design document (P1–P17)
- The system is project-agnostic — all project type detection and module discovery is automatic
- All output files go to `.analysis/code-intelligence/` (not `documents/` or `.kiro/`)
- Atomic write strategy (temp file + rename) prevents metadata corruption from interrupted writes