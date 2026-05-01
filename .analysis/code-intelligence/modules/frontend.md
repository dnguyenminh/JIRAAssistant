# Module Analysis — frontend

**Last Updated:** 2026-04-30T10:44:27.000Z
**Language:** javascript | **Framework:** —

## Package Structure

```
frontend/
├── com.assistant.frontend.api/     # HTTP request handling
├── com.assistant.frontend/     # Application logic
├── com.assistant.frontend.components/     # Application logic
├── com.assistant.frontend.components.chat/     # Application logic
├── com.assistant.frontend.models/     # Domain model
├── com.assistant.frontend.pages.analysis/     # Application logic
├── com.assistant.frontend.pages/     # Application logic
├── com.assistant.frontend.pages.dashboard/     # Application logic
├── com.assistant.frontend.pages.graph/     # Application logic
├── com.assistant.frontend.pages.integrations/     # Application logic
├── com.assistant.frontend.pages.settings/     # Application logic
├── com.assistant.frontend.pages.ticket/     # Application logic
├── com.assistant.frontend.pages.ticket.drawio/     # Application logic
├── com.assistant.frontend.pages.usermgmt/     # Application logic
├── com.assistant.frontend.router/     # Application logic
├── com.assistant.frontend.services/     # Business logic
├── projects.kotlin.JIRAAssitantNewSollution.frontend/     # Application logic
└── projects.kotlin.JIRAAssitantNewSollution.frontend.webpack.config.d/     # Configuration
```

## Key Classes

| Class | Package | Responsibility | Visibility |
|-------|---------|---------------|------------|
| name | com.assistant.frontend.api | Application component | public |
| version | com.assistant.frontend.api | Application component | public |
| lockfileVersion | com.assistant.frontend.api | Application component | public |
| requires | com.assistant.frontend.api | Application component | public |
| packages | com.assistant.frontend.api | Application component | public |
| name | com.assistant.frontend.api | Application component | public |
| version | com.assistant.frontend.api | Application component | public |
| private | com.assistant.frontend.api | Application component | public |
| scripts | com.assistant.frontend.api | Application component | public |
| dependencies | com.assistant.frontend.api | Application component | public |
| devDependencies | com.assistant.frontend.api | Application component | public |
| ApiClient | com.assistant.frontend.api | External service client | public |
| TokenManager | com.assistant.frontend.api | Application component | internal |
| AppStartup | com.assistant.frontend.api | Application component | internal |
| AIChatSidebar | com.assistant.frontend.api | Application component | public |
| BlockingOverlay | com.assistant.frontend.api | Application component | public |
| AIConfigPanel | projects.kotlin.JIRAAssitantNewSollution.frontend.webpack.config.d | Application component | public |
| AIConfigPopupForm | projects.kotlin.JIRAAssitantNewSollution.frontend.webpack.config.d | Application component | public |
| AIConfigPopupFormExtra | projects.kotlin.JIRAAssitantNewSollution.frontend.webpack.config.d | Application component | public |
| AIConfigSummaryTable | projects.kotlin.JIRAAssitantNewSollution.frontend.webpack.config.d | Application component | public |
| AIConfigTableBuilder | projects.kotlin.JIRAAssitantNewSollution.frontend.webpack.config.d | Application component | public |
| ChatActionHandler | com.assistant.frontend.api | HTTP request handling | internal |
| ChatCommandHistory | com.assistant.frontend.api | Application component | internal |
| ChatGraphContextBuilder | com.assistant.frontend.api | Application component | internal |
| ChatMessageRenderer | com.assistant.frontend.api | Application component | internal |
| ChatTicketContextBuilder | com.assistant.frontend.api | Application component | internal |
| ChatToolPermissions | com.assistant.frontend.api | Application component | public |
| ClipboardHandler | com.assistant.frontend.api | HTTP request handling | public |
| ContextIndicator | com.assistant.frontend.api | Application component | public |
| ConversationList | com.assistant.frontend.api | Application component | public |
| FileUploader | com.assistant.frontend.api | Application component | public |
| UploadedFile | com.assistant.frontend.api | Application component | data |
| GraphActionHandler | com.assistant.frontend.api | HTTP request handling | internal |
| McpToolIndicator | com.assistant.frontend.api | Application component | public |
| McpToolsSection | com.assistant.frontend.api | Application component | public |
| ToolInfo | com.assistant.frontend.api | Application component | data |
| ToolAutocomplete | com.assistant.frontend.api | Application component | public |
| ToolPicker | com.assistant.frontend.api | Application component | public |
| ToolEntry | com.assistant.frontend.api | Application component | data |
| VoiceInput | com.assistant.frontend.api | Application component | public |
| GlobalJobIndicator | com.assistant.frontend.api | Application component | public |
| GlobalJobIndicatorPanel | com.assistant.frontend.api | Application component | public |
| Navbar | com.assistant.frontend.api | Application component | public |
| NavbarDropdown | com.assistant.frontend.api | Application component | internal |
| Shell | com.assistant.frontend.api | Application component | public |
| Sidebar | com.assistant.frontend.api | Application component | public |
| NavItem | com.assistant.frontend.api | Application component | data |
| ProjectAnalysisResponse | com.assistant.frontend.api | Data transfer object | data |
| SprintVelocity | com.assistant.frontend.api | Application component | data |
| BottleneckAlert | com.assistant.frontend.api | Application component | data |
| ProviderStatusInfo | com.assistant.frontend.api | Application component | data |
| AttachmentStatusDTO | com.assistant.frontend.api | Data transfer object | data |
| CollectionJobResponse | com.assistant.frontend.api | Data transfer object | data |
| CollectionJobItemResponse | com.assistant.frontend.api | Data transfer object | data |
| DashboardAnalysis | com.assistant.frontend.api | Application component | data |
| VelocityPoint | com.assistant.frontend.api | Application component | data |
| BottleneckEntry | com.assistant.frontend.api | Application component | data |
| ProviderStatusEntry | com.assistant.frontend.api | Application component | data |
| ConsoleEntry | com.assistant.frontend.api | Application component | data |
| TicketTab | com.assistant.frontend.api | Application component | public |
| TicketPageState | com.assistant.frontend.api | Application component | data |
| ConfidenceDisplay | com.assistant.frontend.api | Application component | data |
| MetadataBadgeInfo | com.assistant.frontend.api | Application component | data |
| GeneratedDocumentMeta | com.assistant.frontend.api | Application component | data |
| GeneratedDocumentFull | com.assistant.frontend.api | Application component | data |
| DocumentGenerationStatus | com.assistant.frontend.api | Application component | data |
| GenerationJobDto | com.assistant.frontend.api | Data transfer object | data |
| VersionMeta | com.assistant.frontend.api | Application component | data |
| GraphFilters | com.assistant.frontend.api | Application component | data |
| GraphLayoutResponse | com.assistant.frontend.api | Data transfer object | data |
| NodeTypeInfo | com.assistant.frontend.api | Application component | data |
| GraphNode | com.assistant.frontend.api | Application component | data |
| GraphEdge | com.assistant.frontend.api | Application component | data |
| GraphCluster | com.assistant.frontend.api | Application component | data |
| ProviderInfo | com.assistant.frontend.api | Application component | data |
| OllamaModelsListResponse | com.assistant.frontend.models | Data transfer object | data |
| OllamaModelEntry | com.assistant.frontend.models | Application component | data |
| TestResult | com.assistant.frontend.api | Application component | data |
| ConfigUpdate | projects.kotlin.JIRAAssitantNewSollution.frontend.webpack.config.d | Application component | data |
| PriorityUpdate | com.assistant.frontend.api | Application component | data |
| JiraConfigRequest | projects.kotlin.JIRAAssitantNewSollution.frontend.webpack.config.d | Data transfer object | data |
| JiraProjectInfo | com.assistant.frontend.api | Application component | data |
| JiraConfigResponse | projects.kotlin.JIRAAssitantNewSollution.frontend.webpack.config.d | Data transfer object | data |
| McpServerInfo | com.assistant.frontend.api | Application component | data |
| McpProcessStatusDto | com.assistant.frontend.api | Data transfer object | data |
| McpToolInfoDto | com.assistant.frontend.api | Data transfer object | data |
| McpToolCallRequestDto | com.assistant.frontend.api | Data transfer object | data |
| McpToolCallResponseDto | com.assistant.frontend.api | Data transfer object | data |
| McpContentDto | com.assistant.frontend.api | Data transfer object | data |
| ModelInfoResponse | com.assistant.frontend.models | Data transfer object | data |
| NavScanStatusResponse | com.assistant.frontend.api | Data transfer object | data |
| ProjectsResponse | com.assistant.frontend.api | Data transfer object | data |
| ProjectInfo | com.assistant.frontend.api | Application component | data |
| ScanStatusResponse | com.assistant.frontend.api | Data transfer object | data |
| ScanLogEntryDTO | com.assistant.frontend.api | Data transfer object | data |
| AnalysisResponse | com.assistant.frontend.api | Data transfer object | data |
| RequirementSummary | com.assistant.frontend.api | Application component | data |
| AffectedModule | com.assistant.frontend.api | Application component | data |
| EvolutionEntry | com.assistant.frontend.api | Application component | data |
| ComplexityAssessment | com.assistant.frontend.api | Application component | data |
| KBReference | com.assistant.frontend.api | Application component | data |
| AnalysisStatus | com.assistant.frontend.api | Application component | data |
| UserInfo | com.assistant.frontend.api | Application component | data |
| AuditLogEntry | com.assistant.frontend.api | Application component | data |
| RoleChangeRequest | com.assistant.frontend.api | Data transfer object | data |
| PermissionToggleRequest | com.assistant.frontend.api | Data transfer object | data |
| PermToggle | com.assistant.frontend.api | Application component | data |
| AnalysisBottleneckRadar | com.assistant.frontend.api | Application component | internal |
| AnalysisScanStatus | com.assistant.frontend.api | Application component | internal |
| AnalysisStateManager | com.assistant.frontend.api | Application component | internal |
| AnalysisVelocityChart | com.assistant.frontend.api | Application component | internal |
| AnalysisPage | com.assistant.frontend.api | Application component | public |
| for | com.assistant.frontend.api | Application component | public |
| BasePage | com.assistant.frontend.api | Application component | abstract |
| DashboardMultiScanProgress | com.assistant.frontend.api | Application component | internal |
| DashboardNeuralConsole | com.assistant.frontend.api | Application component | internal |
| DashboardScanControl | com.assistant.frontend.api | Application component | internal |
| DashboardScanSettings | com.assistant.frontend.api | Application component | internal |
| DashboardSvgCharts | com.assistant.frontend.api | Application component | internal |
| Node | com.assistant.frontend.api | Application component | data |
| Pt | com.assistant.frontend.api | Application component | data |
| ScanLogDialog | com.assistant.frontend.api | Application component | internal |
| LogResponse | com.assistant.frontend.api | Data transfer object | data |
| ScanLogRenderer | com.assistant.frontend.api | Application component | internal |
| DashboardPage | com.assistant.frontend.api | Application component | public |
| CytoscapeLayoutHelper | com.assistant.frontend.api | Utility functions | internal |
| CytoscapeRenderer | com.assistant.frontend.api | Application component | internal |
| after | com.assistant.frontend.api | Application component | public |
| CytoscapeStyles | com.assistant.frontend.api | Application component | internal |
| with | com.assistant.frontend.api | Application component | public |
| name | com.assistant.frontend.api | Application component | public |
| name | com.assistant.frontend.api | Application component | public |
| name | com.assistant.frontend.api | Application component | public |
| name | com.assistant.frontend.api | Application component | public |
| GraphDetailPanel | com.assistant.frontend.api | Application component | internal |
| GraphFilterEngine | com.assistant.frontend.api | Application component | internal |
| GraphFilterPanel | com.assistant.frontend.api | Application component | internal |
| GraphNavControls | com.assistant.frontend.api | Application component | internal |
| GraphScanStatus | com.assistant.frontend.api | Application component | internal |
| PollResult | com.assistant.frontend.api | Application component | data |
| GraphDiff | com.assistant.frontend.api | Application component | data |
| GraphSearchCombobox | com.assistant.frontend.api | Application component | internal |
| SearchResult | com.assistant.frontend.api | Application component | data |
| GraphState | com.assistant.frontend.api | Application component | internal |
| GraphStateManager | com.assistant.frontend.api | Application component | internal |
| IntegrationsCardBuilder | com.assistant.frontend.api | Application component | internal |
| IntegrationsConfigModal | projects.kotlin.JIRAAssitantNewSollution.frontend.webpack.config.d | Application component | internal |
| FeatureToggleResp | com.assistant.frontend.api | Application component | data |
| FeatureToggleBody | com.assistant.frontend.api | Application component | data |
| IntegrationsStartStop | com.assistant.frontend.api | Application component | public |
| IntegrationsTestLink | com.assistant.frontend.api | Application component | internal |
| LocalKBCard | com.assistant.frontend.api | Application component | public |
| LocalServerStartStop | com.assistant.frontend.api | Application component | public |
| McpAtlassianPreset | com.assistant.frontend.api | Application component | public |
| McpConfigModal | projects.kotlin.JIRAAssitantNewSollution.frontend.webpack.config.d | Application component | public |
| with | com.assistant.frontend.api | Application component | public |
| McpSchemaModal | com.assistant.frontend.api | Application component | public |
| McpServerCards | com.assistant.frontend.api | Application component | public |
| McpStartStopControl | com.assistant.frontend.api | Application component | public |
| McpStatusPoller | com.assistant.frontend.api | Application component | public |
| McpToolApprovalDialog | com.assistant.frontend.api | Application component | public |
| McpToolsSection | com.assistant.frontend.api | Application component | public |
| ProviderConfigFields | projects.kotlin.JIRAAssitantNewSollution.frontend.webpack.config.d | Application component | internal |
| IntegrationsPage | com.assistant.frontend.api | Application component | public |
| KnowledgeGraphPage | com.assistant.frontend.api | Application component | public |
| LoginPage | com.assistant.frontend.api | Application component | public |
| Renderable | com.assistant.frontend.api | Application component | public |
| Cleanable | com.assistant.frontend.api | Application component | public |
| Pollable | com.assistant.frontend.api | Application component | public |
| ProjectSelectPage | com.assistant.frontend.api | Application component | public |
| ProjectSelectStates | com.assistant.frontend.api | Application component | public |
| SettingsAgentPipelineToggle | com.assistant.frontend.api | Application component | public |
| SettingsCurationToggle | com.assistant.frontend.api | Application component | public |
| CurationToggleResp | com.assistant.frontend.api | Application component | data |
| CurationToggleBody | com.assistant.frontend.api | Application component | data |
| SettingsSaveHandler | com.assistant.frontend.api | HTTP request handling | internal |
| FormValues | com.assistant.frontend.api | Application component | data |
| SettingsPage | com.assistant.frontend.api | Application component | public |
| CascadeIntegration | com.assistant.frontend.api | Application component | internal |
| CascadeLogEntryRenderer | com.assistant.frontend.api | Application component | internal |
| CascadeLogPanel | com.assistant.frontend.api | Application component | internal |
| CollectionJobPanel | com.assistant.frontend.api | Application component | internal |
| CollectionJobPoller | com.assistant.frontend.api | Application component | internal |
| ComplexityTabRenderer | com.assistant.frontend.api | Application component | internal |
| ConfidenceBadge | com.assistant.frontend.api | Application component | internal |
| ConflictBannerManager | com.assistant.frontend.api | Application component | internal |
| ContextTabEnrichment | com.assistant.frontend.api | Application component | internal |
| ContextTabRenderer | com.assistant.frontend.api | Application component | internal |
| DependencySectionRenderer | com.assistant.frontend.api | Application component | internal |
| DiagramRenderer | com.assistant.frontend.api | Application component | internal |
| DocGenApiHelper | com.assistant.frontend.api | Utility functions | internal |
| DocGenBadgeRenderer | com.assistant.frontend.api | Application component | internal |
| DocGenButtonHelper | com.assistant.frontend.api | Utility functions | internal |
| DocumentExporter | com.assistant.frontend.api | Application component | internal |
| DocumentGenerationFlow | com.assistant.frontend.api | Application component | internal |
| GenerateResponse | com.assistant.frontend.api | Data transfer object | data |
| ChainResponse | com.assistant.frontend.api | Data transfer object | data |
| DocumentGenerationSection | com.assistant.frontend.api | Application component | internal |
| DocumentPreviewDiagramHelper | com.assistant.frontend.api | Utility functions | internal |
| DocumentPreviewPanel | com.assistant.frontend.api | Application component | internal |
| DocumentPreviewToc | com.assistant.frontend.api | Application component | internal |
| DrawioDownloadHelper | com.assistant.frontend.api | Utility functions | internal |
| DrawioLayoutEngine | com.assistant.frontend.api | Application component | internal |
| DrawioShapeMapper | com.assistant.frontend.api | Data mapping | internal |
| DrawioTemplateEngine | com.assistant.frontend.api | Application component | internal |
| DrawioTemplateRegistry | com.assistant.frontend.api | Application component | internal |
| DrawioDiagramRenderer | com.assistant.frontend.api | Application component | internal |
| EvolutionTabRenderer | com.assistant.frontend.api | Application component | internal |
| InlineProgressRenderer | com.assistant.frontend.api | Application component | internal |
| MarkdownRenderer | com.assistant.frontend.api | Application component | internal |
| McpReadinessChecker | com.assistant.frontend.api | Application component | internal |
| MermaidDiagramRenderer | com.assistant.frontend.api | Application component | internal |
| ReadinessDialog | com.assistant.frontend.api | Application component | internal |
| ReviewPanel | com.assistant.frontend.api | Application component | internal |
| RejectBody | com.assistant.frontend.api | Application component | data |
| SlidePreviewPanel | com.assistant.frontend.api | Application component | internal |
| TechnicalDetailsRenderer | com.assistant.frontend.api | Application component | internal |
| TicketAnalysisFlow | com.assistant.frontend.api | Application component | internal |
| TicketAutoLoader | com.assistant.frontend.api | Application component | internal |
| TicketCombobox | com.assistant.frontend.api | Application component | internal |
| TicketProgressBar | com.assistant.frontend.api | Application component | internal |
| TicketResultTabs | com.assistant.frontend.api | Application component | internal |
| TicketStateManager | com.assistant.frontend.api | Application component | internal |
| VersionHistoryPanel | com.assistant.frontend.api | Application component | internal |
| DiffResponse | com.assistant.frontend.api | Data transfer object | data |
| TicketIntelligencePage | com.assistant.frontend.api | Application component | public |
| UserManagementPage | com.assistant.frontend.api | Application component | public |
| UserAuditLog | com.assistant.frontend.api | Application component | internal |
| UserPermissionPanel | com.assistant.frontend.api | Application component | internal |
| UserRoleChanger | com.assistant.frontend.api | Application component | internal |
| Router | com.assistant.frontend.api | Application component | public |
| Route | com.assistant.frontend.api | Application component | data |
| HtmlUtils | com.assistant.frontend.api | Utility functions | public |
| NavigationContext | com.assistant.frontend.api | Application component | public |
| PendingContext | com.assistant.frontend.api | Application component | data |
| ScanStatusService | com.assistant.frontend.services | Business logic | public |
| ToastService | com.assistant.frontend.services | Business logic | public |
| ValidationService | com.assistant.frontend.services | Business logic | public |
| mxGraphModel | com.assistant.frontend.models | Domain model | public |
| root | com.assistant.frontend.api | Application component | public |
| mxCell | com.assistant.frontend.api | Application component | public |
| mxGraphModel | com.assistant.frontend.models | Domain model | public |
| root | com.assistant.frontend.api | Application component | public |
| mxCell | com.assistant.frontend.api | Application component | public |
| mxGraphModel | com.assistant.frontend.models | Domain model | public |
| root | com.assistant.frontend.api | Application component | public |
| mxCell | com.assistant.frontend.api | Application component | public |
| mxGraphModel | com.assistant.frontend.models | Domain model | public |
| root | com.assistant.frontend.api | Application component | public |
| mxCell | com.assistant.frontend.api | Application component | public |
| mxGraphModel | com.assistant.frontend.models | Domain model | public |
| root | com.assistant.frontend.api | Application component | public |
| mxCell | com.assistant.frontend.api | Application component | public |
| ChatGraphActionIntegrationTest | com.assistant.frontend.api | Test class | public |
| ChatNavigateContextTest | com.assistant.frontend.api | Test class | public |
| ChatPersistenceTest | com.assistant.frontend.api | Test class | public |
| CrossPageNavigationIntegrationTest | com.assistant.frontend.api | Test class | public |
| GraphActionHandlerTest | com.assistant.frontend.api | Test class | public |
| AnalysisPreservationTest | com.assistant.frontend.api | Test class | public |
| AnalysisSlowRestoreBugTest | com.assistant.frontend.api | Test class | public |
| AnalysisPageNavigation | com.assistant.frontend.api | Application component | data |
| BfsTraversalTest | com.assistant.frontend.api | Test class | public |
| TestGraph | com.assistant.frontend.api | Application component | private |
| GraphDetailPanelTest | com.assistant.frontend.api | Test class | public |
| GraphFilterEngineTest | com.assistant.frontend.api | Test class | public |
| GraphPreservationTest | com.assistant.frontend.api | Test class | public |
| GraphSlowRestoreBugTest | com.assistant.frontend.api | Test class | public |
| GraphPageNavigation | com.assistant.frontend.api | Application component | data |
| HoverHighlightEdgesTest | com.assistant.frontend.api | Test class | public |
| SearchFilterCorrectnessTest | com.assistant.frontend.api | Test class | public |
| AIProviderModalPreservationTest | com.assistant.frontend.api | Test class | public |
| ConfigModalInteraction | projects.kotlin.JIRAAssitantNewSollution.frontend.webpack.config.d | Application component | data |
| ButtonRecoveryPropertyTest | com.assistant.frontend.api | Test class | public |
| DiagramRendererFormatRoutingPropertyTest | com.assistant.frontend.api | Test class | public |
| DiagramRendererOrderPreservationPropertyTest | com.assistant.frontend.api | Test class | public |
| DrawioLayoutEnginePropertyTest | com.assistant.frontend.api | Test class | public |
| DrawioShapeMapperPropertyTest | com.assistant.frontend.api | Test class | public |
| DrawioTemplateEnginePropertyTest | com.assistant.frontend.api | Test class | public |
| DrawioTemplateRegistryPropertyTest | com.assistant.frontend.api | Test class | public |
| ElapsedTimeFormattingPropertyTest | com.assistant.frontend.api | Test class | public |
| TimeoutWarningThresholdPropertyTest | com.assistant.frontend.api | Test class | public |
| NoAutoSelectBugConditionTest | com.assistant.frontend.api | Test class | public |
| BlurInput | com.assistant.frontend.api | Application component | data |
| PreservationPropertyTest | com.assistant.frontend.api | Test class | public |
| SlowRestoreBugConditionTest | com.assistant.frontend.api | Test class | public |
| PageNavigation | com.assistant.frontend.api | Application component | data |
| NavigationContextTest | com.assistant.frontend.api | Test class | public |
| ScanStatusServiceTest | com.assistant.frontend.services | Test class | public |

## Public API Surface

- `saveToken(jwt: String): Unit`
- `getToken(): String`
- `clearToken(): Unit`
- `saveProjectKey(key: String): Unit`
- `getProjectKey(): String`
- `clearProjectKey(): Unit`
- `saveUserInfo(role: UserRole, email: String): Unit`
- `getUserRole(): UserRole`
- `getUserEmail(): String`
- `hasPermission(permission: Permission): Boolean`
- `onLoginSuccess(result: AuthResult.Success): Unit`
- `signOut(): Unit`
- `handleUnauthorized(response: HttpResponse): Boolean`
- `saveToken(jwt: String): Unit`
- `getToken(): String`
- `clearAll(): Unit`
- `saveProjectKey(key: String): Unit`
- `getProjectKey(): String`
- `clearProjectKey(): Unit`
- `saveUserInfo(role: UserRole, email: String): Unit`
- `getUserRole(): UserRole`
- `getUserEmail(): String`
- `main(): Unit`
- `checkAuthAndNavigate(): Unit`
- `showToast(message: String, durationMs: Int = 5000): Unit`
- `init(): Unit`
- `toggle(): Unit`
- `loadHistory(): Unit`
- `sendMessage(): Unit`
- `updateGraphContext(): Unit`
- `showError(msg: String): Unit`
- `show(containerId: String, message: String = "Processing..."): Unit`
- `remove(containerId: String): Unit`
- `open(sidebar: HTMLElement): Unit`
- `close(): Unit`
- `updateBadge(): Unit`
- `showModal(title: String, bodyHtml: String, onSave: (): Unit`
- `closeModal(): Unit`
- `validateRequired(selector: String): Boolean`
- `showSkillForm(existing: SkillEntry?, onResult: (SkillEntry): Unit`
- `showWorkflowForm(existing: WorkflowEntry?, nextStep: Int, onResult: (WorkflowEntry): Unit`
- `showInstructionForm(existing: InstructionEntry?, onResult: (InstructionEntry): Unit`
- `showRuleForm(existing: RuleEntry?, onResult: (RuleEntry): Unit`
- `clearAll(): Unit`
- `renderSkillsTable(id: String): Unit`
- `renderWorkflowTable(id: String): Unit`
- `renderInstructionsTable(id: String): Unit`
- `renderRulesTable(id: String): Unit`
- `buildSkillsTable(): String`
- `buildWorkflowTable(): String`
- `buildInstructionsTable(): String`
- `buildRulesTable(): String`
- `addSkillRow(tableId: String, entry: SkillEntry? = null): Unit`
- `addWorkflowRow(tableId: String, entry: WorkflowEntry? = null): Unit`
- `addInstructionRow(tableId: String, entry: InstructionEntry? = null): Unit`
- `addRuleRow(tableId: String, entry: RuleEntry? = null): Unit`
- `bindDeleteButton(row: HTMLTableRowElement): Unit`
- `collectSkills(tableId: String): List`
- `collectWorkflow(tableId: String): List`
- `collectInstructions(tableId: String): List`
- `collectRules(tableId: String): List`
- `add(message: String): Unit`
- `clear(): Unit`
- `populateFromMessages(messages: List<String>): Unit`
- `current(): GraphChatContext`
- `refresh(): Unit`
- `renderMessage(role: String, content: String): HTMLElement`
- `parseMarkdown(text: String): String`
- `current(): TicketChatContext`
- `init(): Unit`
- `load(): Unit`
- `init(textarea: HTMLTextAreaElement): Unit`
- `update(percent: Int): Unit`
- `load(): Unit`
- `createNew(): Unit`
- `render(): Unit`
- `openFilePicker(): Unit`
- `uploadFile(file: File): Unit`
- `getPendingFiles(): List`
- `clearPending(): Unit`
- `canHandle(action: ChatAction): Boolean`
- `execute(action: ChatAction): String`
- `executePendingAction(): Unit`
- `createRunningIndicator(toolName: String, serverId: String? = null): HTMLElement`
- `createResultBlock(toolName: String, result: String, isError: Boolean): HTMLElement`
- `init(input: HTMLTextAreaElement): Unit`
- `load(): Unit`
- `init(input: HTMLTextAreaElement): Unit`
- `init(input: HTMLTextAreaElement): Unit`
- `loadTools(): Unit`
- `toggle(): Unit`
- `filterAndShow(query: String): Unit`
- `hide(): Unit`
- `init(inputEl: HTMLTextAreaElement, btnEl: HTMLButtonElement): Unit`
- `isSupported(): Boolean`
- `render(navActions: HTMLElement): Unit`
- `cleanup(): Unit`
- `startPolling(): Unit`
- `updateJobs(jobs: List<GenerationJobDto>): Unit`
- `render(container: Element): Unit`
- `updateBreadcrumb(route: String): Unit`
- `renderProjectSelector(container: HTMLElement): Unit`
- `renderUserWidget(container: HTMLElement): Unit`
- `refreshProjectSelector(): Unit`
- `closeAll(): Unit`
- `render(container: Element, contentRenderer: (Element): Unit`
- `render(container: Element): Unit`
- `updateActiveState(currentRoute: String): Unit`
- `updateStatus(label: String, detail: String, progressPercent: Int): Unit`
- `fromId(id: String): TicketTab`
- `from(confidence: ExtractionConfidence): Unit`
- `render(bottlenecks: List<BottleneckAlert>): Unit`
- `renderEmpty(): Unit`
- `cancelJobs(): Unit`
- `loadScanStatus(): Unit`
- `save(data: ProjectAnalysisResponse): Unit`
- `restore(): ProjectAnalysisResponse`
- `clear(): Unit`
- `render(velocityTrend: List<SprintVelocity>): Unit`
- `renderEmpty(): Unit`
- `render(container: Element): Unit`
- `cleanup(): Unit`
- `render(container: Element): Unit`
- `onBind(): Unit`
- `onLoad(): Unit`
- `formatProgressLabel(scan: ScanStatusResponse): String`
- `renderActiveScans(scans: List<ScanStatusResponse>): Unit`
- `startActivePolling(): Unit`
- `stopActivePolling(): Unit`
- `update(data: DashboardAnalysis): Unit`
- `bindControls(): Unit`
- `loadScanStatus(): Unit`
- `stopPolling(): Unit`
- `restore(): Unit`
- `bindPersistence(getConcurrency: (): Unit`
- `renderNetworkPreview(): Unit`
- `renderDriftChart(velocityTrend: List<VelocityPoint>): Unit`
- `bind(): Unit`
- `open(): Unit`
- `close(): Unit`
- `refreshIfOpen(): Unit`
- `render(entries: List<ScanLogEntryDTO>): Unit`
- `reset(): Unit`
- `formatTimestamp(isoTimestamp: String): String`
- `render(container: Element): Unit`
- `cleanup(): Unit`
- `cytoscape(options: dynamic): dynamic`
- `runIncrementalLayout(c: dynamic, newNodeIds: Set<String>): Unit`
- `layoutVisible(c: dynamic, count: Int): Unit`
- `layoutConcentric(c: dynamic, focusId: String): Unit`
- `cyRef(): dynamic`
- `isInitialized(): Boolean`
- `renderGraph(): Unit`
- `renderEmptyState(): Unit`
- `destroy(): Unit`
- `applyFilter(filteredIds: Set<String>?): Unit`
- `centerOnNode(nodeId: String): Unit`
- `resetCamera(): Unit`
- `buildOptions(): dynamic`
- `containerBackground(): String`
- `cyanEdge(): String`
- `purpleEdge(): String`
- `highlightClass(): String`
- `focusedClass(): String`
- `dimClass(): String`
- `fadeInClass(): String`
- `show(node: GraphNode): Unit`
- `close(): Unit`
- `isAnyFilterActive(filters: GraphFilters): Boolean`
- `bfsFromEdges(startNodeId: String, depth: Int, edges: List<GraphEdge>): Set`
- `init(): Unit`
- `populateNodeTypes(types: List<NodeTypeInfo>): Unit`
- `onFilterChange(): Unit`
- `activateFocusMode(nodeId: String): Unit`
- `deactivateFocusMode(): Unit`
- `resetAll(): Unit`
- `updateNodeCount(visible: Int, total: Int): Unit`
- `getFilters(): GraphFilters`
- `populateClusters(clusters: List<GraphCluster>): Unit`
- `allTypeNames(): Set`
- `isSimplifyEnabled(): Boolean`
- `bind(cy: dynamic): Unit`
- `cancelJobs(): Unit`
- `loadScanStatus(): Unit`
- `init(): Unit`
- `reset(): Unit`
- `save(data: GraphLayoutResponse): Unit`
- `restore(): GraphLayoutResponse`
- `clear(): Unit`
- `buildCardHtml(provider: ProviderInfo, index: Int, canConfig: Boolean): String`
- `buildTooltipText(provider: ProviderInfo): String`
- `statusBadgeClass(status: String): String`
- `bindCardEvents(card: HTMLElement, provider: ProviderInfo, index: Int, canConfig: Boolean): Unit`
- `openConfigModal(provider: ProviderInfo): Unit`
- `closeConfigModal(): Unit`
- `toggle(provider: ProviderInfo, card: HTMLElement): Unit`
- `testProviderLink(providerId: String): Unit`
- `render(container: HTMLElement): Unit`
- `createButton(server: McpServerInfo, card: HTMLElement): HTMLElement`
- `open(): Unit`
- `close(): Unit`
- `open(server: McpServerInfo? = null): Unit`
- `close(): Unit`
- `open(tool: McpToolInfoDto): Unit`
- `close(): Unit`
- `load(): Unit`
- `render(): Unit`
- `cleanup(): Unit`
- `createButton(serverId: String, serverName: String, state: String): HTMLElement`
- `updateButtonState(btn: HTMLElement, state: String): Unit`
- `start(servers: List<McpServerInfo>): Unit`
- `stop(): Unit`
- `stateColor(state: String): String`
- `formatUptime(seconds: Long): String`
- `close(): Unit`
- `createSection(serverId: String, toolCount: Int): HTMLElement`
- `build(provider: ProviderInfo, disabled: String): String`
- `render(container: Element): Unit`
- `render(container: Element): Unit`
- `cleanup(): Unit`
- `render(container: Element): Unit`
- `render(container: Element): Unit`
- `cleanup(): Unit`
- `startPolling(): Unit`
- `stopPolling(): Unit`
- `render(container: Element): Unit`
- `showCredentialError(): Unit`
- `showJiraNotConfigured(): Unit`
- `showEmptyWithRetry(onRetry: (): Unit`
- `init(): Unit`
- `init(): Unit`
- `showStatus(message: String, isError: Boolean): Unit`
- `hideStatus(): Unit`
- `render(container: Element): Unit`
- `triggerCascade(ticketId: String): Unit`
- `reset(): Unit`
- `cleanup(): Unit`
- `createLogLine(entry: CascadeLogEntry): HTMLElement`
- `statusDisplay(status: CascadeLogStatus): Pair`
- `startPolling(ticketId: String): Unit`
- `stopPolling(): Unit`
- `showPanel(): Unit`
- `hidePanel(): Unit`
- `renderSnapshot(result: CascadeResult): Unit`
- `render(jobs: List<CollectionJobResponse>): Unit`
- `hide(): Unit`
- `startMonitoring(ticketId: String): Unit`
- `cleanup(): Unit`
- `isTicketProcessing(ticketId: String): Boolean`
- `getParentTicketId(ticketId: String): String`
- `render(container: HTMLElement, data: AnalysisResponse): Unit`
- `render(parent: HTMLElement, metadata: AnalysisMetadata): Unit`
- `update(ticketId: String, jobs: List<CollectionJobResponse>): Unit`
- `showPermissionWarning(count: Int): Unit`
- `hideAll(): Unit`
- `renderDependenciesOverview(parent: HTMLElement, deps: DependencyInfo): Unit`
- `renderAnalysisInfo(parent: HTMLElement, metadata: AnalysisMetadata): Unit`
- `render(container: HTMLElement, data: AnalysisResponse): Unit`
- `render(parent: HTMLElement, deps: DependencyInfo): Unit`
- `render(container: HTMLElement, diagrams: List<DiagramData>): Unit`
- `generateEndpoint(ticketId: String, docType: String): String`
- `showErrorToast(sectionId: String, message: String): Unit`
- `dismissErrorToast(): Unit`
- `renderBadges(docs: List<GeneratedDocumentMeta>, onClick: (String): Unit`
- `renderInlineProgress(activeJobs: List<GenerationJobDto>): Unit`
- `shouldEnableButton(jobStatus: String): Boolean`
- `buttonIdForDocType(docType: String): String`
- `progressAreaId(docType: String): String`
- `enableGenerateButton(docType: String): Unit`
- `enableCancelButton(docType: String): Unit`
- `disableButton(btn: HTMLElement): Unit`
- `enableButton(btn: HTMLElement): Unit`
- `bindExportHandlers(doc: GeneratedDocumentFull): Unit`
- `exportMarkdown(doc: GeneratedDocumentFull): Unit`
- `exportPdf(): Unit`
- `cleanup(): Unit`
- `startGeneration(ticketId: String, documentType: String): Unit`
- `startGenerateAll(ticketId: String): Unit`
- `cancelJob(jobId: String, ticketId: String, documentType: String): Unit`
- `fetchAndPreview(ticketId: String, docType: String): Unit`
- `fetchDraftAndPreview(ticketId: String, docType: String): Unit`
- `cancelPolling(): Unit`
- `render(ticketId: String, isAnalyzed: Boolean, isReader: Boolean): Unit`
- `hide(): Unit`
- `refreshBadges(ticketId: String): Unit`
- `isJsonDiagramMetadata(text: String): Boolean`
- `tryParseJsonMetadata(text: String): DrawioMetadata`
- `open(document: GeneratedDocumentFull): Unit`
- `close(): Unit`
- `render(contentArea: Element): Unit`
- `showFallback(container: HTMLElement, xml: String, title: String): Unit`
- `calculate(template: String, nodeCount: Int): List`
- `styleFor(type: String): String`
- `merge(metadata: DrawioMetadata, onReady: (String): Unit`
- `load(name: String, onReady: (String): Unit`
- `renderInCard(card: HTMLElement, diagram: DiagramData): Unit`
- `render(container: HTMLElement, data: AnalysisResponse): Unit`
- `installCancelDelegate(areaId: String, onCancel: (): Unit`
- `removeCancelDelegate(areaId: String): Unit`
- `renderProgress(areaId: String, job: GenerationJobDto): Unit`
- `renderError(areaId: String, job: GenerationJobDto, onRetry: (): Unit`
- `renderSuccess(areaId: String, onComplete: (): Unit`
- `startElapsedTimer(areaId: String, startedAt: String): Int`
- `stopElapsedTimer(intervalId: Int): Unit`
- `clearProgress(areaId: String): Unit`
- `formatElapsed(totalSeconds: Int): String`
- `shouldShowTimeoutWarning(elapsedSeconds: Int): Boolean`
- `renderInCard(card: HTMLElement, diagram: DiagramData): Unit`
- `isCriticalForDocType(role: String, docType: String): Boolean`
- `areAllCriticalDown(servers: List<McpServerHealth>, docType: String): Boolean`
- `render(doc: GeneratedDocumentFull, docId: String?, isReader: Boolean): Unit`
- `hide(): Unit`
- `open(markdownContent: String): Unit`
- `close(): Unit`
- `render(parent: HTMLElement, details: TechnicalDetails): Unit`
- `cancelJobs(): Unit`
- `startAnalysis(ticketId: String, forceReanalyze: Boolean): Unit`
- `checkAndResumeAnalysis(): Unit`
- `cleanup(): Unit`
- `onTicketSelected(ticketId: String, state: TicketAnalysisState): Unit`
- `cancelLoad(): Unit`
- `reset(): Unit`
- `filterTickets(query: String): Unit`
- `selectTicket(ticket: TicketAnalysisStatus): Unit`
- `selectTicketSilently(ticket: TicketAnalysisStatus): Unit`
- `updateStatusBadge(state: TicketAnalysisState): Unit`
- `updateActionButton(state: TicketAnalysisState): Unit`
- `setInputText(text: String): Unit`
- `isValidTicketId(text: String): Boolean`
- `getTypedTicketId(): String`
- `acceptCrossProjectTicket(ticketId: String): Unit`
- `updateFromStatus(status: AnalysisStatus): Unit`
- `show(): Unit`
- `hide(): Unit`
- `complete(): Unit`
- `switchTab(tabName: String): Unit`
- `updateTabStyles(): Unit`
- `renderTabContent(data: AnalysisResponse): Unit`
- `save(state: TicketPageState): Unit`
- `restore(): TicketPageState`
- `clear(): Unit`
- `render(ticketId: String, docType: String): Unit`
- `hide(): Unit`
- `closeDiffView(): Unit`
- `render(container: Element): Unit`
- `render(container: Element): Unit`
- `clear(): Unit`
- `addEntry(tag: String, description: String): Unit`
- `selectUser(userId: String): Unit`
- `changeRole(userId: String, newRole: String): Unit`
- `init(container: Element): Unit`
- `register(path: String, render: (container): Unit`
- `registerShell(renderer: (container, contentRenderer: (Element): Unit`
- `registerStandalone(path: String): Unit`
- `handleRoute(): Unit`
- `navigateTo(path: String): Unit`
- `getCurrentRoute(): String`
- `escapeHtml(text: String): String`
- `formatNumber(n: Int): String`
- `store(screen: String, params: Map<String, String>: Any): Unit`
- `consume(screen: String): Map`
- `peek(): PendingContext`
- `clear(): Unit`
- `updateBadge(badge: HTMLElement, status: ScanStatus, processed: Int, total: Int): Unit`
- `show(message: String, type: String = "success"): Unit`
- `isValidUrl(url: String): Boolean`
- `setup(): Unit`
- `focusNodeOnGraphPageReturnsConfirmation(): Unit`
- `focusNodeNotFoundReturnsError(): Unit`
- `focusNodeFromOtherPageNavigatesFirst(): Unit`
- `filterByTypeNavigatesWhenNotOnGraph(): Unit`
- `resetFiltersNotOnGraphReturnsWarning(): Unit`
- `navigateToGraphReturnsConfirmation(): Unit`
- `canHandleAcceptsGraphTypes(): Unit`
- `canHandleRejectsNonGraphTypes(): Unit`
- `filterByClusterNotFoundReturnsError(): Unit`
- `openUrlReturnsConfirmation(): Unit`
- `setup(): Unit`
- `navigateWithTicketKeyStoresContext(): Unit`
- `navigateWithoutExtraParamsDoesNotStoreContext(): Unit`
- `navigateScreenParamExcludedFromContext(): Unit`
- `navigateWithMultipleContextParams(): Unit`
- `navigateWithBlankScreenDoesNotStoreContext(): Unit`
- `setup(): Unit`
- `currentScreenDefaultsToDashboardWhenHashEmpty(): Unit`
- `currentScreenReflectsKnowledgeGraphHash(): Unit`
- `currentScreenReflectsAnalysisHash(): Unit`
- `currentScreenUpdatesAfterNavigation(): Unit`
- `currentScreenHandlesAllRoutes(): Unit`
- `graphContextNullOnDashboard(): Unit`
- `graphContextNullOnAnalysis(): Unit`
- `graphContextNullOnIntegrations(): Unit`
- `graphContextAvailableOnKnowledgeGraph(): Unit`
- `shellRerenderPreservesExistingWorkspacePattern(): Unit`
- `chatContextBuiltFreshOnEachMessage(): Unit`
- `chatContextPreservesProjectKeyAcrossNavigation(): Unit`
- `setup(): Unit`
- `navigateWithTicketKeyStoresAndConsumes(): Unit`
- `navigateToAnalysisWithTicketKey(): Unit`
- `navigateWithoutExtraParamsNoContext(): Unit`
- `contextConsumedOnlyOnce(): Unit`
- `consumeWrongScreenReturnsNull(): Unit`
- `multipleContextParamsPreserved(): Unit`
- `sequentialNavigationsOverwriteContext(): Unit`
- `graphActionFromOtherPageNavigatesToGraph(): Unit`
- `blankScreenDoesNotStoreContext(): Unit`
- `setup(): Unit`
- `canHandleReturnsTrueForAllSevenActionTypes(): Unit`
- `canHandleReturnsFalseForUnknownTypes(): Unit`
- `focusNodeMissingParamReturnsMissingMessage(): Unit`
- `focusNodeNotFoundWhenOnGraphPage(): Unit`
- `focusNodeNavigatesWhenNotOnGraphPage(): Unit`
- `filterByTypeMissingParamWhenOnGraphPage(): Unit`
- `filterByTypeNavigatesWhenNotOnGraphPage(): Unit`
- `filterByClusterMissingParamWhenOnGraphPage(): Unit`
- `filterByClusterNotFoundWhenOnGraphPage(): Unit`
- `filterByClusterNavigatesWhenNotOnGraphPage(): Unit`
- `resetFiltersNotOnGraphPageReturnsMessage(): Unit`
- `searchNodesMissingQueryWhenOnGraphPage(): Unit`
- `searchNodesNavigatesWhenNotOnGraphPage(): Unit`
- `navigateToGraphReturnsNavigatedMessage(): Unit`
- `openUrlMissingParamReturnsMissingMessage(): Unit`
- `openUrlWithUrlReturnsOpenedMessage(): Unit`
- `unknownActionTypeReturnsUnknownMessage(): Unit`
- `setup(): Unit`
- `saveRestoreRoundtripPreservesAllFields(): Unit`
- `restoreReturnsNullWhenSessionStorageEmpty(): Unit`
- `restoreReturnsNullOnInvalidJson(): Unit`
- `firstLoadHasNoSavedState(): Unit`
- `setup(): Unit`
- `metricsShallDisplaySavedValuesImmediately(): Unit`
- `allFourMetricsShallBePopulatedFromSessionStorage(): Unit`
- `loadAnalysisDataShallSaveToSessionStorage(): Unit`
- `addNode(id: String): Unit`
- `addEdge(a: String, b: String): Unit`
- `bfsTraversalCorrectness(): Unit`
- `bfsStartNodeAlwaysIncluded(): Unit`
- `bfsDepthZeroReturnsOnlyStart(): Unit`
- `bfsIsolatedNodeReturnsOnlyStart(): Unit`
- `bfsLinearChainRespectsDepth(): Unit`
- `bfsResultCapAt500WhenDepthGreaterThan3(): Unit`
- `setupDom(): Unit`
- `descriptionRenderedWhenPresent(): Unit`
- `descriptionFallbackWhenNull(): Unit`
- `descriptionFallbackWhenBlank(): Unit`
- `renderLinkedTicketsShowsSection(): Unit`
- `linkedTicketItemHasCorrectClasses(): Unit`
- `linkedSectionHiddenByDefault(): Unit`
- `renderSubTasksShowsSection(): Unit`
- `subtaskStatusIcons(): Unit`
- `subtasksSectionHiddenByDefault(): Unit`
- `ticketKeyLookupFindsNodeInGraphState(): Unit`
- `ticketKeyLookupReturnsNullWhenMissing(): Unit`
- `combinedAndFilterNodeVisibility(): Unit`
- `allFiltersDefaultReturnsAllNodes(): Unit`
- `visibleSetIsSubsetOfAllNodeIds(): Unit`
- `edgeVisibilityDerivedFromNodeVisibility(): Unit`
- `isAnyFilterActiveCorrectness(): Unit`
- `setup(): Unit`
- `saveRestoreRoundtripPreservesAllFields(): Unit`
- `restoreReturnsNullWhenSessionStorageEmpty(): Unit`
- `restoreReturnsNullOnInvalidJson(): Unit`
- `graphStateResetClearsAllFields(): Unit`
- `navigationContextTakesPriorityOverRestore(): Unit`
- `firstLoadHasNoSavedState(): Unit`
- `setup(): Unit`
- `graphStateShallContainSavedNodesImmediately(): Unit`
- `allGraphStateFieldsShallBePopulated(): Unit`
- `hoverHighlightsExactlyConnectedEdges(): Unit`
- `everyHighlightedEdgeTouchesHoveredNode(): Unit`
- `noMissedEdgesOutsideHighlightedSet(): Unit`
- `isolatedNodeHasNoConnectedEdges(): Unit`
- `searchFilterReturnsExactMatchingNodes(): Unit`
- `blankQueryReturnsAllNodeIds(): Unit`
- `filterIsCaseInsensitive(): Unit`
- `filteredSetIsSubsetOfAllIds(): Unit`
- `aiProviderModalShallHaveTestConnectionButton(): Unit`
- `aiProviderModalSaveButtonShallBePresentAndDisabled(): Unit`
- `aiProviderModalShallNotHaveSaveAndTestButton(): Unit`
- `openConfigModalRoutesNonJiraToStandardModal(): Unit`
- `aiProviderModalInputFieldsUseDarkBackgroundClass(): Unit`
- `shouldEnableButtonForAllTerminalStatusAndDocTypeCombinations(): Unit`
- `shouldNotEnableButtonForNonTerminalStatuses(): Unit`
- `allTerminalStatusesExhaustive(): Unit`
- `buttonIdMappingCoversAllDocTypes(): Unit`
- `progressAreaIdCoversAllDocTypes(): Unit`
- `formatRoutingCorrectness(): Unit`
- `mermaidFormatRoutesToMermaidRenderer(): Unit`
- `drawioFormatRoutesToDrawioRenderer(): Unit`
- `emptyOrBlankFormatRoutesToMermaidRenderer(): Unit`
- `renderingOrderPreservesInputOrder(): Unit`
- `mixedFormatsAreNotGrouped(): Unit`
- `singleDiagramPreservesOrder(): Unit`
- `emptyListProducesNoRendering(): Unit`
- `layoutPositionsAreUniqueForAllTemplatesAndCounts(): Unit`
- `emptyNodeListReturnsEmptyPositions(): Unit`
- `singleNodeAlwaysProducesOnePosition(): Unit`
- `unknownTemplatePositionsAreAlsoUnique(): Unit`
- `unknownTypeUsesDefaultRectangleStyle(): Unit`
- `unknownTypeNeverThrowsError(): Unit`
- `validTypesDoNotUseDefaultStyle(): Unit`
- `mergeOutputVertexCountMatchesNodeCount(): Unit`
- `mergeOutputEdgeCountMatchesConnectionCount(): Unit`
- `mergeOutputContainsBothVerticesAndEdges(): Unit`
- `unknownNameResolvesToComponentFallback(): Unit`
- `validNamesResolveToThemselves(): Unit`
- `emptyStringResolvesToFallback(): Unit`
- `caseSensitiveNamesResolveToFallback(): Unit`
- `formatElapsedProducesCorrectPattern(): Unit`
- `formatElapsedMinutesAreNonNegative(): Unit`
- `formatElapsedSecondsPartInRange(): Unit`
- `formatElapsedBoundaryValues(): Unit`
- `shouldShowTimeoutWarningIffElapsedExceeds240(): Unit`
- `timeoutWarningFalseAtAndBelow240(): Unit`
- `timeoutWarningTrueAbove240(): Unit`
- `timeoutWarningBoundaryValues(): Unit`
- `setup(): Unit`
- `clickOutsideShallAutoSelectMatchingTicket(): Unit`
- `clickOutsideShallSaveStateForMatchedTicket(): Unit`
- `clickOutsideWithNonMatchingTextShallRestore(): Unit`
- `setup(): Unit`
- `selectTicketUpdatesStateAndInput(): Unit`
- `filterTicketsReturnsOnlyMatchingTickets(): Unit`
- `clickInsideComboboxDoesNotHideDropdown(): Unit`
- `emptyTicketListAndNoStateShowsEmptyInput(): Unit`
- `clickOutsideWithEmptyInputOnlyHidesDropdown(): Unit`
- `setup(): Unit`
- `comboboxShallDisplaySavedTicketImmediately(): Unit`
- `comboboxInputNotEmptyWhenStateExists(): Unit`
- `setup(): Unit`
- `consumeReturnsNullWhenEmpty(): Unit`
- `storeAndConsumeMatchingScreen(): Unit`
- `consumeReturnsNullForMismatchedScreen(): Unit`
- `consumeClearsContextAfterRead(): Unit`
- `peekDoesNotClearContext(): Unit`
- `clearRemovesPendingContext(): Unit`
- `storeOverwritesPreviousContext(): Unit`
- `emptyParamsStoreAndConsumeCorrectly(): Unit`
- `setup(): Unit`
- `scanningBadgeShowsProgressFormat(): Unit`
- `scanningBadgeZeroTotal(): Unit`
- `scanningBadgeFullProgress(): Unit`
- `completedBadgeShowsTotalCount(): Unit`
- `pausedBadgeShowsProcessedOfTotal(): Unit`
- `cancelledBadgeShowsCancelled(): Unit`
- `idleBadgeIsHidden(): Unit`

## Dependencies

| Imports From | Classes Used |
|-------------|-------------|
| — | — |

## Detected Patterns

- **DI Style**: none
- **Error Handling**: Result type
- **Naming**: *Service
- **Logging**: unknown
- **Testing**: unknown

## Annotations

| Target | Author Agent | Type | Content | Timestamp |
|--------|-------------|------|---------|-----------|
